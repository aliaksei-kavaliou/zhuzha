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
 * Common languages offered as suggestions. Ordered by Aliaksei-relevance
 * first (his region), then broad-utility. Free-text override remains
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
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
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
        val filtered = LanguageSuggestions.filter {
            value.isBlank() || it.contains(value, ignoreCase = true)
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
                            expanded = false
                        },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}
