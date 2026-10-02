package com.inkfirst.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.inkfirst.app.R
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inkfirst.app.data.ApiKeyStore
import com.inkfirst.app.data.geminiErrorLabel
import com.inkfirst.app.data.rememberCourseStore
import com.inkfirst.core.Course
import com.inkfirst.core.KtorGeminiClient
import com.inkfirst.core.PersistedChapter
import com.inkfirst.core.QaGenerator
import com.inkfirst.core.QaTranscript
import com.inkfirst.core.parseInlines
import kotlinx.coroutines.launch
import java.time.LocalDateTime

@Composable
fun ChatPanel(
    course: Course,
    chapter: PersistedChapter,
    chapterMarkdown: String,
    initialSelection: String?,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val store = rememberCourseStore()
    val apiKeyStore = remember { ApiKeyStore(context) }
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    var historyRaw by remember(chapter.slug) { mutableStateOf(store.readQa(course.slug, chapter.slug)) }
    var input by remember(chapter.slug) { mutableStateOf("") }
    var pending by remember(chapter.slug) { mutableStateOf(false) }
    var error by remember(chapter.slug) { mutableStateOf<String?>(null) }
    val selection = initialSelection?.trim()?.takeIf { it.isNotEmpty() }

    LaunchedEffect(chapter.slug) {
        historyRaw = store.readQa(course.slug, chapter.slug)
    }

    val feed = remember(historyRaw, course.annotations, chapter.slug, selection) {
        QaTranscript.feed(
            qaMarkdown = historyRaw,
            annotations = course.annotations,
            chapterSlug = chapter.slug,
            pendingSelection = selection,
            // A timestamp, not a date: the passage just marked has to sort
            // AFTER everything recorded earlier today, and a bare date ties
            // with all of them.
            today = LocalDateTime.now().withNano(0).toString(),
        )
    }

    /**
     * The feed is laid out bottom-up, so the newest turn is where the list
     * already rests and opening the panel scrolls by exactly nothing.
     *
     * The first version scrolled to the bottom after layout instead. It landed
     * in the right place, but the move itself was visible — text crawling up
     * out of the dither, which Aliaksei described as appearing "from the
     * sand". On e-ink a scroll is not a cheap way to reach a position; not
     * needing to move is.
     */
    val listState = rememberLazyListState()

    /**
     * The feed moves a screenful at a time, and never under a dragging finger.
     *
     * Dragging is the thing e-ink cannot do: every intermediate frame is a
     * partial panel update and the result crawls. Boox's own settings app does
     * the same, so it is the display, not us. The reading view answered this by
     * paginating; this is that answer in a smaller place. One viewport per
     * press, with a tenth of a screen of overlap so the eye has a line to land
     * on.
     */
    var viewportPx by remember { mutableStateOf(0) }
    /**
     * Move one screenful toward older or newer turns.
     *
     * The list is reversed, so scrolling FORWARD (a positive delta) walks
     * toward higher indices, which are the older turns rendered above. Naming
     * the axis rather than passing a bare sign is deliberate: the first
     * version took a direction int, and the arrows that called it were wired
     * backwards precisely because "+1" said nothing about which way that was.
     */
    val showOlder: () -> Unit = {
        scope.launch { listState.scrollBy((viewportPx * 0.9f).coerceAtLeast(1f)) }
    }
    val showNewer: () -> Unit = {
        scope.launch { listState.scrollBy(-(viewportPx * 0.9f).coerceAtLeast(1f)) }
    }

    val apiKey = apiKeyStore.getApiKey().orEmpty()
    val model = apiKeyStore.getModelName()

    // The Boox side buttons, mapped as they are in the reader: up goes back
    // through the transcript, down goes forward. Consumed here (returning
    // true) so they neither reach the reader underneath and turn book pages
    // behind the panel, nor raise Android's volume popup.
    val keyFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { keyFocus.requestFocus() }
    // And claimed at the Activity's door as well, which is what actually
    // stops the volume slider: a Compose key handler only fires while its
    // subtree holds focus, and this panel loses focus to its own text field
    // the moment the reader starts typing. Registered last, so it outranks
    // the reader underneath — see [PageKeys].
    val movePanel = rememberUpdatedState<(Boolean) -> Unit> { forward ->
        if (forward) showNewer() else showOlder()
    }
    DisposableEffect(Unit) {
        val release = PageKeys.register { forward -> movePanel.value(forward) }
        onDispose(release)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper)
            .focusRequester(keyFocus)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.VolumeUp, Key.PageUp, Key.DirectionUp -> { showOlder(); true }
                    Key.VolumeDown, Key.PageDown, Key.DirectionDown -> { showNewer(); true }
                    else -> false
                }
            }
            .pointerInput(Unit) {
                // Tap outside inputs dismisses the IME so the Ask button stays
                // reachable — but clearFocus() unfocuses EVERYTHING, this Box
                // included, and a Compose key handler only hears keys while
                // something in its subtree has focus. Without the re-request
                // the side buttons stopped being consumed after the first tap
                // and Android's volume popup came up instead.
                detectTapGestures {
                    focusManager.clearFocus()
                    keyFocus.requestFocus()
                }
            },
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
            ) {
                TextButton(onClick = onClose) { Text("← " + stringResource(R.string.close), color = Ink) }
                Spacer(Modifier.weight(1f))
                // Says the gesture exists, and only while there is somewhere
                // to go. Not a control — the feed is moved by swiping it,
                // which is the app's own idiom (the reader turns pages on a
                // horizontal drag and changes chapter on a vertical one).
                if (listState.canScrollForward || listState.canScrollBackward) {
                    // Words, not arrows. An arrow has to be guessed at — does
                    // it move the viewport or the content — and that guess is
                    // exactly what went wrong with the first pair. "Older" and
                    // "newer" name the axis and cannot be read backwards.
                    TextButton(onClick = showOlder) {
                        Text(stringResource(R.string.older), color = Ink)
                    }
                    TextButton(onClick = showNewer) {
                        Text(stringResource(R.string.newer), color = Ink)
                    }
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = "Ch ${chapter.n} · Q&A",
                    style = MaterialTheme.typography.bodySmall.copy(color = Muted),
                )
            }
            HorizontalDivider(color = Hairline)

            // The feed, flattened to rows so it can be laid out bottom-up.
            // Built oldest-first here and handed to the list reversed, which
            // is what puts the newest turn at the resting position.
            val rows = buildList {
                if (feed.isEmpty()) {
                    add(ChatRow.Note("Type a question about this chapter and tap Ask."))
                }
                feed.forEachIndexed { i, turn ->
                    if (i > 0) add(ChatRow.Rule)
                    when (turn) {
                        is QaTranscript.Entry.Marked -> add(ChatRow.Passage(turn.passage))
                        is QaTranscript.Entry.Asked -> {
                            turn.passage?.let { add(ChatRow.Passage(it)) }
                            add(ChatRow.Question(turn.question))
                            if (turn.answer.isNotBlank()) add(ChatRow.Answer(turn.answer))
                        }
                    }
                }
                // Only under a passage with nothing after it — the one just
                // marked. Says what to do next without labelling every older
                // unasked passage as unfinished business.
                if (feed.lastOrNull() is QaTranscript.Entry.Marked && !pending) {
                    add(
                        ChatRow.Note(
                            "Ask about this passage below — or leave it, and the book "
                                + "will note you stopped here.",
                        ),
                    )
                }
                if (pending) add(ChatRow.Thinking)
                error?.let { add(ChatRow.Problem(it)) }
            }

            // A new turn arrives at index 0 of the reversed list, which is
            // exactly where the list is already sitting — so this is a no-op
            // in the common case and only does work if the reader had paged
            // up to read something older.
            LaunchedEffect(rows.size) { listState.scrollToItem(0) }

            LazyColumn(
                state = listState,
                // Bottom-up. Index 0 is the newest row and renders last.
                reverseLayout = true,
                // The finger does not drag this; the header arrows move it.
                userScrollEnabled = false,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onSizeChanged { viewportPx = it.height }
                    .pointerInput(Unit) {
                        // Swipe as a TRIGGER, not a drag. Nothing follows the
                        // finger — the feed jumps one screenful when the
                        // contact ends — so the panel repaints once instead of
                        // smearing through every intermediate position, which
                        // is the whole reason this app refuses scrolling.
                        //
                        // Direction is direct manipulation, the way paper
                        // works: push the content DOWN and what was above it
                        // (older) comes into view; push UP and you travel
                        // toward the newest. Same threshold as the reader's
                        // vertical drag, since accidental vertical movement is
                        // commoner than horizontal.
                        val thresholdPx = 80.dp.toPx()
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var totalDy = 0f
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (!change.pressed) {
                                    // A long-press that became a text
                                    // selection is consumed by the
                                    // SelectionContainer and is not a swipe.
                                    if (!change.isConsumed) {
                                        if (totalDy >= thresholdPx) showOlder()
                                        else if (totalDy <= -thresholdPx) showNewer()
                                    }
                                    break
                                }
                                totalDy += change.positionChange().y
                            }
                        }
                    },
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(rows.size) { i ->
                    when (val row = rows[rows.size - 1 - i]) {
                        is ChatRow.Passage -> PassageTurn(row.text)
                        is ChatRow.Question -> QuestionTurn(row.text)
                        is ChatRow.Answer -> AnswerTurn(row.text)
                        is ChatRow.Note -> Text(
                            text = row.text,
                            fontFamily = FontFamily.Serif,
                            fontStyle = FontStyle.Italic,
                            fontSize = 13.sp,
                            color = Muted,
                        )
                        is ChatRow.Problem -> Text(
                            text = row.text,
                            fontFamily = FontFamily.Serif,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.error,
                        )
                        ChatRow.Thinking -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.width(18.dp).height(18.dp).padding(end = 8.dp),
                                color = Ink,
                                strokeWidth = 2.dp,
                            )
                            Text(
                                text = "Thinking…",
                                fontFamily = FontFamily.Serif,
                                fontStyle = FontStyle.Italic,
                                fontSize = 14.sp,
                                color = Muted,
                            )
                        }
                        ChatRow.Rule -> HorizontalDivider(color = Hairline)
                    }
                }
            }

            HorizontalDivider(color = Hairline)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                    placeholder = { Text(stringResource(R.string.ask_a_question)) },
                    enabled = !pending,
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = !pending && input.isNotBlank() && apiKey.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Ink),
                    onClick = {
                        val question = input.trim()
                        focusManager.clearFocus()
                        pending = true
                        error = null
                        scope.launch {
                            try {
                                KtorGeminiClient().use { client ->
                                    val answer = QaGenerator(client, model).answer(
                                        course = course,
                                        n = chapter.n,
                                        chapterMarkdown = chapterMarkdown,
                                        selection = selection,
                                        question = question,
                                        apiKey = apiKey,
                                    )
                                    store.appendQa(
                                        courseSlug = course.slug,
                                        chapterSlug = chapter.slug,
                                        selection = selection,
                                        question = question,
                                        answer = answer,
                                        // The `## ` heading of the entry, and
                                        // the key the feed is ordered by — so
                                        // it carries the time of day. Still
                                        // plain ISO text in a file meant to
                                        // be read by a human in ten years.
                                        created = LocalDateTime.now().withNano(0).toString(),
                                    )
                                    historyRaw = store.readQa(course.slug, chapter.slug)
                                    input = ""
                                    pending = false
                                }
                            } catch (e: Exception) {
                                error = geminiErrorLabel(e)
                                pending = false
                            }
                        }
                    },
                ) { Text(if (apiKey.isBlank()) "Set API key" else "Ask", color = Paper) }
            }
        }
    }
}

