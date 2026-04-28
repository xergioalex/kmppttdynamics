#!/usr/bin/env python3
"""Detect and crop avatars from 4x3 grid images.

Each grid PNG in INPUT_DIR contains 12 avatars (4 cols x 3 rows) separated
by a thin near-white gap. This script finds the gap rows/columns, splits the
image into individual tiles, saves each as a square PNG into OUTPUT_DIR, and
writes a debug overlay image into DEBUG_DIR.
"""

from __future__ import annotations

from pathlib import Path
from typing import List, Optional, Tuple

import cv2
import numpy as np

REPO = Path(__file__).resolve().parent.parent
INPUT_DIR = REPO / "assets" / "avatars" / "grids"
OUTPUT_DIR = REPO / "assets" / "avatars" / "individual"
ALL_DIR = REPO / "assets" / "avatars" / "all"
DEBUG_DIR = REPO / "assets" / "avatars" / "debug"

ROWS = 3
COLS = 4

# A pixel is considered "gap" when it is bright AND nearly grayscale.
GAP_BRIGHTNESS_MIN = 225          # 0-255
GAP_CHANNEL_SPREAD_MAX = 12       # max(R,G,B) - min(R,G,B)
# A row/column counts as a separator when at least this fraction of pixels are gap.
SEPARATOR_PIXEL_FRAC = 0.92


def gap_mask(img: np.ndarray) -> np.ndarray:
    """Boolean mask, True for pixels that look like inter-tile gap."""
    bright = img.min(axis=2) >= GAP_BRIGHTNESS_MIN
    spread = img.max(axis=2).astype(np.int16) - img.min(axis=2).astype(np.int16)
    flat = spread <= GAP_CHANNEL_SPREAD_MAX
    return bright & flat


def find_separator_runs(line_is_gap: np.ndarray) -> List[Tuple[int, int]]:
    """Return (start, end_exclusive) ranges where consecutive lines are gap."""
    runs: List[Tuple[int, int]] = []
    n = len(line_is_gap)
    i = 0
    while i < n:
        if line_is_gap[i]:
            j = i
            while j < n and line_is_gap[j]:
                j += 1
            runs.append((i, j))
            i = j
        else:
            i += 1
    return runs


def split_axis(line_is_gap: np.ndarray, expected_tiles: int) -> List[Tuple[int, int]]:
    """Find tile (start, end) ranges along one axis using gap separator runs.

    Strategy: locate runs of gap lines, then keep the (expected_tiles + 1)
    longest runs as the outer margins + inner separators, sort by position,
    and read off tile spans between consecutive runs.
    """
    runs = find_separator_runs(line_is_gap)
    if len(runs) < expected_tiles + 1:
        return []
    # Sort runs by length, keep the longest expected_tiles + 1.
    runs_sorted = sorted(runs, key=lambda r: r[1] - r[0], reverse=True)
    keep = sorted(runs_sorted[: expected_tiles + 1], key=lambda r: r[0])
    spans: List[Tuple[int, int]] = []
    for a, b in zip(keep, keep[1:]):
        spans.append((a[1], b[0]))  # tile spans the area between two gap runs
    return spans


def detect_tile_boxes(img: np.ndarray) -> Optional[List[Tuple[int, int, int, int]]]:
    h, w = img.shape[:2]
    mask = gap_mask(img)
    row_gap_frac = mask.mean(axis=1)
    col_gap_frac = mask.mean(axis=0)

    row_is_gap = row_gap_frac >= SEPARATOR_PIXEL_FRAC
    col_is_gap = col_gap_frac >= SEPARATOR_PIXEL_FRAC

    row_spans = split_axis(row_is_gap, ROWS)
    col_spans = split_axis(col_is_gap, COLS)

    if len(row_spans) != ROWS or len(col_spans) != COLS:
        return None

    boxes: List[Tuple[int, int, int, int]] = []
    for y0, y1 in row_spans:
        for x0, x1 in col_spans:
            boxes.append((x0, y0, x1 - x0, y1 - y0))
    return boxes


def fallback_uniform_grid(img: np.ndarray) -> List[Tuple[int, int, int, int]]:
    """Even split — last resort if separator detection fails."""
    h, w = img.shape[:2]
    boxes: List[Tuple[int, int, int, int]] = []
    for r in range(ROWS):
        for c in range(COLS):
            x = round(w / COLS * c)
            y = round(h / ROWS * r)
            cw = round(w / COLS * (c + 1)) - x
            ch = round(h / ROWS * (r + 1)) - y
            boxes.append((x, y, cw, ch))
    return boxes


