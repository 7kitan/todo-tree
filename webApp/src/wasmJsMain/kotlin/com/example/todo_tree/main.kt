package com.example.todo_tree

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import org.jetbrains.skiko.wasm.onWasmReady
import org.jetbrains.skiko.ExperimentalSkikoApi

@OptIn(ExperimentalComposeUiApi::class, ExperimentalSkikoApi::class)
fun main() {
    onWasmReady {
        ComposeViewport { App() }
    }
}
