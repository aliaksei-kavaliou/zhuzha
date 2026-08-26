package com.inkfirst.core

/**
 * The subset of Markdown InkFirst chapters use. See docs/design.md §2 —
 * vanilla Markdown plus the three custom fenced-block kinds (exercise,
 * recap, figure). Kept lean on purpose: no links, block quotes, tables,
 * HR, or nested lists. A `commonmark` swap-in is possible later without
 * touching callers because [Block] stays as the contract.
 */
sealed interface Block {
    data class Heading(val level: Int, val inlines: List<Inline>) : Block
    data class Paragraph(val inlines: List<Inline>) : Block
    data class BulletItem(val inlines: List<Inline>) : Block
    data class OrderedItem(val n: Int, val inlines: List<Inline>) : Block
    /** `kind` matches the fence info word — "exercise", "recap", "figure", or anything the model emits. */
    data class CustomFence(val kind: String, val params: Map<String, String>, val body: String) : Block
}

sealed interface Inline {
    data class Text(val text: String) : Inline
    data class Bold(val text: String) : Inline
    data class Italic(val text: String) : Inline
    data class Code(val text: String) : Inline
}

/**
 * Parses a chapter Markdown blob into a flat list of blocks. Robust to
 * ragged input: unclosed fences swallow to EOF; missing inline close-markers
 * are treated as literal characters.
 */
fun parseMarkdown(input: String): List<Block> {
    val blocks = mutableListOf<Block>()
    val paragraphBuf = mutableListOf<String>()

    fun flushParagraph() {
        if (paragraphBuf.isNotEmpty()) {
            blocks += Block.Paragraph(parseInlines(paragraphBuf.joinToString(" ")))
            paragraphBuf.clear()
        }
    }

    val lines = input.lines()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        when {
            trimmed.isEmpty() -> flushParagraph()

            trimmed.startsWith("```") -> {
                flushParagraph()
                val (kind, params) = parseFenceInfo(trimmed.removePrefix("```").trim())
                val body = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    if (body.isNotEmpty()) body.append('\n')
                    body.append(lines[i])
                    i++
                }
                blocks += Block.CustomFence(kind, params, body.toString())
                // fall through to the i++ below to skip the closing ``` (if any)
            }

            trimmed.startsWith("#") -> {
                flushParagraph()
                val level = trimmed.takeWhile { it == '#' }.length.coerceAtMost(6)
                blocks += Block.Heading(level, parseInlines(trimmed.drop(level).trim()))
            }

            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                flushParagraph()
                blocks += Block.BulletItem(parseInlines(trimmed.drop(2)))
            }

            ORDERED_LIST.matches(trimmed) -> {
                flushParagraph()
                val m = ORDERED_LIST.matchEntire(trimmed)!!
                blocks += Block.OrderedItem(m.groupValues[1].toInt(), parseInlines(m.groupValues[2]))
            }

            else -> paragraphBuf += trimmed
        }
        i++
    }
    flushParagraph()
    return blocks
}

private val ORDERED_LIST = Regex("""^(\d+)\.\s+(.*)$""")

private fun parseFenceInfo(info: String): Pair<String, Map<String, String>> {
    if (info.isEmpty()) return "" to emptyMap()
    val sp = info.indexOfAny(charArrayOf(' ', '\t'))
    if (sp == -1) return info to emptyMap()
    val kind = info.substring(0, sp)
    val paramsPart = info.substring(sp + 1)
    val params = mutableMapOf<String, String>()
    Regex("""(\w+)="([^"]*)"""").findAll(paramsPart).forEach { m ->
        params[m.groupValues[1]] = m.groupValues[2]
    }
    return kind to params
}

private fun parseInlines(text: String): List<Inline> {
    if (text.isEmpty()) return emptyList()
    val out = mutableListOf<Inline>()
    val buf = StringBuilder()
    var i = 0

    fun flushText() {
        if (buf.isNotEmpty()) {
            out += Inline.Text(buf.toString())
            buf.clear()
        }
    }

    while (i < text.length) {
        val c = text[i]
        when {
            c == '*' && i + 1 < text.length && text[i + 1] == '*' -> {
                val end = text.indexOf("**", startIndex = i + 2)
                if (end == -1) {
                    buf.append(c); i++
                } else {
                    flushText()
                    out += Inline.Bold(text.substring(i + 2, end))
                    i = end + 2
                }
            }
            c == '*' -> {
                val end = text.indexOf('*', startIndex = i + 1)
                if (end == -1) {
                    buf.append(c); i++
                } else {
                    flushText()
                    out += Inline.Italic(text.substring(i + 1, end))
                    i = end + 1
                }
            }
            c == '`' -> {
                val end = text.indexOf('`', startIndex = i + 1)
                if (end == -1) {
                    buf.append(c); i++
                } else {
                    flushText()
                    out += Inline.Code(text.substring(i + 1, end))
                    i = end + 1
                }
            }
            else -> {
                buf.append(c); i++
            }
        }
    }
    flushText()
    return out
}
