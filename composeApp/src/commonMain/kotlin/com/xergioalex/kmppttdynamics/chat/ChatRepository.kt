package com.xergioalex.kmppttdynamics.chat

import com.xergioalex.kmppttdynamics.domain.ChatDraft
import com.xergioalex.kmppttdynamics.domain.ChatMessage
import com.xergioalex.kmppttdynamics.domain.ChatStatus
import com.xergioalex.kmppttdynamics.domain.ChatType
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

class ChatRepository(private val supabase: SupabaseClient) {

    suspend fun send(meetupId: String, participantId: String, message: String): ChatMessage =
        supabase.from(TABLE)
            .insert(ChatDraft(meetupId, participantId, message.trim())) { select() }
            .decodeSingle()

    /** Host-only path. Announcements render distinct in the chat feed. */
    suspend fun sendAnnouncement(meetupId: String, hostParticipantId: String, message: String): ChatMessage =
        supabase.from(TABLE)
            .insert(
                ChatDraft(
                    meetupId = meetupId,
                    participantId = hostParticipantId,
                    message = message.trim(),
                    type = ChatType.ANNOUNCEMENT,
                ),
            ) { select() }
            .decodeSingle()

    suspend fun hide(messageId: String) {
        supabase.from(TABLE)
            .update(mapOf("status" to ChatStatus.HIDDEN.name.lowercase())) {
                filter { eq("id", messageId) }
            }
    }

    suspend fun list(meetupId: String, limit: Long = 200): List<ChatMessage> =
        supabase.from(TABLE)
            .select {
                filter {
                    eq("meetup_id", meetupId)
                    neq("status", ChatStatus.DELETED.name.lowercase())
                }
                order("created_at", Order.ASCENDING)
                limit(limit)
            }
            .decodeList()

    fun observe(meetupId: String): Flow<List<ChatMessage>> = flow {
        val channel = supabase.channel("chat_$meetupId")
        val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = TABLE
            filter("meetup_id", FilterOperator.EQ, meetupId)
        }
        channel.subscribe()
        try {
            emit(list(meetupId))
            changes.collect { emit(list(meetupId)) }
        } finally {
            withContext(NonCancellable) { channel.unsubscribe() }
        }
    }

    private companion object {
        const val TABLE = "chat_messages"
    }
}
