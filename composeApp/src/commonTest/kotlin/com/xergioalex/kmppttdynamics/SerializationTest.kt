package com.xergioalex.kmppttdynamics

import com.xergioalex.kmppttdynamics.domain.JoinRequest
import com.xergioalex.kmppttdynamics.domain.ParticipantRole
import com.xergioalex.kmppttdynamics.domain.PollDraft
import com.xergioalex.kmppttdynamics.domain.PollStatus
import com.xergioalex.kmppttdynamics.domain.RaffleDraft
import com.xergioalex.kmppttdynamics.domain.RaffleStatus
import com.xergioalex.kmppttdynamics.trivia.TriviaChoiceDraft
import com.xergioalex.kmppttdynamics.trivia.TriviaEntryDraft
import com.xergioalex.kmppttdynamics.trivia.TriviaQuestionDraft
import com.xergioalex.kmppttdynamics.trivia.TriviaQuizDraft
import com.xergioalex.kmppttdynamics.trivia.TriviaStatus
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
    fun triviaQuizDraftSendsStatusDraftAndDefaultSeconds() {
        // The trivia tab routes off `quiz.status`; without
        // @EncodeDefault on the field the row would land with whatever
        // the server defaults to (also 'draft' today, but a column-
        // default change would silently break the client routing).
        val payload = json.encodeToString(
            TriviaQuizDraft.serializer(),
            TriviaQuizDraft(
                meetupId = "m1",
                title = "Round 1",
                createdByClientId = "abc123",
            ),
        )
        assertTrue(
            "\"status\":\"draft\"" in payload,
            "expected TriviaQuizDraft to send status=draft, got: $payload",
        )
        assertTrue(
            "\"default_seconds_per_question\":15" in payload,
            "expected TriviaQuizDraft to send default_seconds_per_question=15, got: $payload",
        )
    }

    @Test
    fun triviaQuestionDraftSendsSecondsToAnswer() {
        // Per-Q timer override: 15 is the application-wide default
        // and must always reach the server.
        val payload = json.encodeToString(
            TriviaQuestionDraft.serializer(),
            TriviaQuestionDraft(
                quizId = "q1",
                position = 0,
                prompt = "What is 2 + 2?",
            ),
        )
        assertTrue(
            "\"seconds_to_answer\":15" in payload,
            "expected TriviaQuestionDraft to send seconds_to_answer=15, got: $payload",
        )
    }

    @Test
    fun triviaChoiceDraftSendsIsCorrectFalseByDefault() {
        // Three of the four choices per question default to is_correct
        // = false; without @EncodeDefault we'd end up with rows where
        // the column is silently filled in by the database, which is
        // fine for the OK case but masks a serialization bug if the
        // default ever changes.
        val payload = json.encodeToString(
            TriviaChoiceDraft.serializer(),
            TriviaChoiceDraft(
                questionId = "q1",
                position = 1,
                label = "Paris",
            ),
        )
        assertTrue(
            "\"is_correct\":false" in payload,
            "expected TriviaChoiceDraft to send is_correct=false, got: $payload",
        )
    }

    @Test
    fun triviaEntryDraftRoundTripsAllFields() {
        // No @EncodeDefault on this draft because every field is
        // required (no defaults to drop). Roundtrip ensures the wire
        // format matches the column names.
        val payload = json.encodeToString(
            TriviaEntryDraft.serializer(),
            TriviaEntryDraft(
                quizId = "q1",
                participantId = "p1",
                clientId = "abc123",
            ),
        )
        assertTrue("\"quiz_id\":\"q1\"" in payload, payload)
        assertTrue("\"participant_id\":\"p1\"" in payload, payload)
        assertTrue("\"client_id\":\"abc123\"" in payload, payload)
    }

    @Test
    fun triviaStatusEnumSerializesToLowercaseKebab() {
        // The status column uses lowercase strings ('in_progress', not
        // 'IN_PROGRESS'); the @SerialName overrides on the enum guard
        // against a Kotlin renaming silently breaking the wire format.
        for (status in TriviaStatus.entries) {
            val payload = Json.encodeToString(TriviaStatus.serializer(), status)
            val expected = when (status) {
                TriviaStatus.DRAFT -> "\"draft\""
                TriviaStatus.LOBBY -> "\"lobby\""
                TriviaStatus.IN_PROGRESS -> "\"in_progress\""
                TriviaStatus.CALCULATING -> "\"calculating\""
                TriviaStatus.FINISHED -> "\"finished\""
            }
            assertTrue(payload == expected, "TriviaStatus.$status serialized as $payload, expected $expected")
        }
    }

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
