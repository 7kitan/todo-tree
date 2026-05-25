// =============================================================================
//  ARBS.KT
//  Custom Kotest property-test generators (Arb) for tree data structures.
// =============================================================================

package com.example.todo_tree

import com.example.todo_tree.model.Item
import com.example.todo_tree.model.ItemNode
import com.example.todo_tree.model.TaskState
import com.example.todo_tree.model.TaskTree
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.of

fun arbString(minLen: Int = 1, maxLen: Int = 12): Arb<String> = arbitrary {
    val len = Arb.int(minLen, maxLen).bind()
    buildString {
        repeat(len) { append('a' + Arb.int(0, 25).bind()) }
    }
}

// ==== Tree traversal helpers (for test use) ====

fun collectTaskIds(forest: List<ItemNode>): List<String> =
    forest.flatMap { listOf(it.id) + collectTaskIds(it.children) }

fun countNodes(forest: List<ItemNode>): Int =
    forest.sumOf { 1 + countNodes(it.children) }

fun findNode(forest: List<ItemNode>, taskId: String): ItemNode? =
    forest.firstOrNull { it.id == taskId }
        ?: forest.firstNotNullOfOrNull { findNode(it.children, taskId) }

fun hasLeftSibling(forest: List<ItemNode>, taskId: String): Boolean =
    TaskTree._findParent(forest, taskId)?.children
        ?.indexOfFirst { it.id == taskId }
        ?.let { it > 0 }
        ?: false

fun hasRightSibling(forest: List<ItemNode>, taskId: String): Boolean =
    TaskTree._findParent(forest, taskId)?.children
        ?.let { siblings ->
            val i = siblings.indexOfFirst { it.id == taskId }
            i >= 0 && i < siblings.size - 1
        }
        ?: false

fun hasParent(forest: List<ItemNode>, taskId: String): Boolean =
    TaskTree._findParent(forest, taskId) != null

fun isNotProject(forest: List<ItemNode>, taskId: String): Boolean =
    findNode(forest, taskId)?.item !is Item.Project

fun hasProjectAncestor(forest: List<ItemNode>, taskId: String): Boolean {
    var current = taskId
    while (true) {
        val parent = TaskTree._findParent(forest, current) ?: return false
        if (parent.item is Item.Project) return true
        current = parent.id
    }
}

fun hasProjectDescendant(forest: List<ItemNode>, taskId: String): Boolean {
    val node = findNode(forest, taskId) ?: return false
    return node.children.any { it.item is Item.Project || hasProjectDescendant(forest, it.id) }
}

fun forestHasNoProjects(forest: List<ItemNode>): Boolean =
    collectTaskIds(forest).none { findNode(forest, it)?.item is Item.Project }

fun parentHasOtherChildren(forest: List<ItemNode>, taskId: String): Boolean =
    TaskTree._findParent(forest, taskId)?.children?.any { it.id != taskId } == true

// ==== Generators ====

fun arbTaskState(): Arb<TaskState> =
    Arb.of(TaskState.Active, TaskState.Done, TaskState.Waiting)

fun arbItem(): Arb<Item> = Arb.of(Item.Task(), Item.Project(), Item.Category)

fun arbItemWithState(): Arb<Item> = arbitrary {
    when (Arb.of("task", "project", "category").bind()) {
        "task" -> Item.Task(
            state = arbTaskState().bind(),
            doDate = null,
            dueDate = null,
        )
        "project" -> Item.Project(
            state = when (Arb.of("active", "done").bind()) {
                "active" -> TaskState.Active
                else -> TaskState.Done
            },
            dueDate = null,
        )
        else -> Item.Category
    }
}

private fun arbLeafNode(): Arb<ItemNode> = arbitrary {
    ItemNode(
        title = arbString().bind(),
        item = arbItemWithState().bind(),
        children = emptyList(),
    )
}

private fun arbNodeWithChildren(maxDepth: Int): Arb<ItemNode> = arbitrary {
    val childCount = Arb.int(0..4).bind()
    ItemNode(
        title = arbString().bind(),
        item = arbItemWithState().bind(),
        children = if (maxDepth <= 1 || childCount == 0) {
            emptyList()
        } else {
            Arb.list(arbNodeWithChildren(maxDepth - 1), 0..childCount).bind()
        },
    )
}

