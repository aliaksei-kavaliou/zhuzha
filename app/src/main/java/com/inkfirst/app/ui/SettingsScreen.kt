package com.inkfirst.app.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.material3.Button
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.inkfirst.app.data.ApiKeyStore
import com.inkfirst.app.data.ReaderPrefs
import com.inkfirst.app.data.maskApiKey

@Composable
fun SettingsScreen(
    onDone: () -> Unit,
    onBackup: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { ApiKeyStore(context) }
    val readerPrefs = remember { ReaderPrefs(context) }
    val focusManager = LocalFocusManager.current

    var apiKeyDraft by remember { mutableStateOf("") }
    var modelDraft by remember { mutableStateOf(store.getModelName()) }
    var imageModelDraft by remember { mutableStateOf(store.getImageModelName()) }
    var languageDraft by remember { mutableStateOf(store.getPreferredLanguage()) }
    var fingerDraws by remember { mutableStateOf(readerPrefs.getFingerDraws()) }
    var showKey by remember { mutableStateOf(false) }
    var storedKey by remember { mutableStateOf(store.getApiKey()) }
    var savedNote by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(showKey) {
        if (showKey) apiKeyDraft = storedKey.orEmpty()
    }

    // Scrolls, and the actions do not scroll with it.
    //
    // This screen was a plain fillMaxSize Column with neither pagination nor
    // a scroll container — the only one in the app that was neither, which is
    // why it survived until someone opened it on a 320x640 emulator. Its
    // content is roughly twice that tall, and Save/Done are the LAST thing in
    // the column, so on a phone they were not merely below the fold but
    // unreachable: there was no way to save an API key at all.
    //
    // Same shape as the outline review page: content scrolls in the weighted
    // half, the actions are pinned outside it. imePadding on the root keeps
    // that row above the keyboard, which matters on a screen that is mostly
    // text fields.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { focusManager.clearFocus() })
            }
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineMedium)

            Text(
                text = "Your Gemini API key stays on this device (encrypted). Get one at " +
                    "aistudio.google.com/apikey.",
                style = MaterialTheme.typography.bodySmall,
            )

            // The encrypted store could not be opened — the device's Keystore
            // would not give us a master key. The app still works, but nothing
            // typed here outlives the session, and a reader who is not told
            // simply re-enters their key every morning and wonders why.
            if (!store.isPersistent) {
                Text(
                    text = "This device would not unlock encrypted storage, so a key entered " +
                        "here lasts until the app closes. It is not saved anywhere, and it is " +
                        "not written in plain text. Changing the screen lock, or reinstalling, " +
                        "usually fixes it.",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (!storedKey.isNullOrBlank() && !showKey) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Current: ${maskApiKey(storedKey)}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    TextButton(onClick = { showKey = true }) { Text("Change") }
                }
            } else {
                OutlinedTextField(
                    value = apiKeyDraft,
                    onValueChange = { apiKeyDraft = it },
                    label = { Text("Gemini API key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            OutlinedTextField(
                value = modelDraft,
                onValueChange = { modelDraft = it },
                label = { Text("Model") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = imageModelDraft,
                onValueChange = { imageModelDraft = it },
                label = { Text("Figure model") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "Draws chapter figures. Must be an image-capable model — " +
                    "roughly 3 cents a picture on the default.",
                style = MaterialTheme.typography.bodySmall,
            )

            LanguagePicker(
                label = "Preferred language",
                value = languageDraft,
                onValueChange = { languageDraft = it },
            )
            Text(
                text = "Used to pre-fill the language on new courses. Existing courses keep the language they were created with.",
                style = MaterialTheme.typography.bodySmall,
            )

            // NOT a dev toggle, though it was labelled one until 2026-10-19.
            // On a phone there is no stylus, so this switch is the ONLY way
            // to use the working room under an exercise — and a reader who
            // meets it marked "(dev)" will reasonably leave it alone.
            //
            // It is still OFF by default, and defaulting it on for stylus-less
            // devices was considered and rejected after measuring: the ink
            // overlay claims the whole touch stream on ACTION_DOWN whenever
            // this is set, so with it on a swipe DRAWS instead of turning the
            // page. Verified on the emulator — a left swipe came back as a
            // stroke. Turning it on silently would hand a phone reader a book
            // they could not page through.
            //
            // So the honest fix is the WORDING: say what it costs, and name
            // the way out (the volume buttons still turn pages, which is also
            // verified). Letting a finger draw only inside the working room
            // would give a phone both, and is the real fix when there is time
            // to test it.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Finger draws ink",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(end = 12.dp),
                )
                androidx.compose.material3.Switch(
                    checked = fingerDraws,
                    onCheckedChange = {
                        fingerDraws = it
                        readerPrefs.setFingerDraws(it)
                    },
                )
            }
            Text(
                text = "Off: your finger turns pages; a stylus draws. " +
                    "On: your finger draws instead of turning pages — use the " +
                    "volume buttons to turn. Turn this on to write in the space " +
                    "under an exercise on a device with no stylus.",
                style = MaterialTheme.typography.bodySmall,
            )

            HorizontalDivider()

            Text("Backup", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Copy your books to a folder you choose, and bring them back. " +
                    "They are only on this device until you do.",
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(
                onClick = onBackup,
                contentPadding = PaddingValues(0.dp),
            ) { Text("Backup and restore") }

            savedNote?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }

        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                // Enable if we're either setting a new key OR one is already stored
                // (so the model field alone can be updated without re-entering the key).
                enabled = apiKeyDraft.isNotBlank() || !storedKey.isNullOrBlank(),
                onClick = {
                    focusManager.clearFocus()
                    if (apiKeyDraft.isNotBlank()) {
                        store.setApiKey(apiKeyDraft)
                    }
                    store.setModelName(modelDraft)
                    store.setImageModelName(imageModelDraft)
                    store.setPreferredLanguage(languageDraft)
                    storedKey = store.getApiKey()
                    showKey = false
                    apiKeyDraft = ""
                    savedNote = "Saved."
                },
            ) { Text("Save") }

            TextButton(onClick = onDone) { Text("Done") }
        }
    }
}
