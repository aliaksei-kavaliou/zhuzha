package com.inkfirst.app.ui

import androidx.compose.foundation.border
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DialogInk = Color(0xFF000000)
private val DialogPaper = Color(0xFFFFFFFF)

/**
 * A dialog that looks like it belongs in this book.
 *
 * Material 3's default is a tinted, rounded, shadowed container with purple
 * text, and every part of that is wrong here. The tint and the purple dither
 * into visible grey noise on a reflective panel — the palette rule is pure
 * black on pure white and it exists because the display physically cannot do
 * anything else cleanly. The shadow is a gradient, same problem. And the
 * rounded borderless card has no edge at all once the tint is gone, so it
 * floats over the page with nothing separating it from the text underneath.
 *
 * This is the same furniture as the reader's own format menu: square, one
 * hairline of black, flat white, serif. Anything that interrupts a page
 * should look like it came from the same press as the page.
 */
@Composable
internal fun InkDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.border(width = 1.dp, color = DialogInk),
        shape = RectangleShape,
        containerColor = DialogPaper,
        titleContentColor = DialogInk,
        textContentColor = DialogInk,
        // Tonal elevation is how M3 tints a surface by "lifting" it. At zero
        // the container stays the white it was told to be.
        tonalElevation = 0.dp,
        title = {
            Text(
                text = title,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                lineHeight = 24.sp,
                color = DialogInk,
            )
        },
        text = content,
        confirmButton = { DialogButton(confirmLabel, onConfirm) },
        dismissButton = { DialogButton(dismissLabel, onDismiss) },
    )
}

@Composable
private fun DialogButton(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            text = label,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = DialogInk,
        )
    }
}
