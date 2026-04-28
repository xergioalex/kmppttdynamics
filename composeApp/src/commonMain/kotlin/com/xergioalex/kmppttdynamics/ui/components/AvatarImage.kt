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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
 * Renders the bundled avatar at id [avatarId] (1-based, matching the
 * file `composeResources/files/avatars/<id>.png`).
 *
 * Avatars are loaded as raw bytes via the Compose Resources file API
 * and decoded into an ImageBitmap on first composition. The decoded
 * bitmap is `remember`ed so re-composition inside lazy lists doesn't
 * re-decode. A small placeholder shows the avatar id while the bytes
 * load — handy on the picker grid where 132 avatars stream in.
 */
@OptIn(ExperimentalResourceApi::class)
@Composable
fun AvatarImage(
    avatarId: Int,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    contentDescription: String? = null,
) {
    val bytes by produceState<ByteArray?>(initialValue = null, key1 = avatarId) {
        value = runCatching { Res.readBytes("files/avatars/$avatarId.png") }.getOrNull()
    }
    val bitmap: ImageBitmap? = remember(bytes) { bytes?.decodeToImageBitmap() }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
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
