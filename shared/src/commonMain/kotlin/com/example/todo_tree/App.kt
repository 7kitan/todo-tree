// =============================================================================
//  APP.KT
//  Root composable: configurable dark theme, IBM Plex Mono, TaskTreeScreen.
//  Dark mode preference is persisted via PlatformStorage.settings.json.
// =============================================================================

package com.example.todo_tree

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.todo_tree.persistence.PlatformStorage
import com.example.todo_tree.ui.TaskTreeScreen
import com.example.todo_tree.viewmodel.TaskViewModel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.Font
import todo_tree.shared.generated.resources.Res
import todo_tree.shared.generated.resources.ibm_plex_mono_bold
import todo_tree.shared.generated.resources.ibm_plex_mono_italic
import todo_tree.shared.generated.resources.ibm_plex_mono_regular

// =============================================================================
//  Settings
// =============================================================================

@Serializable
data class Settings(val isDarkTheme: Boolean = false)

private val settingsJson = Json { prettyPrint = false }

// =============================================================================
//  CompositionLocal for theme toggle
// =============================================================================

val LocalDarkMode = compositionLocalOf { mutableStateOf(false) }

// =============================================================================
//  Theme colors
// =============================================================================

private val LightColors = lightColorScheme(
    primary = Color(0xFF1976D2),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF90CAF9),
)

// =============================================================================
//  App entry point
// =============================================================================

@Composable
fun App() {
    val systemDark = isSystemInDarkTheme()
    val darkMode = remember {
        val stored = PlatformStorage.readSettings()
        val s = stored?.let { runCatching { settingsJson.decodeFromString<Settings>(it) }.getOrNull() }
        mutableStateOf(s?.isDarkTheme ?: systemDark)
    }

    fun saveTheme() {
        val s = Settings(isDarkTheme = darkMode.value)
        PlatformStorage.writeSettings(settingsJson.encodeToString(s))
    }

    CompositionLocalProvider(LocalDarkMode provides darkMode) {
        MaterialTheme(
            colorScheme = if (darkMode.value) DarkColors else LightColors,
            typography = Typography(
                bodyLarge = TextStyle(fontFamily = FontFamily(Font(Res.font.ibm_plex_mono_regular, FontWeight.Normal), Font(Res.font.ibm_plex_mono_bold, FontWeight.Bold), Font(Res.font.ibm_plex_mono_italic, FontWeight.Normal)), fontSize = 15.sp),
                bodyMedium = TextStyle(fontFamily = FontFamily(Font(Res.font.ibm_plex_mono_regular)), fontSize = 14.sp),
                bodySmall = TextStyle(fontFamily = FontFamily(Font(Res.font.ibm_plex_mono_regular)), fontSize = 12.sp),
                labelLarge = TextStyle(fontFamily = FontFamily(Font(Res.font.ibm_plex_mono_regular)), fontSize = 14.sp),
                labelSmall = TextStyle(fontFamily = FontFamily(Font(Res.font.ibm_plex_mono_regular)), fontSize = 12.sp),
                titleLarge = TextStyle(fontFamily = FontFamily(Font(Res.font.ibm_plex_mono_regular, FontWeight.Bold)), fontSize = 18.sp),
                titleMedium = TextStyle(fontFamily = FontFamily(Font(Res.font.ibm_plex_mono_regular)), fontSize = 16.sp),
                titleSmall = TextStyle(fontFamily = FontFamily(Font(Res.font.ibm_plex_mono_regular)), fontSize = 14.sp),
            )) {
                TaskTreeScreen(viewModel = viewModel { TaskViewModel() }, modifier = Modifier.fillMaxSize(), onThemeToggle = {
                    darkMode.value = !darkMode.value
                    saveTheme()
                })
            }
        }
    }
