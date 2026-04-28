package com.xergioalex.kmppttdynamics.supabase

import com.xergioalex.kmppttdynamics.config.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.realtime

object SupabaseClientProvider {

    /**
     * `true` only if BuildKonfig was generated with both SUPABASE_URL and
     * SUPABASE_PUBLISHABLE_KEY filled in (i.e. .env existed at build time).
     * The UI uses this to decide whether to render screens or a friendly
     * "configure your .env" placeholder.
     */
    val isConfigured: Boolean by lazy {
        BuildConfig.SUPABASE_URL.isNotBlank() &&
            BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()
    }

    val client: SupabaseClient by lazy {
        check(isConfigured) {
            "Supabase is not configured. Copy .env.example to .env, fill in " +
                "SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY, and rebuild."
        }
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
        ) {
            install(Postgrest)
            install(Realtime)
        }
    }

    /** Returns the realtime plugin handle (not used directly today, but
     *  threaded through so callers don't need to import the extension). */
    val realtime get() = client.realtime
}
