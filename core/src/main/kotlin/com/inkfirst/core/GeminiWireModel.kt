package com.inkfirst.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/*
 * Wire types for Gemini's generateContent endpoint. Only the fields we use.
 * Kept internal — nothing outside :core should care about the REST shape.
 */

@Serializable
internal data class GenerateContentRequest(
    val systemInstruction: SystemInstruction? = null,
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
)

@Serializable
internal data class SystemInstruction(val parts: List<Part>)

@Serializable
internal data class Content(val role: String = "model", val parts: List<Part> = emptyList())

/**
 * A Part is a union — text OR inline binary. Requests only ever set [text];
 * image responses come back with [inlineData] instead. Both are nullable so
 * the one shape serves both directions (the client's Json has
 * explicitNulls = false, so the unused half is omitted from the wire).
 */
@Serializable
internal data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null,
)

/** Base64 image payload as returned by image-capable models. */
@Serializable
internal data class InlineData(
    val mimeType: String = "image/png",
    val data: String = "",
)

@Serializable
internal data class GenerationConfig(
    /**
     * Sampling temperature. Left null — the model's own default, around 1.0 —
     * for everything that WRITES, where variety is the point. Pinned to 0 for
     * anything that JUDGES: a screening verdict that changes between two
     * identical runs is not a gate, and this field being absent is why
     * "how to rubber the bank" was refused on one run and allowed on another.
     */
    val temperature: Double? = null,
    val responseMimeType: String? = null,
    val responseSchema: JsonObject? = null,
    /** ["TEXT", "IMAGE"] switches the model into picture-returning mode. */
    val responseModalities: List<String>? = null,
    val imageConfig: ImageConfig? = null,
)

/**
 * Picture shape and size. Note this is `generationConfig.imageConfig`, NOT
 * the sibling `responseFormat.image` — the latter exists but wants protobuf
 * enum names (ASPECT_RATIO_FOUR_BY_THREE, IMAGE_SIZE_ONE_K) and 400s on the
 * human-readable forms. imageConfig takes them as plain strings.
 *
 * [aspectRatio] is "4:3", "16:9", "1:1"…; [imageSize] is "512", "1K", "2K" or "4K".
 */
@Serializable
internal data class ImageConfig(
    val aspectRatio: String? = null,
    val imageSize: String? = null,
)

@Serializable
internal data class GenerateContentResponse(
    val candidates: List<Candidate> = emptyList(),
)

@Serializable
internal data class Candidate(
    val content: Content? = null,
    val finishReason: String? = null,
)
