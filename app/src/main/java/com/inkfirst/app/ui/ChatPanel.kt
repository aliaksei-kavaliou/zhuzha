package com.inkfirst.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inkfirst.app.data.ApiKeyStore
import com.inkfirst.app.data.rememberCourseStore
import com.inkfirst.core.Course
import com.inkfirst.core.KtorGeminiClient
import com.inkfirst.core.PersistedChapter
import com.inkfirst.core.QaGenerator
import kotlinx.coroutines.launch
import java.time.LocalDate

private val ChatInkColor = Color(0xFF000000)
private val ChatPaperColor = Color(0xFFFFFFFF)
private val ChatMutedColor = Color(0xFF555555)
private val ChatBorderColor = Color(0xFF888888)

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

    val apiKey = apiKeyStore.getApiKey().orEmpty()
    val model = apiKeyStore.getModelName()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ChatPaperColor)
            .pointerInput(Unit) {
                // Tap outside inputs dismisses the IME so the Ask button stays reachable.
                detectTapGestures { focusManager.clearFocus() }
            },
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
            ) {
                TextButton(onClick = onClose) { Text("← Close", color = ChatInkColor) }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Ch ${chapter.n} · Q&A",
                    style = MaterialTheme.typography.bodySmall.copy(color = ChatMutedColor),
                )
            }
            HorizontalDivider(color = ChatBorderColor)

            // Scrollable content — selection quote + history + status.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (selection != null) {
                    Text(
                        text = "About this passage:",
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = ChatMutedColor,
                    )
                    SelectionContainer {
                        Text(
                            text = selection,
                            fontFamily = FontFamily.Serif,
                            fontStyle = FontStyle.Italic,
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            color = ChatInkColor,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    HorizontalDivider(color = ChatBorderColor)
                }

                if (historyRaw.isBlank()) {
                    Text(
                        text = if (selection != null)
                            "Type a question about the passage below and tap Ask."
                        else
                            "Type a question about this chapter and tap Ask.",
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic,
                        fontSize = 14.sp,
                        color = ChatMutedColor,
                    )
                } else {
                    SelectionContainer {
                        Text(
                            text = historyRaw.trim(),
                            fontFamily = FontFamily.Serif,
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            color = ChatInkColor,
                        )
                    }
                }

                if (pending) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.width(18.dp).height(18.dp).padding(end = 8.dp),
                            color = ChatInkColor,
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = "Thinking…",
                            fontFamily = FontFamily.Serif,
                            fontStyle = FontStyle.Italic,
                            fontSize = 14.sp,
                            color = ChatMutedColor,
                        )
                    }
                }

                error?.let {
                    Text(
                        text = it,
                        fontFamily = FontFamily.Serif,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            HorizontalDivider(color = ChatBorderColor)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                    placeholder = { Text("Ask a question…") },
                    enabled = !pending,
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = !pending && input.isNotBlank() && apiKey.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = ChatInkColor),
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
                                        created = LocalDate.now().toString(),
                                    )
                                    historyRaw = store.readQa(course.slug, chapter.slug)
                                    input = ""
                                    pending = false
                                }
                            } catch (e: Exception) {
                                error = e.message ?: "Unknown error"
                                pending = false
                            }
                        }
                    },
                ) { Text(if (apiKey.isBlank()) "Set API key" else "Ask", color = ChatPaperColor) }
            }
        }
    }
}
