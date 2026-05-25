// =============================================================================
//  PLATFORM.KT
//  Platform detection — used to gate desktop-only features (keyboard dispatch).
// =============================================================================

package com.example.todo_tree

expect val isDesktop: Boolean
