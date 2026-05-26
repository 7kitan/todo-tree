package com.example.todo_tree

import kotlin.js.ExperimentalWasmJsInterop

@OptIn(ExperimentalWasmJsInterop::class)
private val dateNow: () -> Double = js("() => Date.now()")

@OptIn(ExperimentalWasmJsInterop::class)
private val getHours: () -> Int = js("() => new Date().getHours()")

actual fun currentTimeMillis(): Long = dateNow().toLong()
actual fun currentHour(): Int = getHours()
