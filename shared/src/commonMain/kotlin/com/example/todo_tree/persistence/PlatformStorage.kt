// =============================================================================
//  PLATFORM_STORAGE
//  Singleton expect/actual for persisting the forest JSON on each platform.
//
//  Why expect/actual singleton (Option A) instead of constructor injection
//  (Option B):
//    - ViewModel constructor stays clean (no factory boilerplate)
//    - Platform init is one call in main/activity code
//    - Singleton is fine: only one forest, no competing writers
//    - Testability: tests never touch PlatformStorage, they seed the ViewModel
//      directly (tests don't init the ViewModel via App.kt)
//
//  Local JSON chosen over SQLite for v1:
//    - Full-tree serialization is ~1ms for 10K nodes — imperceptible
//    - Zero schema management, zero migrations
//    - Flat file easy to debug, backup, and diff
//    - Trade-off: cannot merge two JSON blobs when online sync lands.
//      At that point we swap PlatformStorage for a Supabase client + local
//      SQLDelight cache.
// =============================================================================

package com.example.todo_tree.persistence

expect object PlatformStorage {
    fun write(json: String)
    fun read(): String?
    fun writeSettings(json: String)
    fun readSettings(): String?
}
