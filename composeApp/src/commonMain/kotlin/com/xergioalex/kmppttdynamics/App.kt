package com.xergioalex.kmppttdynamics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xergioalex.kmppttdynamics.domain.MeetupParticipant
import com.xergioalex.kmppttdynamics.ui.components.PttVerticalMark
import com.xergioalex.kmppttdynamics.ui.create.CreateMeetupScreen
import com.xergioalex.kmppttdynamics.ui.home.HomeScreen
import com.xergioalex.kmppttdynamics.ui.join.JoinMeetupScreen
import com.xergioalex.kmppttdynamics.ui.room.RoomScreen
import com.xergioalex.kmppttdynamics.ui.theme.AppTheme
import kmppttdynamics.composeapp.generated.resources.Res
import kmppttdynamics.composeapp.generated.resources.error_supabase_not_configured
import org.jetbrains.compose.resources.stringResource

private sealed interface Screen {
    data object Home : Screen
    data object Create : Screen
    data class Join(val meetupId: String) : Screen
    data class Room(val meetupId: String, val me: MeetupParticipant) : Screen
}

@Composable
fun App(container: AppContainer) {
    val themeMode by container.settings.themeMode.collectAsStateWithLifecycle()

    AppTheme(themeMode) {
        Surface(
            modifier = Modifier.fillMaxSize().safeContentPadding(),
            color = MaterialTheme.colorScheme.background,
        ) {
            if (!container.isSupabaseConfigured) {
                NotConfiguredScreen()
                return@Surface
            }

            // Navigation is in-memory for Milestone 1; process-death restore
            // will land with a proper nav graph later.
            var screen by remember { mutableStateOf<Screen>(Screen.Home) }

            when (val s = screen) {
                Screen.Home -> HomeScreen(
                    container = container,
                    onCreate = { screen = Screen.Create },
                    onJoin = { meetupId -> screen = Screen.Join(meetupId) },
                )

                Screen.Create -> CreateMeetupScreen(
                    container = container,
                    onCancel = { screen = Screen.Home },
                    onCreated = { meetup -> screen = Screen.Join(meetup.id) },
                )

                is Screen.Join -> JoinMeetupScreen(
                    container = container,
                    meetupId = s.meetupId,
                    initialDisplayName = remember { container.settings.lastDisplayName().orEmpty() },
                    onCancel = { screen = Screen.Home },
                    onJoined = { participant ->
                        container.settings.setLastDisplayName(participant.displayName)
                        screen = Screen.Room(s.meetupId, participant)
                    },
                )

                is Screen.Room -> RoomScreen(
                    container = container,
                    meetupId = s.meetupId,
                    me = s.me,
                    onLeave = { screen = Screen.Home },
                )
            }
        }
    }
}

@Composable
private fun NotConfiguredScreen() {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            PttVerticalMark()
            Text(
                text = stringResource(Res.string.error_supabase_not_configured),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}
