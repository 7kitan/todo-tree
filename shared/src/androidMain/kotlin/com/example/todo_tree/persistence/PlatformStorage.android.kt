// =============================================================================
//  PLATFORM_STORAGE — Android
//  Stores forest JSON in app's internal files directory (Context.filesDir).
//  Requires PlatformStorage.init(context) from MainActivity.onCreate before
//  the composable tree is built.
//
//  Chose filesDir over SharedPreferences:
//    - Human-readable JSON, easy to debug and backup
//    - Trivially convertible to content:// URI for export
//    - Survives app cache clears (unlike cacheDir)
//    - No size limits (SharedPreferences has 100KB recommended cap)
// =============================================================================

package com.example.todo_tree.persistence

import android.content.Context
import java.io.File

actual object PlatformStorage {
    private var storageDir: File? = null

    fun init(context: Context) {
        storageDir = context.filesDir
    }

    private val file: File
        get() {
            val dir = storageDir
                ?: error("PlatformStorage.init(context) must be called before use")
            return File(dir, "forest.json")
        }

    actual fun write(json: String) = file.writeText(json)

    actual fun read(): String? =
        if (file.exists()) file.readText() else null
}
