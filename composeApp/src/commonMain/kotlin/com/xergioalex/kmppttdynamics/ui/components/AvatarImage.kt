package com.xergioalex.kmppttdynamics.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kmppttdynamics.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * Process-wide cache of decoded avatar bitmaps keyed by avatar id.
 *
 * The first time [AvatarImage] is composed for an id we read the PNG
 * from `composeResources/files/avatars/<id>.png` and decode it into an
 * `ImageBitmap`. Decoding 132 different avatars during normal use is
 * fine; **decoding the same avatar 20 times in 2 seconds during the
 * raffle spin animation is not** — it makes the UI thread janky on
 * Android. Stashing the decoded bitmap in this cache lets every
 * subsequent composition (and the spin animation) reuse it for free.
 *
 * The cache is unbounded by design: 132 ImageBitmaps at 192×192 are
 * only ≈ 20 MB total even if every single one ends up cached, which
 * is acceptable for a session-scoped LRU we don't bother evicting.
 */
private val decodedAvatarCache: MutableMap<Int, ImageBitmap> = mutableMapOf()

/**
 * Renders the bundled avatar at id [avatarId] (1-based, matching the
 * file `composeResources/files/avatars/<id>.png`).
 *
 * Avatars are loaded as raw bytes via the Compose Resources file API
 * and decoded into an `ImageBitmap`. The decoded bitmap is cached
 * process-wide ([decodedAvatarCache]) so re-using the same id from a
 * different call site (e.g. the same avatar in `EntryAvatarStack` and
 * later in a `WinnerReveal` spin) doesn't re-decode the PNG.
 */
@OptIn(ExperimentalResourceApi::class)
@Composable
fun AvatarImage(
    avatarId: Int,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    contentDescription: String? = null,
) {
    // Recreating the state on every avatarId change lets us synchronously
    // pick up cached bitmaps as the initial value — important for the
    // raffle spin animation where avatarId flips ~20× per second and a
    // null-flash between frames would look broken.
    var bitmap: ImageBitmap? by remember(avatarId) {
        mutableStateOf(decodedAvatarCache[avatarId])
    }
    LaunchedEffect(avatarId) {
        if (bitmap != null) return@LaunchedEffect
        val bytes = runCatching { Res.readBytes("files/avatars/$avatarId.png") }.getOrNull()
        val decoded = bytes?.decodeToImageBitmap()
        if (decoded != null) {
            decodedAvatarCache[avatarId] = decoded
        }
        bitmap = decoded
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = "$avatarId",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Total number of bundled avatars under composeResources/files/avatars/. */
const val TOTAL_AVATARS = 132
