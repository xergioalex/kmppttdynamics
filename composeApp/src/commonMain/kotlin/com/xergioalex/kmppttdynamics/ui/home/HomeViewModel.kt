package com.xergioalex.kmppttdynamics.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xergioalex.kmppttdynamics.domain.Meetup
import com.xergioalex.kmppttdynamics.meetups.HomeFeed
import com.xergioalex.kmppttdynamics.meetups.MeetupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val live: List<Meetup> = emptyList(),
    val past: List<Meetup> = emptyList(),
    val isLoading: Boolean = true,
    val joinError: String? = null,
    val joinPending: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface HomeEvent {
    data class JoinResolved(val meetupId: String) : HomeEvent
}

class HomeViewModel(private val meetups: MeetupRepository) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val _events = MutableStateFlow<HomeEvent?>(null)
    val events: StateFlow<HomeEvent?> = _events.asStateFlow()

    init {
        viewModelScope.launch {
            meetups.observeAll()
                .catch { t -> _state.update { it.copy(isLoading = false, errorMessage = t.message) } }
                .collect { feed: HomeFeed ->
                    _state.update {
                        it.copy(live = feed.live, past = feed.past, isLoading = false, errorMessage = null)
                    }
                }
        }
    }

    fun consumeEvent() { _events.value = null }

    fun onJoinByCode(code: String) {
        val trimmed = code.trim().uppercase()
        if (trimmed.isBlank()) return
        _state.update { it.copy(joinPending = true, joinError = null) }
        viewModelScope.launch {
            try {
                val meetup = meetups.findByJoinCode(trimmed)
                if (meetup == null) {
                    _state.update { it.copy(joinPending = false, joinError = NOT_FOUND) }
                } else {
                    _state.update { it.copy(joinPending = false, joinError = null) }
                    _events.value = HomeEvent.JoinResolved(meetup.id)
                }
            } catch (t: Throwable) {
                _state.update { it.copy(joinPending = false, joinError = t.message ?: NOT_FOUND) }
            }
        }
    }

    private companion object {
        const val NOT_FOUND = "join_meetup_not_found"
    }
}
