package com.example.todo_tree

actual fun currentTimeMillis(): Long = js("Date.now()").unsafeCast<Double>().toLong()
