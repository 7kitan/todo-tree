// =============================================================================
//  PLATFORM_STORAGE — WasmJs
//  Stores forest JSON in browser localStorage under key "todo-tree-forest".
//
//  Uses @JsFun for JS interop (dynamic type is not available in Kotlin/Wasm).
//  Chose localStorage over IndexedDB: synchronous (no async overhead),
//  sufficient for <1MB forests, trivial API.
// =============================================================================

package com.example.todo_tree.persistence

import kotlin.js.ExperimentalWasmJsInterop

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("(key) => window.localStorage.getItem(key)")
private external fun getItem(key: String): String?

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("(key, value) => window.localStorage.setItem(key, value)")
private external fun setItem(key: String, value: String)

@OptIn(ExperimentalWasmJsInterop::class)
actual object PlatformStorage {
    private const val KEY = "todo-tree-forest"

    actual fun write(json: String) {
        setItem(KEY, json)
    }

    actual fun read(): String? = getItem(KEY)
}
