package com.xergioalex.kmppttdynamics.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xergioalex.kmppttdynamics.domain.Meetup
import com.xergioalex.kmppttdynamics.domain.MeetupParticipant
import com.xergioalex.kmppttdynamics.domain.ParticipantRole
import com.xergioalex.kmppttdynamics.meetups.HomeFeed
import com.xergioalex.kmppttdynamics.meetups.MeetupRepository
import com.xergioalex.kmppttdynamics.participants.ParticipantRepository
import com.xergioalex.kmppttdynamics.presence.GlobalPresenceTracker
import com.xergioalex.kmppttdynamics.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val live: List<Meetup> = emptyList(),
    val past: List<Meetup> = emptyList(),
    /** App-wide online count (including this device) from the global presence channel. */
    val globalOnline: Int = 1,
    val isLoading: Boolean = true,
    val joinError: String? = null,
    val joinPending: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface HomeEvent {
    /** Always carries a non-null participant — the UI just navigates straight to the room. */
    data class EnterRoom(val meetupId: String, val participant: MeetupParticipant) : HomeEvent
}

class HomeViewModel(
    private val meetups: MeetupRepository,
    private val participants: ParticipantRepository,
    private val settings: AppSettings,
    private val presence: GlobalPresenceTracker,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val _events = MutableStateFlow<HomeEvent?>(null)
    val events: StateFlow<HomeEvent?> = _events.asStateFlow()

    init {
        viewModelScope.launch {
            meetups.observeAll()
                .combine(presence.onlineCount) { feed: HomeFeed, online: Int -> feed to online }
                .catch { t -> _state.update { it.copy(isLoading = false, errorMessage = t.message) } }
                .collect { (feed, online) ->
                    _state.update {
                        it.copy(
                            live = feed.live,
                            past = feed.past,
                            globalOnline = online.coerceAtLeast(1),
                            isLoading = false,
                            errorMessage = null,
                        )
                    }
                }
        }
    }

    fun consumeEvent() { _events.value = null }

    /**
     * Resolves what should happen when the user taps a meetup card.
     *
     * Resolution order:
     *
     * 1. **Cached participant id** (set when this device last joined
     *    the room). If the row still exists, reuse it and flip
     *    `is_online = true`.
     * 2. **Server-side lookup by install client id**. The
     *    `(meetup_id, client_id)` unique index means there can only
     *    ever be one row per device per meetup. If we find one — even
     *    without a local cache (e.g. fresh install on the same device,
     *    or cache cleared) — we reuse it and refresh the cache.
     * 3. **No row anywhere** → route through the Join screen, which
     *    will upsert a new row keyed by client id.
     *
     * Transient network errors are NOT treated as "no row" — that's
     * what previously inflated the room counts to 8 with only 2 real
     * devices. On error we keep the cache, surface the message, and
     * stay on Home.
     */
    /**
     * Resolves the participant row this device should be in, then
     * navigates straight to the room. There is no per-meetup join
     * screen anymore — the device's profile (display name + avatar)
     * was set during onboarding and is reused everywhere.
     *
     * Resolution order:
     *  1. Server lookup by `(meetup_id, install_client_id)` — the
     *     authoritative source of truth.
     *  2. Pre-migration cache fallback: a row pointed to by
     *     `AppSettings.participantIdFor(meetupId)` whose `client_id`
     *     is still NULL gets claimed for this install.
     *  3. No row anywhere → auto-join with the local profile.
     */
    fun onEnterMeetup(meetupId: String) {
        val profile = settings.profile.value
        if (profile == null) {
            _state.update { it.copy(joinError = "profile_missing") }
            return
        }
        viewModelScope.launch {
            val myClientId = settings.installClientId()
            val cached = settings.participantIdFor(meetupId)
            try {
                var existing = participants.findByClientId(meetupId, myClientId)
                if (existing == null && cached != null) {
                    val byCache = participants.findById(cached)
                    if (byCache != null) {
                        existing = when (byCache.clientId) {
                            null -> participants.claim(byCache.id, myClientId) ?: byCache
                            myClientId -> byCache
                            else -> null
                        }
                    }
                }
                if (existing == null) {
                    // Auto-join: no JoinMeetupScreen anymore; the
                    // identity comes from AppSettings.profile.
                    val joined = participants.join(
                        meetupId = meetupId,
                        displayName = profile.displayName,
                        role = ParticipantRole.PARTICIPANT,
                        clientId = myClientId,
                    )
                    settings.setParticipantIdFor(meetupId, joined.id)
                    _events.value = HomeEvent.EnterRoom(meetupId, joined)
                } else {
                    if (cached != existing.id) {
                        settings.setParticipantIdFor(meetupId, existing.id)
                    }
                    runCatching { participants.setOnline(existing.id, true) }
                    _events.value = HomeEvent.EnterRoom(meetupId, existing)
                }
            } catch (t: Throwable) {
                _state.update { it.copy(joinError = t.message ?: "network_error") }
            }
        }
    }

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
                    onEnterMeetup(meetup.id)
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
