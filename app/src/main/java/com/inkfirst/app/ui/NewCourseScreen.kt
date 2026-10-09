package com.inkfirst.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.inkfirst.app.data.ApiKeyStore
import com.inkfirst.app.data.geminiErrorLabel
import com.inkfirst.app.data.rememberCourseStore
import com.inkfirst.core.CourseOutline
import com.inkfirst.core.DEFAULT_MEASUREMENT_SYSTEM
import com.inkfirst.core.DEFAULT_FIGURE_POLICY
import com.inkfirst.core.FIGURE_POLICIES
import com.inkfirst.core.FigurePolicy
import com.inkfirst.core.bookPictureEstimate
import com.inkfirst.core.figurePolicyFor
import com.inkfirst.core.MEASUREMENT_SYSTEMS
import com.inkfirst.core.KtorGeminiClient
import com.inkfirst.core.OutlineGenerator
import com.inkfirst.core.OutlineParseException
import com.inkfirst.core.StyleBrief
import com.inkfirst.core.StyleBriefException
import com.inkfirst.core.StyleBriefGenerator
import com.inkfirst.core.StylePreset
import com.inkfirst.core.StylePresets
import com.inkfirst.core.TopicCheckInputs
import com.inkfirst.core.TopicChecker
import com.inkfirst.core.TopicVerdict
import com.inkfirst.core.courseSlug
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDate

private sealed interface OutlineUiState {
    data object Idle : OutlineUiState

    /**
     * [note] is what the waiting page says it is doing. A custom style adds a
     * call BEFORE the outline, so a single fixed "Writing the outline…" would
     * be a lie for the first stretch of a wait that can run to a minute — and
     * on a panel with no spinner to watch, the sentence is the only evidence
     * the tap landed at all.
     */
    data class Loading(val note: String) : OutlineUiState
    data class Ready(val outline: CourseOutline) : OutlineUiState
    data class Failed(val message: String) : OutlineUiState

    /**
     * The screening check was unsure — see [TopicChecker].
     *
     * Its own state rather than a [Failed] with a button, because the two
     * mean different things to the person reading them: a Failed is "this did
     * not work", and this is "it worked, and it has a question for you". The
     * reader is the one who settles it.
     */
    data class Queried(val reason: String, val pending: CourseOutline? = null) : OutlineUiState
}

/**
 * Creating a course, as three full PAGES rather than one growing form.
 *
 * It used to be one scrolling column: the form, the Generate button, and the
 * result appended underneath. On the Go 7 that button sits at the bottom edge
 * of the viewport, so a finished outline rendered entirely off-screen while
 * the button's label flipped from "Generating…" back to "Generate outline".
 * Nothing on e-ink animates or scrolls by itself, so the page looked
 * untouched and a perfectly good course sat one swipe away, unseen — reported
 * as "new course generate did nothing" on 2026-09-21.
 *
 * Scrolling the result into view would have fixed that instance. Pages remove
 * the whole class of it: a result that has its own page cannot render below
 * the fold. It also matches the rest of the app, which is paginated
 * everywhere else, and it gives "try again" and "edit inputs" somewhere
 * obvious to live. The form's state lives here, in the parent, so returning
 * to it from the review page costs nothing and keeps every field filled.
 */
