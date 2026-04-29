package com.xergioalex.kmppttdynamics.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xergioalex.kmppttdynamics.appusers.AppUserRepository
import com.xergioalex.kmppttdynamics.domain.AppUser
import com.xergioalex.kmppttdynamics.domain.AppUserDraft
import com.xergioalex.kmppttdynamics.participants.ParticipantRepository
import com.xergioalex.kmppttdynamics.settings.AppSettings
import com.xergioalex.kmppttdynamics.ui.components.TOTAL_AVATARS
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives both the first-launch onboarding flow and the
 * "edit profile" flow. The two are the same screen — the entry
 * point passes [editing] = true when invoked from the home chip.
 *
 * Holds:
 *  - [selectedAvatar]: id currently chosen (initially randomized to a
 *    free avatar; never equal to one already taken by another user).
 *  - [takenAvatars]: realtime set of ids that are NOT available
 *    (claimed by users other than us). Used by both the picker grid
 *    and the on-screen avatar preview to disable / lock entries.
 *  - [allUsers]: full server snapshot so the picker can render any
 *    other user's display name on a locked tile (nice tooltip later).
 */
data class OnboardingUiState(
    val displayName: String = "",
    val selectedAvatar: Int = 1,
    val takenAvatars: Set<Int> = emptySet(),
    val allUsers: List<AppUser> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val finished: AppUser? = null,
) {
    val canSubmit: Boolean
        get() = displayName.trim().length >= 2 &&
            selectedAvatar in 1..TOTAL_AVATARS &&
            selectedAvatar !in takenAvatars &&
            !isSaving
}

class OnboardingViewModel(
    private val users: AppUserRepository,
    private val participants: ParticipantRepository,
    private val settings: AppSettings,
    /** True when launched from the home profile chip (re-pick name / avatar). */
    private val editing: Boolean,
) : ViewModel() {

    private val clientId = settings.installClientId()

    private val _state = MutableStateFlow(
        OnboardingUiState(
            displayName = settings.profile.value?.displayName.orEmpty(),
            selectedAvatar = settings.profile.value?.avatarId ?: 1,
        ),
    )
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    init {
        // Live-update the taken set so freshly-claimed avatars
        // immediately disable in the picker / preview.
        viewModelScope.launch {
            users.observeAll()
                .catch { t -> _state.update { it.copy(isLoading = false, errorMessage = t.message) } }
                .collect { allUsers ->
                    val taken = allUsers
                        .filter { it.clientId != clientId }
                        .map { it.avatarId }
                        .toSet()
                    _state.update { current ->
                        // First load picks a random free avatar so the user
                        // doesn't always start on #1. We only randomize when
                        // we don't already have a profile to edit.
                        val initial = current.selectedAvatar
                        val selected = when {
                            editing && initial !in taken -> initial
                            initial !in taken && initial in 1..TOTAL_AVATARS -> initial
                            else -> randomAvailable(taken) ?: initial
                        }
                        current.copy(
                            allUsers = allUsers,
                            takenAvatars = taken,
                            selectedAvatar = selected,
                            isLoading = false,
                        )
                    }
                }
        }
    }

    fun onDisplayName(value: String) = _state.update { it.copy(displayName = value.take(40)) }

    fun pickAvatar(avatarId: Int) {
        if (avatarId !in 1..TOTAL_AVATARS) return
        if (avatarId in _state.value.takenAvatars) return
        _state.update { it.copy(selectedAvatar = avatarId) }
    }

    fun consumeFinished() = _state.update { it.copy(finished = null) }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val saved = users.upsert(
                    AppUserDraft(
                        clientId = clientId,
                        displayName = current.displayName.trim(),
                        avatarId = current.selectedAvatar,
                    ),
                )
                settings.setProfile(saved.displayName, saved.avatarId)
                // Propagate the new name to every meetup_participants row
                // this device owns so chat / Q&A / members lists stop
                // showing the old name. Best-effort: a transient
                // failure here shouldn't block the local save.
                runCatching { participants.syncDisplayName(clientId, saved.displayName) }
                _state.update { it.copy(isSaving = false, finished = saved) }
            } catch (t: Throwable) {
                _state.update { it.copy(isSaving = false, errorMessage = t.message ?: "save_failed") }
            }
        }
    }

    private fun randomAvailable(taken: Set<Int>): Int? {
        val available = (1..TOTAL_AVATARS).filter { it !in taken }
        if (available.isEmpty()) return null
        return available[Random.nextInt(available.size)]
    }
}
