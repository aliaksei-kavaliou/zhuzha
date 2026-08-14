package com.inkfirst.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.inkfirst.app.data.rememberCourseStore
import com.inkfirst.core.Course

@Composable
fun CourseDetailScreen(
    slug: String,
    onBack: () -> Unit,
) {
    val store = rememberCourseStore()
    var course by remember { mutableStateOf<Course?>(null) }

    LaunchedEffect(slug) { course = store.load(slug) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back") }
        }

        val c = course
        if (c == null) {
            Text("Course not found: $slug", color = MaterialTheme.colorScheme.error)
        } else {
            Text(c.title, style = MaterialTheme.typography.headlineMedium)
            Text(
                text = "${c.chapters.size} chapters · ${c.dailyMinutes} min/day · ${c.style}",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.padding(top = 4.dp))
            HorizontalDivider()

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(c.chapters) { chapter ->
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(
                            text = "${chapter.n}. ${chapter.title}  ·  ~${chapter.estMinutes} min",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = "status: ${chapter.status.name.lowercase()}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        chapter.objectives.forEach { objective ->
                            Text("• $objective", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
