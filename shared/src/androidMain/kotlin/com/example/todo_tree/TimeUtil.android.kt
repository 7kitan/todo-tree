package com.example.todo_tree

import java.util.Calendar

actual fun currentTimeMillis(): Long = java.lang.System.currentTimeMillis()
actual fun currentHour(): Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
