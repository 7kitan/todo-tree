// =============================================================================
//  UNDO_MANAGER.KT
//  Pure undo/redo stack manager. Takes current forest and a Command, returns
//  the new forest. No side effects, no ViewModel coupling.
//
//  Invariant:
//    undoStack — already-applied inverse commands (applying them undoes prior
//                forward actions).
//    redoStack — forward commands that were undone (applying them redoes).
//    Every execute() pushes the inverse, clears redoStack, and caps at maxSize.
// =============================================================================

package com.example.todo_tree.model

class UndoManager(private val maxSize: Int = 50) {
    private val undoStack = mutableListOf<Command>()
    private val redoStack = mutableListOf<Command>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun execute(cmd: Command, forest: List<ItemNode>): ApplyResult {
        val result = cmd.apply(forest)
        undoStack.add(result.inverse)
        redoStack.clear()
        if (undoStack.size > maxSize) undoStack.removeFirst()
        return result
    }

    fun undo(forest: List<ItemNode>): ApplyResult? {
        val inverseCmd = undoStack.removeLastOrNull() ?: return null
        val result = inverseCmd.apply(forest)
        redoStack.add(result.inverse)
        return result
    }

    fun redo(forest: List<ItemNode>): ApplyResult? {
        val cmd = redoStack.removeLastOrNull() ?: return null
        val result = cmd.apply(forest)
        undoStack.add(result.inverse)
        return result
    }
}
