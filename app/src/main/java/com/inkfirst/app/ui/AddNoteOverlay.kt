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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.inkfirst.app.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Modal composed on top of the Reader when the user taps "Add note" on a
 * selection. Shows the quoted passage as read-only context, then a text
 * field for the note. Tapping outside the card cancels; Save persists via
 * the callback passed in.
 */
@Composable
fun AddNoteOverlay(
    quote: String,
    onCancel: () -> Unit,
    onSave: (String) -> Unit,
) {
    var note by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Edge-to-edge (2026-10-02) made adjustResize inert — see
            // ChatPanel for the full story. Every text field needs this.
            .imePadding()
            .background(Scrim)
            // Tap outside the card = cancel. Card intercepts its own taps below.
            .pointerInput(Unit) { detectTapGestures { onCancel() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .background(Paper)
                .border(1.dp, Hairline)
                .pointerInput(Unit) { detectTapGestures { /* swallow */ } }
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "NOTE ON PASSAGE",
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = Muted,
            )
            SelectionContainer {
                Text(
                    text = quote.trim(),
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    color = Ink,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            HorizontalDivider(color = Hairline)
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp),
                placeholder = {
                    Text(
                        text = stringResource(R.string.type_your_note),
                        fontFamily = FontFamily.Serif,
                        fontSize = 15.sp,
                        color = Muted,
                    )
                },
                textStyle = TextStyle(
                    fontFamily = FontFamily.Serif,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    color = Ink,
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Paper,
                    unfocusedContainerColor = Paper,
                    cursorColor = Ink,
                ),
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.cancel), color = Ink)
                }
                Spacer(Modifier.padding(horizontal = 4.dp))
                Button(
                    enabled = note.isNotBlank(),
                    onClick = { onSave(note) },
                    colors = ButtonDefaults.buttonColors(containerColor = Ink),
                ) {
                    Text(stringResource(R.string.save), color = Paper)
                }
            }
        }
    }
}
