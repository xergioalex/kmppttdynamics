package com.xergioalex.kmppttdynamics.supabase

import com.xergioalex.kmppttdynamics.config.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.serialization.json.Json

object SupabaseClientProvider {

    /**
     * Shared JSON instance with `encodeDefaults = true` so that data
     * classes used for inserts include their default values in the
     * payload sent to Postgrest. Without this, fields like
     * `is_online = true` on [com.xergioalex.kmppttdynamics.domain.JoinRequest]
     * would be omitted, and the column default would win — which is the
     * root cause of the "0 online" bug we hit during room joins.
     */
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

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
            defaultSerializer = KotlinXSerializer(json)
            install(Postgrest)
            install(Realtime)
        }
    }

    /** Returns the realtime plugin handle (not used directly today, but
     *  threaded through so callers don't need to import the extension). */
    val realtime get() = client.realtime
}