def square_crop(img: np.ndarray, box: Tuple[int, int, int, int]) -> np.ndarray:
    """Center-crop the bounding box to a square."""
    x, y, w, h = box
    side = min(w, h)
    cx = x + w // 2
    cy = y + h // 2
    x0 = max(0, cx - side // 2)
    y0 = max(0, cy - side // 2)
    x1 = x0 + side
    y1 = y0 + side
    H, W = img.shape[:2]
    x1 = min(x1, W)
    y1 = min(y1, H)
    return img[y0:y1, x0:x1]


def process(grid_path: Path, sequence_start: int) -> int:
    img = cv2.imread(str(grid_path))
    if img is None:
        print(f"  ! could not read {grid_path}")
        return 0

    boxes = detect_tile_boxes(img)
    detection_used = "gap-detection"
    if boxes is None:
        boxes = fallback_uniform_grid(img)
        detection_used = "uniform-fallback"

    debug = img.copy()
    for idx, box in enumerate(boxes, start=1):
        tile = square_crop(img, box)
        out_name = f"{grid_path.stem}_{idx:02d}.png"
        cv2.imwrite(str(OUTPUT_DIR / out_name), tile)
        flat_name = f"{sequence_start + idx - 1}.png"
        cv2.imwrite(str(ALL_DIR / flat_name), tile)

        x, y, w, h = box
        cv2.rectangle(debug, (x, y), (x + w, y + h), (0, 200, 0), 4)
        cv2.putText(
            debug,
            str(idx),
            (x + 12, y + 40),
            cv2.FONT_HERSHEY_SIMPLEX,
            1.1,
            (0, 0, 255),
            3,
            cv2.LINE_AA,
        )

    cv2.putText(
        debug,
        f"{grid_path.name}  -  {detection_used}  -  {len(boxes)} tiles",
        (16, debug.shape[0] - 20),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.8,
        (0, 0, 0),
        4,
        cv2.LINE_AA,
    )
    cv2.putText(
        debug,
        f"{grid_path.name}  -  {detection_used}  -  {len(boxes)} tiles",
        (16, debug.shape[0] - 20),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.8,
        (255, 255, 255),
        2,
        cv2.LINE_AA,
    )
    cv2.imwrite(str(DEBUG_DIR / f"{grid_path.stem}_debug.png"), debug)
    print(f"  {grid_path.name}: {len(boxes)} avatars ({detection_used})")
    return len(boxes)


def build_combined_debug() -> None:
    """Stitch all per-grid debug images into one overview."""
    debug_files = sorted(DEBUG_DIR.glob("*_debug.png"))
    if not debug_files:
        return
    images = [cv2.imread(str(p)) for p in debug_files]
    target_w = 800
    resized = []
    for im in images:
        if im is None:
            continue
        scale = target_w / im.shape[1]
        resized.append(cv2.resize(im, (target_w, round(im.shape[0] * scale))))
    if not resized:
        return
    cols = 2
    rows_count = (len(resized) + cols - 1) // cols
    cell_h = max(im.shape[0] for im in resized)
    cell_w = target_w
    canvas = np.full((cell_h * rows_count, cell_w * cols, 3), 245, dtype=np.uint8)
    for i, im in enumerate(resized):
        r, c = divmod(i, cols)
        h, w = im.shape[:2]
        canvas[r * cell_h : r * cell_h + h, c * cell_w : c * cell_w + w] = im
    cv2.imwrite(str(DEBUG_DIR / "_overview.png"), canvas)


def _grid_sort_key(p: Path):
    """Natural sort: '2.png' < '10.png'. Falls back to name for non-numeric stems."""
    try:
        return (0, int(p.stem))
    except ValueError:
        return (1, p.name)


def _reset_dir(path: Path) -> None:
    if path.exists():
        for f in path.iterdir():
            if f.is_file():
                f.unlink()
    path.mkdir(parents=True, exist_ok=True)


def main() -> None:
    _reset_dir(OUTPUT_DIR)
    _reset_dir(ALL_DIR)
    _reset_dir(DEBUG_DIR)

    grid_files = sorted(INPUT_DIR.glob("*.png"), key=_grid_sort_key)
    print(f"Processing {len(grid_files)} grid(s) from {INPUT_DIR}")
    total = 0
    for p in grid_files:
        count = process(p, sequence_start=total + 1)
        total += count
    build_combined_debug()
    print(f"Done. Wrote {total} avatars to {OUTPUT_DIR}")
    print(f"Flat sequence in {ALL_DIR} (1.png .. {total}.png)")
    print(f"Debug overlays in {DEBUG_DIR}")


if __name__ == "__main__":
    main()
