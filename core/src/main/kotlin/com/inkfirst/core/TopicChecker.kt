package com.inkfirst.core

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Screens a course request before anything is written.
 *
 * One cheap text call, at course creation, in front of the outline — not per
 * chapter, because chapters descend from an outline that already passed.
 *
 * WHAT THIS IS ACTUALLY FOR is worth being honest about, because it is not
 * mainly protection. A request for a nerve-agent synthesis is refused by the
 * model that would write the chapters, whether or not this exists. What this
 * buys is the FAILURE MODE: without it, a refusal surfaces as a broken or
 * evasive chapter several chapters in, after the outline and the pictures
 * have been paid for. With it, the reader gets a sentence at the moment they
 * typed the thing. The second reason is publication — the moment this app is
 * installed by someone who is not Aliaksei, "the app declines to write that"
 * has to be a property of the app rather than of whichever model version is
 * behind the key.
 *
 * IT FAILS OPEN. A network blip, a malformed reply, a timeout — all of them
 * return [TopicVerdict.ALLOW] and let the book proceed. Nothing about that
 * weakens the real guard, which is downstream and unaffected; what it avoids
 * is a study app that refuses to write about sourdough because the wifi
 * dropped. A gate that blocks on infrastructure failure gets resented and
 * then routed around, which costs more than it ever saved.
 */
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
