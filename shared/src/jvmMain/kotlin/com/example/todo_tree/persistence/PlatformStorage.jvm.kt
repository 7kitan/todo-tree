// =============================================================================
//  PLATFORM_STORAGE — JVM
//  Stores forest JSON at ~/.todo-tree/forest.json.
//  Chose XDG home dir over XDG_CONFIG_HOME: simpler, no env var dependency,
//  works identically on macOS and Linux. File is plain UTF-8 JSON.
// =============================================================================

package com.example.todo_tree.persistence

import java.io.File

actual object PlatformStorage {
    // Use also { mkdirs() } on init so the directory exists before first write
    private val file = File(
        System.getProperty("user.home"),
        ".todo-tree/forest.json"
    ).also { it.parentFile.mkdirs() }

    actual fun write(json: String) = file.writeText(json)

    actual fun read(): String? =
        if (file.exists()) file.readText() else null
}
