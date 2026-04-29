package com.xergioalex.kmppttdynamics.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xergioalex.kmppttdynamics.AppContainer
import com.xergioalex.kmppttdynamics.ui.components.AvatarImage
import com.xergioalex.kmppttdynamics.ui.components.IconArrowBack
import com.xergioalex.kmppttdynamics.ui.components.IconCheck
import com.xergioalex.kmppttdynamics.ui.components.PttHorizontalMark
import com.xergioalex.kmppttdynamics.ui.components.TOTAL_AVATARS
import kmppttdynamics.composeapp.generated.resources.Res
import kmppttdynamics.composeapp.generated.resources.action_back
import kmppttdynamics.composeapp.generated.resources.action_cancel
import kmppttdynamics.composeapp.generated.resources.onboarding_continue
import kmppttdynamics.composeapp.generated.resources.onboarding_display_name
import kmppttdynamics.composeapp.generated.resources.onboarding_pick_another
import kmppttdynamics.composeapp.generated.resources.onboarding_picker_select
import kmppttdynamics.composeapp.generated.resources.onboarding_picker_taken_by
import kmppttdynamics.composeapp.generated.resources.onboarding_picker_title
import kmppttdynamics.composeapp.generated.resources.onboarding_save
import kmppttdynamics.composeapp.generated.resources.onboarding_subtitle
import kmppttdynamics.composeapp.generated.resources.onboarding_subtitle_edit
import kmppttdynamics.composeapp.generated.resources.onboarding_title
import kmppttdynamics.composeapp.generated.resources.onboarding_title_edit
import org.jetbrains.compose.resources.stringResource

/**
 * Profile screen shown:
 *   - on first launch (`editing = false`) before anything else,
 *   - and from the home profile chip (`editing = true`).
 *
 * The user picks a unique avatar (server-deduped) and a display name.
 * Both are persisted in `AppSettings` and in the server-side
 * `app_users` row, so the rest of the app can stop asking who you are.
 */
@Composable
fun OnboardingScreen(
    container: AppContainer,
    editing: Boolean,
    onComplete: () -> Unit,
    onCancel: (() -> Unit)? = null,
) {
    val vm: OnboardingViewModel = viewModel(key = "onboarding-$editing") {
        OnboardingViewModel(
            users = container.users,
            participants = container.participants,
            settings = container.settings,
            editing = editing,
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()
    var showPicker by remember { mutableStateOf(false) }

    LaunchedEffect(state.finished) {
        if (state.finished != null) {
            vm.consumeFinished()
            onComplete()
        }
    }

    if (showPicker) {
        AvatarPickerView(
            selected = state.selectedAvatar,
            taken = state.takenAvatars,
            takenBy = remember(state.allUsers) {
                state.allUsers.associate { it.avatarId to it.displayName }
            },
            onPick = { id ->
                vm.pickAvatar(id)
                showPicker = false
            },
            onClose = { showPicker = false },
        )
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!editing) {
            PttHorizontalMark()
            Spacer(Modifier.height(20.dp))
        }
        Text(
            stringResource(if (editing) Res.string.onboarding_title_edit else Res.string.onboarding_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(if (editing) Res.string.onboarding_subtitle_edit else Res.string.onboarding_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        if (state.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(20.dp))
        }

        // Avatar preview ring
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable { showPicker = true },
            contentAlignment = Alignment.Center,
        ) {
            AvatarImage(
                avatarId = state.selectedAvatar,
                size = 144.dp,
            )
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = { showPicker = true }) {
            Text(stringResource(Res.string.onboarding_pick_another))
        }
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(
            value = state.displayName,
            onValueChange = vm::onDisplayName,
            singleLine = true,
            label = { Text(stringResource(Res.string.onboarding_display_name)) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.errorMessage != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                state.errorMessage ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.weight(1f))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (onCancel != null) {
                TextButton(onClick = onCancel, enabled = !state.isSaving) {
                    Text(stringResource(Res.string.action_cancel))
                }
                Spacer(Modifier.size(8.dp))
            }
            Button(
                onClick = vm::submit,
                enabled = state.canSubmit,
                modifier = Modifier.fillMaxWidth(if (onCancel != null) 0.6f else 1f),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        stringResource(if (editing) Res.string.onboarding_save else Res.string.onboarding_continue),
                    )
                }
            }
        }
    }
}

