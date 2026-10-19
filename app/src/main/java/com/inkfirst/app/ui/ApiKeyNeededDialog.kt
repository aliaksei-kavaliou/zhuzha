package com.inkfirst.app.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Where a Gemini key comes from. Shown as readable text as well as being tappable. */
private const val KEY_URL = "https://aistudio.google.com/apikey"

/**
 * What a reader meets the first time they ask for a chapter to be written.
 *
 * It used to be one line in the footer — "No API key set. Go to Settings
 * first." — which is a REFUSAL: it names a thing the reader does not have,
 * does not say what it is, where it comes from, or what it costs, and leaves
 * them to guess. Somebody who has just read two chapters and wants a third
 * deserves better than a locked door with no sign on it.
 *
 * It is a dialog rather than a footer line because instructions need room,
 * and it appears at the moment of asking rather than on first launch: by now
 * they have read the book and know what the key buys.
 */
@Composable
internal fun ApiKeyNeededDialog(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    /**
     * Whether there is a book around this ask.
     *
     * The dialog was written for the reader, where you are adding a chapter to
     * something you have been reading — so it says "a chapter", and it closes
     * by reassuring you that what is already written stays readable. Shown
     * from New course (2026-10-19) both of those are wrong: nothing has been
     * written yet and there is no book to reassure anyone about.
     */
    inBook: Boolean = true,
) {
    val context = LocalContext.current
    InkDialog(
        title = if (inBook) {
            "Writing a chapter needs your own key"
        } else {
            "Writing a book needs your own key"
        },
        onDismiss = onDismiss,
        confirmLabel = "Open Settings",
        onConfirm = onOpenSettings,
        dismissLabel = "Not now",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Body(
                "Books are written by Google's Gemini, using a key that belongs to you. " +
                    "It stays on this device. Nothing passes through the developer, and " +
                    "nothing is billed to anyone but you.",
            )
            Body("It takes about a minute, and it is free to begin:")
            Body("1.  Open the page below and sign in with a Google account.")
            Body("2.  Create an API key, and copy it.")
            Body("3.  Paste it into Settings here.")
            Text(
                text = KEY_URL,
                modifier = Modifier.clickable {
                    // A Boox has a browser, but it is not a device anyone wants
                    // to type a URL into — so the address is readable and
                    // copyable above, and the tap is the convenience rather
                    // than the only route.
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(KEY_URL)))
                    }.onFailure { if (it !is ActivityNotFoundException) throw it }
                },
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                textDecoration = TextDecoration.Underline,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                color = Ink,
            )
            Body(
                if (inBook) {
                    "The chapters already written in this book stay readable without a key — " +
                        "one is only needed to write more."
                } else {
                    "Books already on your shelf stay readable without a key — " +
                        "one is only needed to write more."
                },
            )
        }
    }
}

@Composable
private fun Body(text: String) {
    Text(
        text = text,
        fontFamily = FontFamily.Serif,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        color = Ink,
    )
}
