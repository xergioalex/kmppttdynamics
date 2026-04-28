package com.xergioalex.kmppttdynamics

import com.xergioalex.kmppttdynamics.chat.ChatRepository
import com.xergioalex.kmppttdynamics.handraise.HandRepository
import com.xergioalex.kmppttdynamics.meetups.MeetupRepository
import com.xergioalex.kmppttdynamics.participants.ParticipantRepository
import com.xergioalex.kmppttdynamics.polls.PollRepository
import com.xergioalex.kmppttdynamics.qa.QuestionRepository
import com.xergioalex.kmppttdynamics.raffles.RaffleRepository
import com.xergioalex.kmppttdynamics.settings.AppSettings
import com.xergioalex.kmppttdynamics.supabase.SupabaseClientProvider

/**
 * DI-lite container. Held at the platform entry points and threaded
 * through composables / view models. Built lazily — touching any repo
 * before Supabase is configured will throw via [SupabaseClientProvider].
 */
class AppContainer(val settings: AppSettings) {

    /** True when build-time SUPABASE_URL + SUPABASE_PUBLISHABLE_KEY were filled in. */
    val isSupabaseConfigured: Boolean get() = SupabaseClientProvider.isConfigured

    val meetups: MeetupRepository           by lazy { MeetupRepository(SupabaseClientProvider.client) }
    val participants: ParticipantRepository by lazy { ParticipantRepository(SupabaseClientProvider.client) }
    val chat: ChatRepository                by lazy { ChatRepository(SupabaseClientProvider.client) }
    val hands: HandRepository               by lazy { HandRepository(SupabaseClientProvider.client) }
    val questions: QuestionRepository       by lazy { QuestionRepository(SupabaseClientProvider.client) }
    val polls: PollRepository               by lazy { PollRepository(SupabaseClientProvider.client) }
    val raffles: RaffleRepository           by lazy { RaffleRepository(SupabaseClientProvider.client) }
}
