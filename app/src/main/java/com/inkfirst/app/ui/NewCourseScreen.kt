package com.inkfirst.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import com.inkfirst.core.MEASUREMENT_SYSTEMS
import com.inkfirst.core.KtorGeminiClient
import com.inkfirst.core.OutlineGenerator
import com.inkfirst.core.OutlineParseException
import com.inkfirst.core.StylePreset
import com.inkfirst.core.StylePresets
import com.inkfirst.core.courseSlug
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDate

private sealed interface OutlineUiState {
    data object Idle : OutlineUiState
    data object Loading : OutlineUiState
    data class Ready(val outline: CourseOutline) : OutlineUiState
    data class Failed(val message: String) : OutlineUiState
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
    var language by remember { mutableStateOf(store.getPreferredLanguage()) }
    var units by remember { mutableStateOf(DEFAULT_MEASUREMENT_SYSTEM) }
    var uiState by remember { mutableStateOf<OutlineUiState>(OutlineUiState.Idle) }

    // Held so Cancel can stop a request in flight. The request timeout is
    // three minutes, and a page with no way off it for three minutes is worse
    // than the bug this rewrite fixes.
    var job by remember { mutableStateOf<Job?>(null) }

    val apiKey = store.getApiKey()
    val model = store.getModelName()

    // A plain local function, not a composable — it runs on a tap rather than
    // during drawing. Both the form and the review page call it.
    fun generate() {
        focusManager.clearFocus()
        val minutes = dailyMinutes.toIntOrNull() ?: return
        val key = apiKey ?: return
        uiState = OutlineUiState.Loading
        job = scope.launch {
            uiState = runOutline(
                topic, minutes, priorKnowledge, expectations, style, language,
                units, model, key,
            )
        }
    }

    when (val s = uiState) {
        OutlineUiState.Loading -> GeneratingPage(
            topic = topic,
            onCancel = {
                job?.cancel()
                job = null
                uiState = OutlineUiState.Idle
            },
        )

        is OutlineUiState.Ready -> OutlineReviewPage(
            outline = s.outline,
            onApprove = {
                val course = courseStore.create(
                    outline = s.outline,
                    proposedSlug = courseSlug(s.outline.title),
                    topicPrompt = topic,
                    priorKnowledge = priorKnowledge,
                    expectations = expectations,
                    dailyMinutes = dailyMinutes.toIntOrNull() ?: 30,
                    style = style.key,
                    model = model,
                    language = language.trim().ifBlank { "English" },
                    units = units,
                    created = LocalDate.now().toString(),
                )
                onCourseCreated(course.slug)
            },
            onTryAgain = { generate() },
            onEdit = { uiState = OutlineUiState.Idle },
        )

        // Idle and Failed are the same page: a failure means "fix something
        // and go again", so it belongs on the form, at the TOP where it
        // cannot be missed, rather than on a dead-end page of its own.
        else -> Column(
            modifier = Modifier
                .fillMaxSize()
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
                label = { Text("Prior knowledge (optional)") },
                modifier = Modifier.fillMaxWidth().height(120.dp),
            )

            OutlinedTextField(
                value = expectations,
                onValueChange = { expectations = it },
                label = { Text("What you want to be able to do (optional)") },
                modifier = Modifier.fillMaxWidth().height(120.dp),
            )

            StylePicker(current = style, onSelected = { style = it })

            LanguagePicker(
                label = "Language",
                value = language,
                onValueChange = { language = it },
            )

            // Fixed for the life of the book, like the language: a course whose
            // chapter 1 measures in Celsius and whose chapter 6 measures in
            // Fahrenheit is worse than either.
            MeasurementPicker(current = units, onSelected = { units = it })

            Spacer(Modifier.height(8.dp))

            Button(
                enabled = !apiKey.isNullOrBlank() &&
                    topic.isNotBlank() &&
                    language.isNotBlank() &&
                    (dailyMinutes.toIntOrNull() ?: 0) > 0,
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
private fun GeneratingPage(topic: String, onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Writing the outline…", style = MaterialTheme.typography.headlineSmall)
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
        }

        Spacer(Modifier.height(16.dp))

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
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            StylePresets.ALL.forEach { preset ->
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
