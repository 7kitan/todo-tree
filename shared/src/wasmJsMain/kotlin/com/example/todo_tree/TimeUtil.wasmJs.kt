package com.example.todo_tree

import kotlin.js.ExperimentalWasmJsInterop

@OptIn(ExperimentalWasmJsInterop::class)
private val dateNow: () -> Double = js("() => Date.now()")

actual fun currentTimeMillis(): Long = dateNow().toLong()