@Composable
fun NewCourseScreen(
    onBack: () -> Unit,
    onCourseCreated: (String) -> Unit,
) {
    val context = LocalContext.current
    val store = remember { ApiKeyStore(context) }
    val courseStore = rememberCourseStore()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var topic by remember { mutableStateOf("") }
    var dailyMinutes by remember { mutableStateOf("30") }
    var priorKnowledge by remember { mutableStateOf("") }
    var expectations by remember { mutableStateOf("") }
    var style by remember { mutableStateOf(StylePresets.DEFAULT) }
    // What was typed into "Describe the style", and what it was expanded into.
    var styleRequest by remember { mutableStateOf("") }
    var styleBrief by remember { mutableStateOf<StyleBrief?>(null) }
    // The wish [styleBrief] was written from. Holding it is what makes the
    // expansion happen once per WISH rather than once per attempt: "Try again"
    // re-rolls the outline, and re-expanding there would mean approving an
    // outline written in one style while a differently-worded one got stored.
    var briefFor by remember { mutableStateOf("") }
    // The exact request the reader has already been asked about and waved
    // through. Without it, "Write it anyway" would re-run the check, get the
    // same answer, and ask the same question again — a loop with no way out.
    var clearedRequest by remember { mutableStateOf<String?>(null) }
    var language by remember { mutableStateOf(store.getPreferredLanguage()) }
    var units by remember { mutableStateOf(DEFAULT_MEASUREMENT_SYSTEM) }
    var figures by remember { mutableStateOf(DEFAULT_FIGURE_POLICY) }
    var uiState by remember { mutableStateOf<OutlineUiState>(OutlineUiState.Idle) }

    // Held so Cancel can stop a request in flight. The request timeout is
    // three minutes, and a page with no way off it for three minutes is worse
    // than the bug this rewrite fixes.
    var job by remember { mutableStateOf<Job?>(null) }

    val apiKey = store.getApiKey()
    val model = store.getModelName()

    // A plain local function, not a composable — it runs on a tap rather than
    // during drawing. Both the form and the review page call it.
    // What the screening check looks at. Everything the reader typed that
    // could carry a request, style included — a wish is a prompt too.
    fun checkInputs() = TopicCheckInputs(
        topic = topic.trim(),
        priorKnowledge = priorKnowledge.trim(),
        expectations = expectations.trim(),
        styleRequest = if (style.key == StylePresets.CUSTOM_KEY) styleRequest.trim() else "",
    )

    fun generate(skipCheck: Boolean = false) {
        focusManager.clearFocus()
        val minutes = dailyMinutes.toIntOrNull() ?: return
        val key = apiKey ?: return
        val custom = style.key == StylePresets.CUSTOM_KEY
        val wish = styleRequest.trim()
        if (custom && wish.isBlank()) return

        val request = checkInputs()
        // Re-checked whenever the text CHANGES, even after a wave-through:
        // being cleared to write about one thing is not being cleared to
        // write about whatever is typed next.
        val needsCheck = !skipCheck && request.toString() != clearedRequest
        val needsBrief = custom && (styleBrief == null || wish != briefFor)
        uiState = OutlineUiState.Loading(
            when {
                needsCheck -> "Reading the request…"
                needsBrief -> "Working out the style…"
                else -> "Writing the outline…"
            },
        )
        job = scope.launch {
            if (needsCheck) {
                val result = KtorGeminiClient().use { TopicChecker(it, model).check(request, key) }
                when (result.verdict) {
                    TopicVerdict.REFUSE -> {
                        uiState = OutlineUiState.Failed(result.reason)
                        return@launch
                    }
                    TopicVerdict.ASK -> {
                        uiState = OutlineUiState.Queried(result.reason)
                        return@launch
                    }
                    TopicVerdict.ALLOW -> clearedRequest = request.toString()
                }
                uiState = OutlineUiState.Loading(
                    if (needsBrief) "Working out the style…" else "Writing the outline…",
                )
            }

            var brief = styleBrief
            if (needsBrief) {
                brief = try {
                    KtorGeminiClient().use { StyleBriefGenerator(it, model).expand(wish, key) }
                } catch (e: StyleBriefException) {
                    uiState = OutlineUiState.Failed(
                        "Gemini couldn't turn that into a teaching style. Try describing it " +
                            "differently.\n${e.message}",
                    )
                    return@launch
                } catch (e: Exception) {
                    uiState = OutlineUiState.Failed(geminiErrorLabel(e))
                    return@launch
                }
                styleBrief = brief
                briefFor = wish
                uiState = OutlineUiState.Loading("Writing the outline…")
            }

            // The outline is written in the style too — chapter titles are the
            // first thing a fairy-tale book should sound like.
            val effective = if (custom && brief != null) {
                StylePresets.CUSTOM.copy(systemPrompt = brief.prompt)
            } else {
                style
            }

            val outlined = runOutline(
                topic, minutes, priorKnowledge, expectations, effective, language,
                units, model, key,
            )

            // THE SECOND GATE. The outline is the model's own reading of the
            // request, in chapter titles and objectives, and it is far more
            // concrete than the sentence that produced it — which is exactly
            // what the first gate has least of to go on. Screened before it is
            // shown, so a refusal never renders a plan for the thing.
            uiState = if (outlined is OutlineUiState.Ready) {
                val onOutline = KtorGeminiClient().use {
                    TopicChecker(it, model).check(
                        request.copy(outline = describeOutline(outlined.outline)),
                        key,
                    )
                }
                when (onOutline.verdict) {
                    TopicVerdict.REFUSE -> OutlineUiState.Failed(onOutline.reason)
                    TopicVerdict.ASK -> OutlineUiState.Queried(onOutline.reason, outlined.outline)
                    TopicVerdict.ALLOW -> outlined
                }
            } else {
                outlined
            }
        }
    }

    when (val s = uiState) {
        is OutlineUiState.Loading -> GeneratingPage(
            topic = topic,
            note = s.note,
            onCancel = {
                job?.cancel()
                job = null
                uiState = OutlineUiState.Idle
            },
        )

        is OutlineUiState.Ready -> OutlineReviewPage(
            outline = s.outline,
            figures = figurePolicyFor(figures),
            // Guarded rather than passed straight through: switching back to a
            // built-in style after a custom run leaves the brief in state, and
            // a book must never carry a style it is not written in.
            brief = styleBrief.takeIf { style.key == StylePresets.CUSTOM_KEY },
            onApprove = {
                val custom = style.key == StylePresets.CUSTOM_KEY
                val brief = styleBrief.takeIf { custom }
                val course = courseStore.create(
                    outline = s.outline,
                    proposedSlug = courseSlug(s.outline.title),
                    topicPrompt = topic,
                    priorKnowledge = priorKnowledge,
                    expectations = expectations,
                    dailyMinutes = dailyMinutes.toIntOrNull() ?: 30,
                    style = style.key,
                    styleRequest = if (custom) styleRequest.trim() else "",
                    stylePrompt = brief?.prompt.orEmpty(),
                    styleMascot = brief?.mascot.orEmpty(),
                    model = model,
                    language = language.trim().ifBlank { "English" },
                    units = units,
                    figures = figures,
                    created = LocalDate.now().toString(),
                )
                onCourseCreated(course.slug)
            },
            onTryAgain = { generate() },
            onEdit = { uiState = OutlineUiState.Idle },
        )

        is OutlineUiState.Queried -> QueriedPage(
            reason = s.reason,
            onWriteAnyway = {
                clearedRequest = checkInputs().toString()
                // An outline already written is shown rather than paid for
                // again; a query raised before one exists has to generate.
                val held = s.pending
                if (held != null) uiState = OutlineUiState.Ready(held) else generate(skipCheck = true)
            },
            onEdit = { uiState = OutlineUiState.Idle },
        )

        // Idle and Failed are the same page: a failure means "fix something
        // and go again", so it belongs on the form, at the TOP where it
        // cannot be missed, rather than on a dead-end page of its own.
        else -> Column(
            modifier = Modifier
                .fillMaxSize()
                // Edge-to-edge (2026-10-02) made adjustResize inert — see
                // ChatPanel for the full story. Every text field needs this.
                .imePadding()
                .pointerInput(Unit) {
                    // Tap outside any input dismisses the IME so the Generate
                    // button becomes reachable on short viewports.
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                }
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("New course", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onBack) { Text("Back") }
            }

            if (apiKey.isNullOrBlank()) {
                Text(
                    text = "No API key set. Go to Settings first.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            (s as? OutlineUiState.Failed)?.let { failed ->
                Text(
                    text = failed.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            OutlinedTextField(
                value = topic,
                onValueChange = { topic = it },
                label = { Text("Topic (what do you want to learn?)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = dailyMinutes,
                onValueChange = { input -> dailyMinutes = input.filter { it.isDigit() }.take(3) },
                label = { Text("Daily minutes") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = priorKnowledge,
                onValueChange = { priorKnowledge = it },
                label = { Text("Who's reading, and what they already know") },
                placeholder = {
                    Text("optional — \"she's 6, knows her letters but can't blend them yet\"")
                },
                modifier = Modifier.fillMaxWidth().height(120.dp),
            )

            OutlinedTextField(
                value = expectations,
                onValueChange = { expectations = it },
                label = { Text("What they should be able to do afterwards") },
                placeholder = {
                    Text("optional — \"read simple three-letter words on her own\"")
                },
                modifier = Modifier.fillMaxWidth().height(120.dp),
            )

            StylePicker(current = style, onSelected = { style = it })

            if (style.key == StylePresets.CUSTOM_KEY) {
                OutlinedTextField(
                    value = styleRequest,
                    onValueChange = { styleRequest = it },
                    label = { Text("Describe the style") },
                    placeholder = {
                        Text("\"told as a fairy tale\", \"a friendly guide with no jargon and jokes in the margins\"")
                    },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                )
                Text(
                    text = "One sentence is enough — it gets written out into a full " +
                        "instruction before the outline, and you'll see it on the next page. " +
                        "It's stored with the book in course.json, where you can edit it by hand.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            LanguagePicker(
                label = "Language",
                value = language,
                onValueChange = { language = it },
            )

            // Fixed for the life of the book, like the language: a course whose
            // chapter 1 measures in Celsius and whose chapter 6 measures in
            // Fahrenheit is worse than either.
            MeasurementPicker(current = units, onSelected = { units = it })

            FigurePicker(current = figures, onSelected = { figures = it })
            Text(
                text = figurePolicyFor(figures).note,
                style = MaterialTheme.typography.bodySmall,
            )

            Spacer(Modifier.height(8.dp))

            Button(
                enabled = !apiKey.isNullOrBlank() &&
                    topic.isNotBlank() &&
                    language.isNotBlank() &&
                    (dailyMinutes.toIntOrNull() ?: 0) > 0 &&
                    (style.key != StylePresets.CUSTOM_KEY || styleRequest.isNotBlank()),
                onClick = { generate() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Generate outline")
            }
        }
    }
}

/**
 * While the outline is being written. A whole page rather than a line under
 * the button, so there is no doubt that the tap landed.
 *
 * A label, not a spinner: an indeterminate spinner on e-ink is an animation
 * the panel cannot draw, and it says nothing a sentence cannot say better.
 */
@Composable
private fun GeneratingPage(topic: String, note: String, onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(note, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        Text(
            text = topic.trim(),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "This usually takes 10-30 seconds.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(32.dp))
        TextButton(onClick = onCancel) { Text("Cancel") }
    }
}

/**
 * The screening check was unsure, and is asking.
 *
 * A page of its own, like the outline review, because it is a DECISION and
 * the two answers need room to be equally available. It is also why this is
 * an ask rather than a refusal: a check that cannot be wrong in an app
 * somebody owns is a check nobody should ship, and the whole difference
 * between the two verdicts is whether this button exists.
 *
 * Nothing here scolds. The reader is an adult, spending their own API key,
 * on a book only they will read; they are being told what the check noticed
 * and asked to settle it.
 */
@Composable
private fun QueriedPage(
    reason: String,
    onWriteAnyway: () -> Unit,
    onEdit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Text("BEFORE WE START", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = reason.ifBlank {
                    "Something about this request looked unclear, but no explanation came back."
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "If that is not what you meant, change the wording. If it is wrong, " +
                    "carry on — this is your book.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(Modifier.height(16.dp))

        Button(onClick = onWriteAnyway, modifier = Modifier.fillMaxWidth()) {
            Text("Write it anyway")
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onEdit) { Text("Change the wording") }
        }
    }
}

/**
 * The proposed outline, with the decision it asks for.
 *
 * The actions sit OUTSIDE the scrolling area. A twelve-chapter outline will
 * not fit on a 7" panel, so the list has to scroll — but the buttons must
 * never be the thing that scrolled away, which is the entire defect this page
 * was built to remove.
 */
@Composable
private fun OutlineReviewPage(
    outline: CourseOutline,
    figures: FigurePolicy,
    brief: StyleBrief?,
    onApprove: () -> Unit,
    onTryAgain: () -> Unit,
    onEdit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Text("PROPOSED OUTLINE", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            OutlinePreview(outline)

            // The one moment a custom style can be checked against the wish
            // that produced it, and the last page before anything is spent.
            // After this it is twelve chapters of a voice nobody read first.
            if (brief != null) {
                Spacer(Modifier.height(20.dp))
                Text("STYLE", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                Text(brief.prompt, style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Said here because this is the last page before anything is spent,
        // and because a price nobody was shown is the part that stings.
        Text(
            text = "Pictures: ${figures.label.lowercase()}. " +
                bookPictureEstimate(outline.chapters.size, figures),
            style = MaterialTheme.typography.bodySmall,
        )

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = onApprove,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Approve & save")
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onTryAgain) { Text("Try again") }
            TextButton(onClick = onEdit) { Text("Edit inputs") }
        }
    }
}

/**
 * Which units the book's examples are measured in.
 *
 * Read-only dropdown rather than the free-text field the language uses: a
 * language is a name the model understands whatever you type, where this
 * selects one of two written rules.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MeasurementPicker(current: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = MEASUREMENT_SYSTEMS.firstOrNull { it.key == current } ?: MEASUREMENT_SYSTEMS.first()
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Measurements") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        // A white menu on a white page has no edge at all, and M3's own
        // edge is a shadow gradient, which dithers. Same hairline as
        // [InkDialog], same reason.
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.border(width = 1.dp, color = Ink),
        ) {
            MEASUREMENT_SYSTEMS.forEach { system ->
                DropdownMenuItem(
                    text = { Text(system.label) },
                    onClick = {
                        onSelected(system.key)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * How many pictures this book may draw.
 *
 * A property of the BOOK, like its language and its units, and fixed here for
 * the same reason: a course lavishly illustrated in chapter 1 and bare by
 * chapter 6 is worse than either. It is also the only place the reader is
 * asked about money before any is spent, which is why the choice carries a
 * note underneath rather than standing alone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FigurePicker(current: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = figurePolicyFor(current)
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Pictures") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        // A white menu on a white page has no edge at all, and M3's own
        // edge is a shadow gradient, which dithers. Same hairline as
        // [InkDialog], same reason.
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.border(width = 1.dp, color = Ink),
        ) {
            FIGURE_POLICIES.forEach { policy ->
                DropdownMenuItem(
                    text = { Text(policy.label) },
                    onClick = {
                        onSelected(policy.key)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StylePicker(current: StylePreset, onSelected: (StylePreset) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            value = current.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Teaching style") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        // A white menu on a white page has no edge at all, and M3's own
        // edge is a shadow gradient, which dithers. Same hairline as
        // [InkDialog], same reason.
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.border(width = 1.dp, color = Ink),
        ) {
            StylePresets.CHOICES.forEach { preset ->
                DropdownMenuItem(
                    text = { Text(preset.displayName) },
                    onClick = {
                        onSelected(preset)
                        expanded = false
                    },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun OutlinePreview(outline: CourseOutline) {
    // Plain Column (not LazyColumn) — the parent screen is itself vertically
    // scrollable, and nested scrolling would fight itself. Chapter counts
    // top out at ~12 so laziness buys nothing here.
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(outline.title, style = MaterialTheme.typography.titleLarge)
        outline.chapters.forEach { chapter ->
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                Text(
                    text = "${chapter.n}. ${chapter.title}  ·  ~${chapter.estMinutes} min",
                    style = MaterialTheme.typography.titleSmall,
                )
                chapter.objectives.forEach { objective ->
                    Text("• $objective", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/**
 * The outline as evidence for the second gate — what the book would contain,
 * flattened to text. Titles and objectives, because those are where an
 * intention that the topic line kept vague becomes explicit.
 */
private fun describeOutline(outline: CourseOutline): String = buildString {
    appendLine(outline.title)
    outline.chapters.forEach { c ->
        appendLine("${c.n}. ${c.title}")
        c.objectives.forEach { appendLine("   - $it") }
    }
}

private suspend fun runOutline(
    topic: String,
    dailyMinutes: Int,
    priorKnowledge: String,
    expectations: String,
    style: StylePreset,
    language: String,
    units: String,
    model: String,
    apiKey: String,
): OutlineUiState {
    KtorGeminiClient().use { client ->
        val generator = OutlineGenerator(client, model)
        return try {
            OutlineUiState.Ready(
                generator.generate(
                    topic = topic,
                    dailyMinutes = dailyMinutes,
                    priorKnowledge = priorKnowledge,
                    expectations = expectations,
                    style = style,
                    language = language.trim().ifBlank { "English" },
                    units = units,
                    apiKey = apiKey,
                ),
            )
        } catch (e: OutlineParseException) {
            OutlineUiState.Failed("Gemini returned malformed JSON. Try again.\n${e.message}")
        } catch (e: Exception) {
            OutlineUiState.Failed(geminiErrorLabel(e))
        }
    }
}
