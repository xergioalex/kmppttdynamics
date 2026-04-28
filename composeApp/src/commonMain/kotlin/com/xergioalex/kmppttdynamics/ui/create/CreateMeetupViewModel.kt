package com.xergioalex.kmppttdynamics.ui.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xergioalex.kmppttdynamics.JoinCodeGenerator
import com.xergioalex.kmppttdynamics.domain.Meetup
import com.xergioalex.kmppttdynamics.domain.MeetupDraft
import com.xergioalex.kmppttdynamics.domain.MeetupParticipant
import com.xergioalex.kmppttdynamics.domain.MeetupStatus
import com.xergioalex.kmppttdynamics.domain.ParticipantRole
import com.xergioalex.kmppttdynamics.meetups.MeetupRepository
import com.xergioalex.kmppttdynamics.participants.ParticipantRepository
import com.xergioalex.kmppttdynamics.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State for the create-meetup screen. Holds the form fields plus the
 * resulting meetup + the host participant we created on submit.
 *
 * The display name no longer lives here — it comes from the user's
 * onboarding profile (`AppSettings.profile`). So this screen only
 * collects meetup metadata: title, description, join code.
 */
data class CreateMeetupUiState(
    val title: String = "",
    val description: String = "",
    val joinCode: String = JoinCodeGenerator.generate(),
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val created: Pair<Meetup, MeetupParticipant>? = null,
) {
    val canSubmit: Boolean
        get() = title.isNotBlank() && joinCode.length in 4..8 && !isSaving
}

class CreateMeetupViewModel(
    private val meetups: MeetupRepository,
    private val participants: ParticipantRepository,
    private val settings: AppSettings,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateMeetupUiState())
    val state: StateFlow<CreateMeetupUiState> = _state.asStateFlow()

    fun onTitle(value: String) = _state.update { it.copy(title = value) }
    fun onDescription(value: String) = _state.update { it.copy(description = value) }
    fun onJoinCode(value: String) = _state.update { it.copy(joinCode = value.uppercase().take(8)) }
    fun consumeCreated() = _state.update { it.copy(created = null) }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        val profile = settings.profile.value ?: run {
            _state.update { it.copy(errorMessage = "profile_missing") }
            return
        }
        _state.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val meetup = meetups.create(
                    MeetupDraft(
                        title = current.title.trim(),
                        description = current.description.trim().takeIf { it.isNotEmpty() },
                        joinCode = current.joinCode,
                        // The host immediately starts the room as live so
                        // participants can join without an extra step.
                        status = MeetupStatus.LIVE,
                    ),
                )
                val host = participants.join(
                    meetupId = meetup.id,
                    displayName = profile.displayName,
                    role = ParticipantRole.HOST,
                    clientId = settings.installClientId(),
                )
                settings.setParticipantIdFor(meetup.id, host.id)
                _state.update { it.copy(isSaving = false, created = meetup to host) }
            } catch (t: Throwable) {
                _state.update { it.copy(isSaving = false, errorMessage = t.message) }
            }
        }
    }
}
