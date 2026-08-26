package com.inkfirst.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inkfirst.app.data.rememberCourseStore
import com.inkfirst.core.Block
import com.inkfirst.core.Course
import com.inkfirst.core.Inline
import com.inkfirst.core.parseMarkdown

// E-ink palette: near-black ink on off-white paper. Pure white/black looks harsh; slight warm tint.
private val InkColor = Color(0xFF111111)
private val PaperColor = Color(0xFFF8F5EE)
private val MutedColor = Color(0xFF555555)
private val BorderColor = Color(0xFF888888)

@Composable
fun ReaderScreen(
    courseSlug: String,
    chapterSlug: String,
    onBack: () -> Unit,
) {
    val store = rememberCourseStore()
    var course by remember { mutableStateOf<Course?>(null) }
    var markdown by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(courseSlug, chapterSlug) {
        course = store.load(courseSlug)
        markdown = store.readChapter(courseSlug, chapterSlug)
    }

    val blocks = remember(markdown) { markdown?.let { parseMarkdown(it) } ?: emptyList() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PaperColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("← Back", color = InkColor) }
                Spacer(Modifier.weight(1f))
                val chapterN = course?.chapters?.firstOrNull { it.slug == chapterSlug }?.n
                if (chapterN != null && course != null) {
                    Text(
                        text = "Chapter $chapterN of ${course!!.chapters.size}",
                        style = MaterialTheme.typography.bodySmall.copy(color = MutedColor),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            if (markdown == null && course != null) {
                Text("Chapter not on disk yet.", color = MaterialTheme.colorScheme.error)
            } else {
                RenderBlocks(blocks)
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun RenderBlocks(blocks: List<Block>) {
    // Reveal state for exercise blocks, keyed by index in the block list.
    val revealed = remember { mutableStateMapOf<Int, Boolean>() }

    blocks.forEachIndexed { idx, block ->
        when (block) {
            is Block.Heading -> HeadingBlock(block)
            is Block.Paragraph -> ParagraphBlock(block.inlines)
            is Block.BulletItem -> ListItemBlock(prefix = "• ", inlines = block.inlines)
            is Block.OrderedItem -> ListItemBlock(prefix = "${block.n}. ", inlines = block.inlines)
            is Block.CustomFence -> when (block.kind) {
                "exercise" -> ExerciseBlock(block, revealed = revealed[idx] == true, onReveal = { revealed[idx] = true })
                "recap" -> RecapBlock(block)
                "figure" -> FigureBlock(block)
                else -> UnknownFenceBlock(block)
            }
        }
    }
}

@Composable
private fun HeadingBlock(h: Block.Heading) {
    val (size, top, bottom) = when (h.level) {
        1 -> Triple(30.sp, 24.dp, 12.dp)
        2 -> Triple(24.sp, 20.dp, 10.dp)
        3 -> Triple(20.sp, 16.dp, 8.dp)
        else -> Triple(18.sp, 14.dp, 6.dp)
    }
    Spacer(Modifier.height(top))
    Text(
        text = renderInlines(h.inlines),
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = size,
        color = InkColor,
    )
    Spacer(Modifier.height(bottom))
}

@Composable
private fun ParagraphBlock(inlines: List<Inline>) {
    Text(
        text = renderInlines(inlines),
        fontFamily = FontFamily.Serif,
        fontSize = 18.sp,
        lineHeight = 30.sp,
        color = InkColor,
        modifier = Modifier.padding(bottom = 12.dp),
    )
}

@Composable
private fun ListItemBlock(prefix: String, inlines: List<Inline>) {
    Row(modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)) {
        Text(
            text = prefix,
            fontFamily = FontFamily.Serif,
            fontSize = 18.sp,
            lineHeight = 30.sp,
            color = InkColor,
        )
        Text(
            text = renderInlines(inlines),
            fontFamily = FontFamily.Serif,
            fontSize = 18.sp,
            lineHeight = 30.sp,
            color = InkColor,
        )
    }
}

@Composable
private fun ExerciseBlock(f: Block.CustomFence, revealed: Boolean, onReveal: () -> Unit) {
    val (question, answer) = splitExerciseBody(f.body)
    BorderedBlock(label = "TRY THIS") {
        Text(
            text = question,
            fontFamily = FontFamily.Serif,
            fontSize = 17.sp,
            lineHeight = 28.sp,
            color = InkColor,
        )
        Spacer(Modifier.height(8.dp))
        if (revealed || answer.isEmpty()) {
            if (answer.isNotEmpty()) {
                Text(
                    text = "Answer: $answer",
                    fontFamily = FontFamily.Serif,
                    fontSize = 16.sp,
                    lineHeight = 26.sp,
                    fontStyle = FontStyle.Italic,
                    color = InkColor,
                )
            }
        } else {
            TextButton(onClick = onReveal) { Text("Show answer", color = InkColor) }
        }
    }
}

@Composable
private fun RecapBlock(f: Block.CustomFence) {
    BorderedBlock(label = "RECAP") {
        Text(
            text = f.body.trim(),
            fontFamily = FontFamily.Serif,
            fontSize = 17.sp,
            lineHeight = 28.sp,
            color = InkColor,
        )
    }
}

@Composable
private fun FigureBlock(f: Block.CustomFence) {
    val alt = f.params["alt"] ?: f.body.trim().ifBlank { "(figure)" }
    BorderedBlock(label = "FIGURE") {
        Text(
            text = alt,
            fontFamily = FontFamily.Serif,
            fontSize = 16.sp,
            lineHeight = 26.sp,
            fontStyle = FontStyle.Italic,
            color = MutedColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        )
    }
}

@Composable
private fun UnknownFenceBlock(f: Block.CustomFence) {
    BorderedBlock(label = f.kind.uppercase().ifBlank { "CODE" }) {
        Text(
            text = f.body,
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            color = InkColor,
        )
    }
}

@Composable
private fun BorderedBlock(label: String, content: @Composable () -> Unit) {
    Spacer(Modifier.height(4.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .border(1.dp, BorderColor, RoundedCornerShape(4.dp))
            .padding(12.dp),
    ) {
        Text(
            text = label,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = MutedColor,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        content()
    }
    Spacer(Modifier.height(4.dp))
}

/** Splits an exercise body on the first line starting with "answer:" (case-insensitive). */
private fun splitExerciseBody(body: String): Pair<String, String> {
    val lines = body.lines()
    val answerIdx = lines.indexOfFirst { it.trim().lowercase().startsWith("answer:") }
    if (answerIdx == -1) return body.trim() to ""
    val question = lines.take(answerIdx).joinToString("\n").trim()
    val answerLine = lines[answerIdx].substringAfter(":").trim()
    val extra = lines.drop(answerIdx + 1).joinToString("\n").trim()
    val answer = if (extra.isEmpty()) answerLine else "$answerLine\n$extra"
    return question to answer
}

private fun renderInlines(inlines: List<Inline>): AnnotatedString = buildAnnotatedString {
    inlines.forEach { i ->
        when (i) {
            is Inline.Text -> append(i.text)
            is Inline.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(i.text) }
            is Inline.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(i.text) }
            is Inline.Code -> withStyle(
                SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x14000000)),
            ) { append(i.text) }
        }
    }
}
