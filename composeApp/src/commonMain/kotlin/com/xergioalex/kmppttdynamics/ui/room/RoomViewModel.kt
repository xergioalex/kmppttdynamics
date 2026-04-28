package com.xergioalex.kmppttdynamics.ui.room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xergioalex.kmppttdynamics.appusers.AppUserRepository
import com.xergioalex.kmppttdynamics.domain.AppUser
import com.xergioalex.kmppttdynamics.domain.Meetup
import com.xergioalex.kmppttdynamics.domain.MeetupParticipant
import com.xergioalex.kmppttdynamics.domain.MeetupStatus
import com.xergioalex.kmppttdynamics.domain.ParticipantRole
import com.xergioalex.kmppttdynamics.meetups.MeetupRepository
import com.xergioalex.kmppttdynamics.participants.ParticipantRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RoomUiState(
    val meetup: Meetup? = null,
    val participants: List<MeetupParticipant> = emptyList(),
    /**
     * Map of `client_id -> AppUser` for every install we've seen in
     * the realtime feed. Used by the room UI to resolve a participant's
     * avatar (and the latest display name) without re-querying.
     */
    val usersByClientId: Map<String, AppUser> = emptyMap(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
) {
    val onlineCount: Int get() = participants.count { it.isOnline }
    val totalCount: Int get() = participants.size
}

class RoomViewModel(
    private val meetups: MeetupRepository,
    private val participants: ParticipantRepository,
    private val users: AppUserRepository,
    private val meetupId: String,
    private val mySelfId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(RoomUiState())
    val state: StateFlow<RoomUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val meetup = meetups.findById(meetupId)
                _state.update { it.copy(meetup = meetup, isLoading = false) }
            } catch (t: Throwable) {
                _state.update { it.copy(isLoading = false, errorMessage = t.message) }
            }
        }
        viewModelScope.launch {
            participants.observe(meetupId)
                .catch { t -> _state.update { it.copy(errorMessage = t.message) } }
                .collect { list -> _state.update { it.copy(participants = list) } }
        }
        // Cross-meetup user identity feed — gives us each participant's
        // avatar and latest display name in real time.
        viewModelScope.launch {
            users.observeAll()
                .catch { /* non-fatal */ }
                .collect { list ->
                    _state.update { current ->
                        current.copy(usersByClientId = list.associateBy { it.clientId })
                    }
                }
        }
        // Mark ourselves online on entry. With encodeDefaults = true on
        // the JSON serializer, our JoinRequest already inserts as
        // is_online = true; this flips the bit back on if we're
        // reactivating an existing participant row after a back-out.
        viewModelScope.launch {
            runCatching { participants.setOnline(mySelfId, true) }
        }
    }

    fun setStatus(status: MeetupStatus) {
        viewModelScope.launch {
            runCatching { meetups.updateStatus(meetupId, status) }
                .onSuccess { _state.update { s -> s.copy(meetup = it) } }
                .onFailure { t -> _state.update { it.copy(errorMessage = t.message) } }
        }
    }

    /**
     * Promote / demote another participant. The realtime feed will
     * reflect the change for everyone in the room (including the
     * affected participant — their UI flips into / out of host mode).
     */
    fun setParticipantRole(participantId: String, role: ParticipantRole) {
        viewModelScope.launch {
            runCatching { participants.setRole(participantId, role) }
                .onFailure { t -> _state.update { it.copy(errorMessage = t.message) } }
        }
    }

    /**
     * Marks the user as offline and notifies the screen layer to navigate
     * away. The participant row stays in the database so the user can
     * resume their seat from home — including hosts, who keep their
     * `role = host` even while offline.
     */
    fun leave(onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching { participants.setOnline(mySelfId, false) }
            onDone()
        }
    }
}
