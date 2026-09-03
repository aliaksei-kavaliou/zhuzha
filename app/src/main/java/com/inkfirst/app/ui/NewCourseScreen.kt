package com.inkfirst.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
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
import com.inkfirst.core.KtorGeminiClient
import com.inkfirst.core.OutlineGenerator
import com.inkfirst.core.OutlineParseException
import com.inkfirst.core.StylePreset
import com.inkfirst.core.StylePresets
import com.inkfirst.core.courseSlug
import kotlinx.coroutines.launch
import java.time.LocalDate

private sealed interface OutlineUiState {
    data object Idle : OutlineUiState
    data object Loading : OutlineUiState
    data class Ready(val outline: CourseOutline) : OutlineUiState
    data class Failed(val message: String) : OutlineUiState
}

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
    var uiState by remember { mutableStateOf<OutlineUiState>(OutlineUiState.Idle) }

    val apiKey = store.getApiKey()
    val model = store.getModelName()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // Tap outside any input dismisses the IME so the Generate button
                // becomes reachable on short viewports.
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

        OutlinedTextField(
            value = language,
            onValueChange = { language = it },
            label = { Text("Language") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))

        Button(
            enabled = uiState !is OutlineUiState.Loading &&
                !apiKey.isNullOrBlank() &&
                topic.isNotBlank() &&
                language.isNotBlank() &&
                (dailyMinutes.toIntOrNull() ?: 0) > 0,
            onClick = {
                focusManager.clearFocus()  // hide keyboard before the async call
                val minutes = dailyMinutes.toInt()
                val key = apiKey!!
                uiState = OutlineUiState.Loading
                scope.launch {
                    uiState = runOutline(topic, minutes, priorKnowledge, expectations, style, language, model, key)
                }
            },
        ) {
            Text(if (uiState is OutlineUiState.Loading) "Generating…" else "Generate outline")
        }

        Spacer(Modifier.height(8.dp))

        Box(modifier = Modifier.fillMaxWidth()) {
            when (val s = uiState) {
                OutlineUiState.Idle -> Unit
                OutlineUiState.Loading -> CircularProgressIndicator()
                is OutlineUiState.Failed -> Text(
                    text = s.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                is OutlineUiState.Ready -> Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinePreview(s.outline)
                    Button(onClick = {
                        val slug = courseSlug(s.outline.title)
                        val created = LocalDate.now().toString()
                        val course = courseStore.create(
                            outline = s.outline,
                            proposedSlug = slug,
                            topicPrompt = topic,
                            priorKnowledge = priorKnowledge,
                            expectations = expectations,
                            dailyMinutes = dailyMinutes.toIntOrNull() ?: 30,
                            style = style.key,
                            model = model,
                            language = language.trim().ifBlank { "English" },
                            created = created,
                        )
                        onCourseCreated(course.slug)
                    }) { Text("Approve & save") }
                }
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
                .menuAnchor()
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
