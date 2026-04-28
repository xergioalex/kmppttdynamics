package com.xergioalex.kmppttdynamics

import com.xergioalex.kmppttdynamics.meetups.MeetupRepository
import com.xergioalex.kmppttdynamics.participants.ParticipantRepository
import com.xergioalex.kmppttdynamics.settings.AppSettings
import com.xergioalex.kmppttdynamics.supabase.SupabaseClientProvider

/**
 * DI-lite container. Held at the platform entry points and threaded
 * through composables / view models. Built lazily — touching it before
 * Supabase is configured will throw via [SupabaseClientProvider].
 */
class AppContainer(val settings: AppSettings) {

    /** True when build-time SUPABASE_URL + SUPABASE_PUBLISHABLE_KEY were filled in. */
    val isSupabaseConfigured: Boolean get() = SupabaseClientProvider.isConfigured

    val meetups: MeetupRepository by lazy { MeetupRepository(SupabaseClientProvider.client) }
    val participants: ParticipantRepository by lazy { ParticipantRepository(SupabaseClientProvider.client) }
}