/**
 * A passage the reader marked. The left rule is the whole signal — it reads as
 * a quotation at a glance on the panel, where an indent alone does not survive
 * the low contrast.
 */
@Composable
private fun PassageTurn(passage: String) {
    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(Ink),
        )
        Spacer(Modifier.width(10.dp))
        SelectionContainer {
            Text(
                text = passage,
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                color = Ink,
            )
        }
    }
}

/** The question, in the sans face so it reads as the reader's own voice. */
@Composable
private fun QuestionTurn(question: String) {
    SelectionContainer {
        Text(
            text = question,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = Ink,
        )
    }
}

/**
 * The answer, in the book's own serif.
 *
 * Inline Markdown is rendered rather than shown raw: the model returns
 * `**bold**` and backticked code routinely, and the panel used to print the
 * asterisks. Block structure (fences, lists) is deliberately left alone —
 * paragraphs are what answers are made of, and the reader's full block
 * renderer would drag chapter typography into a side panel.
 */
@Composable
private fun AnswerTurn(answer: String) {
    if (answer.isBlank()) return
    SelectionContainer {
        Text(
            text = renderInlines(parseInlines(answer)),
            fontFamily = FontFamily.Serif,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            color = Ink,
        )
    }
}

/** One line of the feed. Flat, so the list can be laid out bottom-up. */
private sealed interface ChatRow {
    data class Passage(val text: String) : ChatRow
    data class Question(val text: String) : ChatRow
    data class Answer(val text: String) : ChatRow
    data class Note(val text: String) : ChatRow
    data class Problem(val text: String) : ChatRow
    data object Thinking : ChatRow
    data object Rule : ChatRow
}
