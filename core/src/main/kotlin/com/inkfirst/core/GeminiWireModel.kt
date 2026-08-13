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

@Serializable
internal data class Part(val text: String)

@Serializable
internal data class GenerationConfig(
    val responseMimeType: String? = null,
    val responseSchema: JsonObject? = null,
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