fun arbForestNode(maxDepth: Int = 3): Arb<ItemNode> = arbitrary {
    if (maxDepth <= 1) {
        arbLeafNode().bind()
    } else {
        arbNodeWithChildren(maxDepth).bind()
    }
}

fun arbForest(minSize: Int = 2, maxSize: Int = 8, maxDepth: Int = 3): Arb<List<ItemNode>> =
    Arb.list(arbForestNode(maxDepth), minSize..maxSize)

fun arbNonProjectItem(): Arb<Item> = arbitrary {
    when (Arb.of("task", "category").bind()) {
        "task" -> Item.Task(
            state = arbTaskState().bind(),
            doDate = null,
            dueDate = null,
        )
        else -> Item.Category
    }
}

fun arbForestWithoutProjects(
    minSize: Int = 2,
    maxSize: Int = 6,
    maxDepth: Int = 2,
): Arb<List<ItemNode>> = arbForestNoProj(minSize, maxSize, maxDepth)

private fun arbLeafNoProj(): Arb<ItemNode> = arbitrary {
    ItemNode(title = arbString().bind(), item = arbNonProjectItem().bind(), children = emptyList())
}

private fun arbNodeNoProj(maxDepth: Int): Arb<ItemNode> = arbitrary {
    val childCount = Arb.int(0..3).bind()
    ItemNode(
        title = arbString().bind(),
        item = arbNonProjectItem().bind(),
        children = if (maxDepth <= 1 || childCount == 0) emptyList()
        else Arb.list(arbNodeNoProj(maxDepth - 1), 0..childCount).bind(),
    )
}

private fun arbForestNoProj(minSize: Int, maxSize: Int, maxDepth: Int): Arb<List<ItemNode>> =
    Arb.list(arbNodeNoProj(maxDepth), minSize..maxSize)

/**
 * Pairs a forest with a taskId that exists within it.
 */
fun arbForestWithTaskId(
    minSize: Int = 2,
    maxSize: Int = 8,
    maxDepth: Int = 3,
): Arb<Pair<List<ItemNode>, String>> = arbitrary {
    val forest = arbForest(minSize, maxSize, maxDepth).bind()
    val ids = collectTaskIds(forest)
    val taskId = ids[Arb.int(0, ids.size - 1).bind()]
    Pair(forest, taskId)
}

/**
 * Pairs a forest with a non-root taskId (has a parent).
 */
fun arbForestWithNonRootTaskId(
    minSize: Int = 2,
    maxSize: Int = 6,
    maxDepth: Int = 3,
): Arb<Pair<List<ItemNode>, String>> = arbitrary {
    var forest = arbForest(minSize, maxSize, maxDepth).bind()
    // Ensure there's at least one non-root task
    val ids = collectTaskIds(forest).filter { hasParent(forest, it) }
    if (ids.isEmpty()) {
        // Add a child to make a non-root task
        val rootId = collectTaskIds(forest).first()
        val child = ItemNode(title = "child", item = Item.Task())
        forest = TaskTree.addTask(forest, rootId, child)
    }
    val nonRootIds = collectTaskIds(forest).filter { hasParent(forest, it) }
    val taskId = nonRootIds[Arb.int(0, nonRootIds.size - 1).bind()]
    Pair(forest, taskId)
}

/**
 * Pairs a forest with a taskId that has a left sibling (can be moved up / indented).
 */
fun arbForestWithLeftSibling(
    minSize: Int = 2,
    maxSize: Int = 6,
    maxDepth: Int = 2,
): Arb<Pair<List<ItemNode>, String>> = arbitrary {
    var forest = arbForest(minSize, maxSize, maxDepth).bind()
    val leftSiblingIds = collectTaskIds(forest).filter { hasLeftSibling(forest, it) }
    if (leftSiblingIds.isEmpty()) {
        // Build a simple forest with siblings
        val parent = ItemNode(title = "p", item = Item.Category)
        val child1 = ItemNode(title = "a", item = Item.Task())
        val child2 = ItemNode(title = "b", item = Item.Task())
        forest = listOf(parent.copy(children = listOf(child1, child2)))
    }
    val ids = collectTaskIds(forest).filter { hasLeftSibling(forest, it) }
    val taskId = ids[Arb.int(0, ids.size - 1).bind()]
    Pair(forest, taskId)
}
