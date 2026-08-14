package com.inkfirst.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import com.inkfirst.app.data.ApiKeyStore
import com.inkfirst.app.data.maskApiKey

@Composable
fun SettingsScreen(
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { ApiKeyStore(context) }

    var apiKeyDraft by remember { mutableStateOf("") }
    var modelDraft by remember { mutableStateOf(store.getModelName()) }
    var showKey by remember { mutableStateOf(false) }
    var storedKey by remember { mutableStateOf(store.getApiKey()) }
    var savedNote by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(showKey) {
        if (showKey) apiKeyDraft = storedKey.orEmpty()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        Text(
            text = "Your Gemini API key stays on this device (encrypted). Get one at ai.google.dev.",
            style = MaterialTheme.typography.bodySmall,
        )

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

        savedNote?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                // Enable if we're either setting a new key OR one is already stored
                // (so the model field alone can be updated without re-entering the key).
                enabled = apiKeyDraft.isNotBlank() || !storedKey.isNullOrBlank(),
                onClick = {
                    if (apiKeyDraft.isNotBlank()) {
                        store.setApiKey(apiKeyDraft)
                    }
                    store.setModelName(modelDraft)
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
