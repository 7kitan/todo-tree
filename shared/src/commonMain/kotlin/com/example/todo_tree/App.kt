// =============================================================================
//  APP.KT
//  Root composable: MaterialTheme with IBM Plex Mono + TaskTreeScreen.
// =============================================================================

package com.example.todo_tree

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.todo_tree.ui.TaskTreeScreen
import com.example.todo_tree.viewmodel.TaskViewModel
import org.jetbrains.compose.resources.Font
import todo_tree.shared.generated.resources.Res
import todo_tree.shared.generated.resources.ibm_plex_mono_bold
import todo_tree.shared.generated.resources.ibm_plex_mono_italic
import todo_tree.shared.generated.resources.ibm_plex_mono_regular

@Composable
fun App() {
    MaterialTheme(typography = Typography(
        bodyLarge = TextStyle(fontFamily = FontFamily(Font(Res.font.ibm_plex_mono_regular, FontWeight.Normal), Font(Res.font.ibm_plex_mono_bold, FontWeight.Bold), Font(Res.font.ibm_plex_mono_italic, FontWeight.Normal)), fontSize = 15.sp),
        bodyMedium = TextStyle(fontFamily = FontFamily(Font(Res.font.ibm_plex_mono_regular)), fontSize = 14.sp),
        labelSmall = TextStyle(fontFamily = FontFamily(Font(Res.font.ibm_plex_mono_regular)), fontSize = 12.sp),
    )) {
        TaskTreeScreen(viewModel = viewModel { TaskViewModel() }, modifier = Modifier.fillMaxSize())
    }
}
