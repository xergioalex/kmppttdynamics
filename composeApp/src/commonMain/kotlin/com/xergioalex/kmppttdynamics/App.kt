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
import com.xergioalex.kmppttdynamics.ui.onboarding.OnboardingScreen
import com.xergioalex.kmppttdynamics.ui.room.RoomScreen
import com.xergioalex.kmppttdynamics.ui.theme.AppTheme
import kmppttdynamics.composeapp.generated.resources.Res
import kmppttdynamics.composeapp.generated.resources.error_supabase_not_configured
import org.jetbrains.compose.resources.stringResource

private sealed interface Screen {
    data object Home : Screen
    data object Create : Screen
    data object EditProfile : Screen
    data class Room(val meetupId: String, val me: MeetupParticipant) : Screen

    /**
     * Pops the profile editor on top of an active room and remembers
     * which room to bounce back to when the user finishes (or cancels).
     * Lets the user fix their avatar / name without leaving the meetup.
     */
    data class RoomEditProfile(val returnTo: Room) : Screen
}

@Composable
fun App(container: AppContainer) {
    val themeMode by container.settings.themeMode.collectAsStateWithLifecycle()
    val profile by container.settings.profile.collectAsStateWithLifecycle()

    AppTheme(themeMode) {
        Surface(
            modifier = Modifier.fillMaxSize().safeContentPadding(),
            color = MaterialTheme.colorScheme.background,
        ) {
            if (!container.isSupabaseConfigured) {
                NotConfiguredScreen()
                return@Surface
            }

            // First-launch gate: until the user has chosen a name and
            // an avatar, every other screen is unreachable.
            if (profile == null) {
                OnboardingScreen(
                    container = container,
                    editing = false,
                    onComplete = { /* state flow flip will re-route automatically */ },
                )
                return@Surface
            }

            // Navigation is in-memory for Milestone 1; process-death restore
            // will land with a proper nav graph later.
            var screen by remember { mutableStateOf<Screen>(Screen.Home) }

            when (val s = screen) {
                Screen.Home -> HomeScreen(
                    container = container,
                    onCreate = { screen = Screen.Create },
                    onEditProfile = { screen = Screen.EditProfile },
                    onEnterRoom = { meetupId, existing ->
                        screen = Screen.Room(meetupId, existing)
                    },
                )

                Screen.Create -> CreateMeetupScreen(
                    container = container,
                    onCancel = { screen = Screen.Home },
                    onCreated = { _, host ->
                        screen = Screen.Room(host.meetupId, host)
                    },
                )

                Screen.EditProfile -> OnboardingScreen(
                    container = container,
                    editing = true,
                    onComplete = { screen = Screen.Home },
                    onCancel = { screen = Screen.Home },
                )

                is Screen.Room -> RoomScreen(
                    container = container,
                    meetupId = s.meetupId,
                    me = s.me,
                    onLeave = { screen = Screen.Home },
                    onEditProfile = { screen = Screen.RoomEditProfile(s) },
                )

                is Screen.RoomEditProfile -> OnboardingScreen(
                    container = container,
                    editing = true,
                    onComplete = { screen = s.returnTo },
                    onCancel = { screen = s.returnTo },
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
