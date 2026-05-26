package com.example.todo_tree

import java.time.LocalTime

actual fun currentTimeMillis(): Long = System.currentTimeMillis()
actual fun currentHour(): Int = LocalTime.now().hour