/** Full-screen grid of all 132 avatars with locks on taken ones. */
@Composable
private fun AvatarPickerView(
    selected: Int,
    taken: Set<Int>,
    takenBy: Map<Int, String>,
    onPick: (Int) -> Unit,
    onClose: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(top = 16.dp)) {
        // Sticky header with the back arrow, title, AND a thumbnail of
        // the currently-selected avatar so the user keeps a reference
        // point while scrolling through the 132-tile grid.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                IconArrowBack(tint = MaterialTheme.colorScheme.onSurface)
            }
            // Selected-avatar preview: ringed in primary, same look as
            // a selected tile so the visual language is consistent.
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .border(
                        width = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                    )
                    .padding(3.dp),
                contentAlignment = Alignment.Center,
            ) {
                AvatarImage(avatarId = selected, size = 42.dp)
            }
            Spacer(Modifier.size(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(Res.string.onboarding_picker_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(Res.string.onboarding_picker_select),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onClose) {
                Text(stringResource(Res.string.action_back))
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 84.dp),
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
        ) {
            items(
                items = (1..TOTAL_AVATARS).toList(),
                key = { it },
            ) { id ->
                AvatarTile(
                    id = id,
                    isSelected = id == selected,
                    isTaken = id in taken,
                    takenByName = takenBy[id],
                    onClick = { if (id !in taken) onPick(id) },
                )
            }
        }
    }
}

@Composable
private fun AvatarTile(
    id: Int,
    isSelected: Boolean,
    isTaken: Boolean,
    takenByName: String?,
    onClick: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val surface = MaterialTheme.colorScheme.surface

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(enabled = !isTaken, onClick = onClick)
            .padding(2.dp),
    ) {
        Box(
            // Selected tile gets a slight scale-up so it visually pops
            // out of the grid even at-a-glance while scrolling.
            modifier = Modifier
                .size(86.dp)
                .graphicsLayer {
                    if (isSelected) {
                        scaleX = 1.05f
                        scaleY = 1.05f
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            // Outer ring + inner ring sandwich: a thick primary ring on
            // the outside with a thin surface-colored ring inside, so
            // the selection reads cleanly on both light and dark
            // surfaces and against the avatar's own border. The fill
            // behind the avatar uses primaryContainer so even tiles
            // that are mostly cool-toned (water creature, etc.) get
            // a warm halo that matches the room's accent color.
            Box(
                modifier = Modifier
                    .size(86.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) primaryContainer else Color.Transparent)
                    .then(
                        if (isSelected) {
                            Modifier
                                .border(width = 4.dp, color = primary, shape = CircleShape)
                                .padding(4.dp)
                                .border(width = 2.dp, color = surface, shape = CircleShape)
                                .padding(2.dp)
                        } else {
                            Modifier.padding(3.dp)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(modifier = Modifier.alpha(if (isTaken) 0.35f else 1f)) {
                    AvatarImage(avatarId = id, size = 76.dp)
                }
                if (isTaken) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("🔒", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
            // Floating checkmark badge in the bottom-right corner of
            // the selected tile. Sits on top of the rings so it stays
            // legible regardless of the underlying avatar art.
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(primary)
                        .border(2.dp, surface, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    IconCheck(
                        tint = onPrimary,
                        size = 16.dp,
                    )
                }
            }
        }
        if (isTaken && takenByName != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(Res.string.onboarding_picker_taken_by, takenByName),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}
