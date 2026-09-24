package com.inkfirst.core

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Screens a course request before anything is written.
 *
 * One cheap text call, at course creation, in front of the outline — not per
 * chapter, because chapters descend from an outline that already passed.
 *
 * WHAT THIS IS FOR, corrected 2026-09-24. This was first written down as
 * "not mainly protection" — the reasoning being that the model writing the
 * chapters refuses the genuinely harmful anyway, so all this bought was a
 * better failure mode. THAT WAS WRONG, and Aliaksei disproved it the same
 * day: "how to kill people quickly" came back from this check as `ask`, and
 * walking past it produced an outline and chapters that carried real
 * instructions. The downstream refusal cannot be relied on. For a request
 * like that one, this check is the only thing between the request and the
 * book.
 *
 * It still buys the failure mode as well — a refusal that surfaces here is a
 * sentence at the moment you typed the thing, rather than a broken chapter
 * several chapters and several pictures in — and it still buys "the app
 * declines to write that" as a property of the app rather than of whichever
 * model version happens to be behind the key. But protection is now the
 * first reason, not the third, which is why the verdict boundary in
 * [buildTopicCheckPrompt] is worth as much care as it has.
 *
 * IT FAILS OPEN. A network blip, a malformed reply, a timeout — all of them
 * return [TopicVerdict.ALLOW] and let the book proceed. Nothing about that
 * weakens the real guard, which is downstream and unaffected; what it avoids
 * is a study app that refuses to write about sourdough because the wifi
 * dropped. A gate that blocks on infrastructure failure gets resented and
 * then routed around, which costs more than it ever saved.
 */
/** Sampling temperature for calls that decide something. See [TopicChecker]. */
const val JUDGING_TEMPERATURE: Double = 0.0

class TopicChecker(
    private val client: GeminiClient,
    private val model: String = DEFAULT_GEMINI_MODEL,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun check(inputs: TopicCheckInputs, apiKey: String): TopicCheckResult {
        if (inputs.topic.isBlank() || apiKey.isBlank()) return ALLOWED

        val raw = try {
            client.generateJson(
                model = model,
                systemInstruction = TOPIC_CHECK_SYSTEM,
                userPrompt = buildTopicCheckPrompt(inputs),
                responseSchema = TOPIC_CHECK_SCHEMA,
                apiKey = apiKey,
                // Pinned, because this JUDGES rather than writes. At the
                // model's default temperature the same request came back
                // refused on one run and allowed on the next — which is not a
                // gate, it is a coin toss with a good prompt attached.
                temperature = JUDGING_TEMPERATURE,
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Cancellation is the reader tapping Cancel, not a check failure,
            // and swallowing it would leave the coroutine running.
            throw e
        } catch (e: Exception) {
            System.err.println("topic check failed, allowing: ${e::class.simpleName}: ${e.message}")
            return ALLOWED
        }

        val reply = try {
            json.decodeFromString(TopicCheckReply.serializer(), raw)
        } catch (e: SerializationException) {
            System.err.println("topic check reply was malformed, allowing: ${e.message}")
            return ALLOWED
        }

        return settle(TopicCheckResult(verdictOf(reply.verdict), reply.reason.trim()))
    }

    private companion object {
        val ALLOWED = TopicCheckResult(TopicVerdict.ALLOW, "")
    }
}

/**
 * Reads the model's word.
 *
 * Anything unrecognised is [TopicVerdict.ALLOW] — the same fail-open rule as
 * a dropped call, and for the same reason. The schema constrains this field
 * to three values, so a fourth means something went wrong upstream rather
 * than that the request was bad.
 *
 * A REFUSE with no sentence is downgraded to ASK. A refusal the reader
 * cannot read is a dead end with no way past it and nothing to act on, which
 * is the worst outcome this design has; an ask with an empty reason at least
 * carries its own escape hatch.
 */
internal fun verdictOf(raw: String): TopicVerdict = when (raw.trim().lowercase()) {
    "refuse" -> TopicVerdict.REFUSE
    "ask" -> TopicVerdict.ASK
    else -> TopicVerdict.ALLOW
}

/**
 * Applied inside [TopicChecker.check] rather than offered to callers, so no
 * screen can forget it and leave a reader staring at a dead end.
 */
internal fun settle(result: TopicCheckResult): TopicCheckResult =
    if (result.verdict == TopicVerdict.REFUSE && result.reason.isBlank()) {
        TopicCheckResult(TopicVerdict.ASK, "")
    } else {
        result
    }
