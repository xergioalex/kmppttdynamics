package com.xergioalex.kmppttdynamics.ui.room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xergioalex.kmppttdynamics.domain.Meetup
import com.xergioalex.kmppttdynamics.domain.MeetupParticipant
import com.xergioalex.kmppttdynamics.domain.MeetupStatus
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
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
) {
    val onlineCount: Int get() = participants.count { it.isOnline }
    val totalCount: Int get() = participants.size
}

class RoomViewModel(
    private val meetups: MeetupRepository,
    private val participants: ParticipantRepository,
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
        // Mark ourselves online on entry. We don't currently flip back to
        // offline on leave because that requires a bullet-proof lifecycle
        // hook on every platform — adding it is part of Milestone 2.
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

    fun leave(onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching { participants.setOnline(mySelfId, false) }
            onDone()
        }
    }
}
