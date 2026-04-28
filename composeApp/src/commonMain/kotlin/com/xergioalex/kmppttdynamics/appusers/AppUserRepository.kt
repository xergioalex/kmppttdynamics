package com.xergioalex.kmppttdynamics.appusers

import com.xergioalex.kmppttdynamics.domain.AppUser
import com.xergioalex.kmppttdynamics.domain.AppUserDraft
import com.xergioalex.kmppttdynamics.supabase.uniqueRealtimeTopic
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlin.time.Clock
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/**
 * Server-backed `app_users` storage: the install-scoped profile
 * (display name + avatar) shared across every meetup.
 *
 * The `avatar_id` column is unique at the database level, so the
 * picker on every device sees a consistent "claimed" set in real time.
 */
class AppUserRepository(private val supabase: SupabaseClient) {

    /**
     * Inserts or updates the row for [draft]'s `client_id`. The
     * underlying upsert is keyed on the primary key (`client_id`),
     * which means a re-pick of a different avatar is a single
     * round-trip and atomic with respect to the unique index.
     */
    suspend fun upsert(draft: AppUserDraft): AppUser =
        supabase.from(TABLE)
            .upsert(draft) {
                onConflict = "client_id"
                select()
            }
            .decodeSingle()

    suspend fun findByClientId(clientId: String): AppUser? =
        supabase.from(TABLE)
            .select { filter { eq("client_id", clientId) } }
            .decodeSingleOrNull<AppUser>()

    /** Snapshot of every user, used to render the avatar picker grid. */
    suspend fun listAll(): List<AppUser> =
        supabase.from(TABLE)
            .select()
            .decodeList()

    /**
     * Realtime feed of the full user set. Re-fetches on every change.
     * The picker collects this and renders any avatar that is taken
     * (by anyone other than the current device) as locked.
     *
     * Each invocation builds its own channel with a randomised topic
     * suffix. Without this, multiple call sites (Onboarding, Room) that
     * subscribe to the same name share the underlying `RealtimeChannel`
     * inside supabase-kt, and the first one whose collector ends will
     * `unsubscribe()` it for everyone — leaving the picker silent on
     * subsequent edits.
     */
    fun observeAll(): Flow<List<AppUser>> = flow {
        val channel = supabase.channel(uniqueRealtimeTopic("app_users"))
        val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = TABLE
        }
        channel.subscribe()
        try {
            emit(listAll())
            // Catch-up fetch: the websocket subscription confirmation
            // can arrive AFTER our first listAll() call, and any UPDATE
            // in that window (e.g. the other device picking a fresh
            // avatar right after we open the picker) would otherwise be
            // missed.
            kotlinx.coroutines.delay(750)
            emit(listAll())
            changes.collect { emit(listAll()) }
        } finally {
            withContext(NonCancellable) { channel.unsubscribe() }
        }
    }

    /**
     * Updates [clientId]'s display name + avatar. Bumps `updated_at`
     * so other devices can prefer the freshest snapshot if we ever
     * add conflict resolution beyond the unique index.
     */
    suspend fun update(clientId: String, displayName: String, avatarId: Int): AppUser =
        supabase.from(TABLE)
            .update(
                mapOf(
                    "display_name" to displayName.trim(),
                    "avatar_id" to avatarId,
                    "updated_at" to Clock.System.now().toString(),
                ),
            ) {
                select()
                filter { eq("client_id", clientId) }
            }
            .decodeSingle()

    private companion object {
        const val TABLE = "app_users"
    }
}
