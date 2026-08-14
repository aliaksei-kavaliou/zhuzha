package com.inkfirst.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.inkfirst.app.ui.NewCourseScreen
import com.inkfirst.app.ui.SettingsScreen

private object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val NEW_COURSE = "new-course"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    InkFirstApp()
                }
            }
        }
    }
}

@Composable
private fun InkFirstApp() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) { HomeScreen(nav) }
        composable(Routes.SETTINGS) { SettingsScreen(onDone = { nav.popBackStack() }) }
        composable(Routes.NEW_COURSE) { NewCourseScreen(onBack = { nav.popBackStack() }) }
    }
}

@Composable
private fun HomeScreen(nav: NavHostController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("InkFirst", style = MaterialTheme.typography.headlineLarge)
        Text("AI-generated study books for e-ink.", style = MaterialTheme.typography.bodyMedium)
        Button(onClick = { nav.navigate(Routes.NEW_COURSE) }) { Text("New course") }
        Button(onClick = { nav.navigate(Routes.SETTINGS) }) { Text("Settings") }
    }
}
