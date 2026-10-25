package com.inkfirst.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Common languages offered as suggestions. Ordered by regional relevance
 * first, then broad utility. Free-text override remains
 * possible — the TextField is editable, the dropdown just seeds choices.
 */
val LanguageSuggestions: List<String> = listOf(
    "English",
    "Belarusian",
    "Russian",
    "Ukrainian",
    "Polish",
    "Spanish",
    "French",
    "German",
    "Italian",
    "Portuguese",
    "Dutch",
    "Czech",
    "Chinese (Simplified)",
    "Japanese",
    "Korean",
    "Arabic",
)

/**
 * Editable-with-dropdown language picker. Users can pick from suggestions
 * or type any language name — the field is not read-only so exotic choices
 * work. Uses the same pattern as [StylePicker] in NewCourseScreen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguagePicker(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    // What the reader has TYPED since the list was last opened, or null if
    // they have not typed since. The list filters on this and never on the
    // committed value.
    //
    // Filtering on the value itself is the obvious thing and it is wrong:
    // a field holding "English" filters the sixteen suggestions down to
    // "English", so opening the picker shows ONE item and the screen says
    // there is only one language. Reported 2026-10-21: "i tap and I see only
    // english and need to delete, but I guess it search work" — the search
    // did work, and there was no way to discover that a list existed.
    //
    // Opening shows everything, the way [StylePicker] beside it does;
    // typing narrows. The field stays editable because `language` is free
    // text by design — the suggestions seed it, they do not bound it.
    var typed by remember { mutableStateOf<String?>(null) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {
            expanded = it
            // A fresh opening is not a search.
            if (it) typed = null
        },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                typed = it
                // Reopen the dropdown as the user types so suggestions stay reachable.
                expanded = true
            },
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryEditable)
                .fillMaxWidth(),
        )
        val search = typed?.trim().orEmpty()
        val filtered = if (search.isEmpty()) {
            LanguageSuggestions
        } else {
            LanguageSuggestions.filter { it.contains(search, ignoreCase = true) }
        }
        if (filtered.isNotEmpty()) {
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.border(width = 1.dp, color = Ink),
            ) {
                filtered.forEach { suggestion ->
                    DropdownMenuItem(
                        text = { Text(suggestion) },
                        onClick = {
                            onValueChange(suggestion)
                            // Picking is not typing: the next opening shows
                            // the whole list again, not just what was picked.
                            typed = null
                            expanded = false
                        },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}
