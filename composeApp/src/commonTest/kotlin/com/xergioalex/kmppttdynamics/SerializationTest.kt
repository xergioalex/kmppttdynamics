package com.xergioalex.kmppttdynamics

import com.xergioalex.kmppttdynamics.domain.JoinRequest
import com.xergioalex.kmppttdynamics.domain.ParticipantRole
import com.xergioalex.kmppttdynamics.domain.PollDraft
import com.xergioalex.kmppttdynamics.domain.PollStatus
import com.xergioalex.kmppttdynamics.domain.RaffleDraft
import com.xergioalex.kmppttdynamics.domain.RaffleStatus
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/**
 * These tests assert the JSON shape that goes over the wire to Supabase.
 * They guard against regressions of the kotlinx.serialization
 * `encodeDefaults = false` gotcha that caused our "0 online" + "polls
 * always Draft" + "raffles always Draft" bugs:
 *
 *   - kotlinx.serialization omits parameters that have a default value
 *     from the JSON output unless `encodeDefaults = true` is set on the
 *     `Json` instance OR the field carries `@EncodeDefault`.
 *   - The Supabase client (configured in `SupabaseClientProvider`) uses
 *     `encodeDefaults = true`, so these tests must mirror that config to
 *     reflect what the live app actually sends.
 */
class SerializationTest {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun joinRequestSendsIsOnlineTrueAndClientId() {
        val payload = json.encodeToString(
            JoinRequest.serializer(),
            JoinRequest(
                meetupId = "m1",
                displayName = "Alice",
                role = ParticipantRole.PARTICIPANT,
                clientId = "abc123",
            ),
        )
        assertTrue(
            "\"is_online\":true" in payload,
            "expected JoinRequest payload to include is_online=true, got: $payload",
        )
        assertTrue(
            "\"client_id\":\"abc123\"" in payload,
            "expected JoinRequest payload to include client_id, got: $payload",
        )
    }

    @Test
    fun pollDraftSendsStatusOpen() {
        val payload = json.encodeToString(
            PollDraft.serializer(),
            PollDraft(
                meetupId = "m1",
                createdBy = "p1",
                question = "?",
                status = PollStatus.OPEN,
                isAnonymous = true,
            ),
        )
        assertTrue(
            "\"status\":\"open\"" in payload,
            "expected PollDraft payload to include status=open, got: $payload",
        )
        assertTrue(
            "\"is_anonymous\":true" in payload,
            "expected PollDraft payload to include is_anonymous=true, got: $payload",
        )
    }

    @Test
    fun raffleDraftSendsStatusOpen() {
        val payload = json.encodeToString(
            RaffleDraft.serializer(),
            RaffleDraft(
                meetupId = "m1",
                createdBy = "p1",
                title = "Prize",
                status = RaffleStatus.OPEN,
            ),
        )
        assertTrue(
            "\"status\":\"open\"" in payload,
            "expected RaffleDraft payload to include status=open, got: $payload",
        )
    }

    @Test
    fun defaultJsonStillEncodesAnnotatedDefaults() {
        // Even with stock kotlinx.serialization (no encodeDefaults flag),
        // the @EncodeDefault(ALWAYS) annotation guarantees these fields
        // ride along in the wire payload — that's the second line of
        // defense behind the JSON-instance config.
        val join = Json.encodeToString(
            JoinRequest.serializer(),
            JoinRequest(
                meetupId = "m1",
                displayName = "Alice",
                role = ParticipantRole.PARTICIPANT,
                clientId = "abc123",
            ),
        )
        assertTrue("\"is_online\":true" in join, "expected default Json to still send is_online, got $join")

        val poll = Json.encodeToString(
            PollDraft.serializer(),
            PollDraft(meetupId = "m1", createdBy = "p1", question = "?"),
        )
        assertTrue("\"status\":\"open\"" in poll, "expected default Json to still send status=open, got $poll")
        assertTrue("\"is_anonymous\":true" in poll, "expected default Json to still send is_anonymous, got $poll")

        val raffle = Json.encodeToString(
            RaffleDraft.serializer(),
            RaffleDraft(meetupId = "m1", createdBy = "p1", title = "Prize"),
        )
        assertTrue("\"status\":\"open\"" in raffle, "expected default Json to still send raffle status=open, got $raffle")
    }

    /**
     * Mirrors the exact path supabase-kt's `KotlinXSerializer.encode`
     * uses: `json.encodeToString(json.serializersModule.serializer(type), value)`
     * with a reflective `KType`, plus the wrapping in `List<T>` that
     * `PostgrestQueryBuilder.insert` does on a single value.
     */
    @Test
    fun pollDraftListGoesThroughEncodeWithStatus() {
        val list = listOf(
            PollDraft(
                meetupId = "m1",
                createdBy = "p1",
                question = "?",
                status = PollStatus.OPEN,
                isAnonymous = true,
            ),
        )
        val type = typeOf<List<PollDraft>>()
        val serializer = json.serializersModule.serializer(type)
        val payload = json.encodeToString(serializer, list)
        assertTrue(
            "\"status\":\"open\"" in payload,
            "expected list-wrapped PollDraft payload to include status=open, got: $payload",
        )
    }
}
