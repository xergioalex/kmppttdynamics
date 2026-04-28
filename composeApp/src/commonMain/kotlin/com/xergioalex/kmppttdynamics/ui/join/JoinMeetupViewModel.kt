package com.xergioalex.kmppttdynamics.ui.join

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xergioalex.kmppttdynamics.domain.Meetup
import com.xergioalex.kmppttdynamics.domain.MeetupParticipant
import com.xergioalex.kmppttdynamics.domain.ParticipantRole
import com.xergioalex.kmppttdynamics.meetups.MeetupRepository
import com.xergioalex.kmppttdynamics.participants.ParticipantRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class JoinMeetupUiState(
    val displayName: String = "",
    val joinAsHost: Boolean = false,
    val meetup: Meetup? = null,
    val isJoining: Boolean = false,
    val errorMessage: String? = null,
    val joined: MeetupParticipant? = null,
) {
    val canSubmit: Boolean get() = displayName.trim().length >= 2 && meetup != null && !isJoining
}

class JoinMeetupViewModel(
    private val meetups: MeetupRepository,
    private val participants: ParticipantRepository,
    initialDisplayName: String,
    private val meetupId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(JoinMeetupUiState(displayName = initialDisplayName))
    val state: StateFlow<JoinMeetupUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                _state.update { it.copy(meetup = meetups.findById(meetupId)) }
            } catch (t: Throwable) {
                _state.update { it.copy(errorMessage = t.message) }
            }
        }
    }

    fun onDisplayName(value: String) = _state.update { it.copy(displayName = value) }
    fun onJoinAsHost(value: Boolean) = _state.update { it.copy(joinAsHost = value) }
    fun consumeJoined() = _state.update { it.copy(joined = null) }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(isJoining = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val role = if (current.joinAsHost) ParticipantRole.HOST else ParticipantRole.PARTICIPANT
                val joined = participants.join(meetupId, current.displayName, role)
                _state.update { it.copy(isJoining = false, joined = joined) }
            } catch (t: Throwable) {
                _state.update { it.copy(isJoining = false, errorMessage = t.message) }
            }
        }
    }
}
