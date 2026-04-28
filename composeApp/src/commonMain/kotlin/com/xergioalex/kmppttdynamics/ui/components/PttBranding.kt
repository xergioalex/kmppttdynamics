package com.xergioalex.kmppttdynamics.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import kmppttdynamics.composeapp.generated.resources.Res
import kmppttdynamics.composeapp.generated.resources.app_tagline
import kmppttdynamics.composeapp.generated.resources.app_title
import kmppttdynamics.composeapp.generated.resources.ptt_logo_horizontal
import kmppttdynamics.composeapp.generated.resources.ptt_logo_vertical
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Vertical lockup — used on home / splash. Logo art is black; we tint it
 * with the current onBackground color so it inverts cleanly in dark mode.
 */
@Composable
fun PttVerticalMark(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(
            painter = painterResource(Res.drawable.ptt_logo_vertical),
            contentDescription = stringResource(Res.string.app_title),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
            modifier = Modifier.height(180.dp),
        )
        Text(
            text = stringResource(Res.string.app_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )
    }
}

/** Horizontal lockup — used in top bars. */
@Composable
fun PttHorizontalMark(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(Res.drawable.ptt_logo_horizontal),
        contentDescription = stringResource(Res.string.app_title),
        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
        modifier = modifier.height(36.dp),
    )
}
