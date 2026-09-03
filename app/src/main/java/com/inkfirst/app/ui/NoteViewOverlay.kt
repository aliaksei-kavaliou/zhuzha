package com.inkfirst.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inkfirst.core.Annotation

private val NoteViewInkColor = Color(0xFF000000)
private val NoteViewPaperColor = Color(0xFFFFFFFF)
private val NoteViewMutedColor = Color(0xFF555555)
private val NoteViewBorderColor = Color(0xFF888888)
private val NoteViewScrimColor = Color(0x40000000)

/**
 * Read-only view of a passage-anchored note. Opened by tapping an inline ✎
 * on the reader page. Provides Delete (removes the annotation via callback)
 * and Close.
 */
@Composable
fun NoteViewOverlay(
    annotation: Annotation,
    onClose: () -> Unit,
    onDelete: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NoteViewScrimColor)
            .pointerInput(Unit) { detectTapGestures { onClose() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .background(NoteViewPaperColor)
                .border(1.dp, NoteViewBorderColor)
                .pointerInput(Unit) { detectTapGestures { /* swallow */ } }
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "NOTE",
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = NoteViewMutedColor,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(
                    text = annotation.created,
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    fontSize = 12.sp,
                    color = NoteViewMutedColor,
                )
            }
            SelectionContainer {
                Text(
                    text = "\"${annotation.quote.trim()}\"",
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    color = NoteViewInkColor,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            HorizontalDivider(color = NoteViewBorderColor)
            SelectionContainer {
                Text(
                    text = annotation.note.trim(),
                    fontFamily = FontFamily.Serif,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    color = NoteViewInkColor,
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDelete) {
                    Text("Delete", color = NoteViewMutedColor)
                }
                TextButton(onClick = onClose) {
                    Text("Close", color = NoteViewInkColor)
                }
            }
        }
    }
}
