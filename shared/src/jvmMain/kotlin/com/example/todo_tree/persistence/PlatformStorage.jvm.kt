// =============================================================================
//  PLATFORM_STORAGE — JVM
//  Stores forest JSON at ~/.todo-tree/forest.json.
//  Chose XDG home dir over XDG_CONFIG_HOME: simpler, no env var dependency,
//  works identically on macOS and Linux. File is plain UTF-8 JSON.
// =============================================================================

package com.example.todo_tree.persistence

import java.io.File

actual object PlatformStorage {
    private val dir = File(System.getProperty("user.home"), ".todo-tree")
        .also { it.mkdirs() }
    private val forestFile = File(dir, "forest.json")
    private val settingsFile = File(dir, "settings.json")

    actual fun write(json: String) = forestFile.writeText(json)

    actual fun read(): String? =
        if (forestFile.exists()) forestFile.readText() else null

    actual fun writeSettings(json: String) = settingsFile.writeText(json)

    actual fun readSettings(): String? =
        if (settingsFile.exists()) settingsFile.readText() else null
}
