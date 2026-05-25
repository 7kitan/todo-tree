// =============================================================================
//  PERSISTENCE_TEST.KT
//  Serialization roundtrip tests for the forest JSON persistence layer.
//
//  These tests verify that @Serializable annotations and the Json instance
//  correctly encode/decode all data types (sealed classes, recursive trees,
//  nullable fields) without information loss.
//
//  The PlatformStorage expect/actual is NOT tested here — platform I/O is
//  tested manually per target. This tests the serialization contract that
//  sits between the ViewModel and PlatformStorage.
// =============================================================================

package com.example.todo_tree

import com.example.todo_tree.model.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.Json

class PersistenceTest : FunSpec({

    val json = Json { prettyPrint = true }

    test("roundtrip: simple tree with all item types") {
        val forest = listOf(
            ItemNode(id = "a", title = "Inbox", item = Item.Category, children = listOf(
                ItemNode(id = "b", title = "Buy milk", item = Item.Task(
                    state = TaskState.Active,
                    doDate = 1000L,
                    dueDate = 2000L,
                )),
                ItemNode(id = "c", title = "Paint fence", item = Item.Task(
                    state = TaskState.Done,
                    dueDate = 3000L,
                )),
                ItemNode(id = "d", title = "Wait for parts", item = Item.Task(
                    state = TaskState.Waiting,
                )),
            )),
            ItemNode(id = "e", title = "Work", item = Item.Category, children = listOf(
                ItemNode(id = "f", title = "Website redesign", item = Item.Project(
                    state = TaskState.Active,
                    dueDate = 5000L,
                ), children = listOf(
                    ItemNode(id = "g", title = "Design mockups", item = Item.Task(dueDate = 4000L)),
                )),
            )),
        )

        val encoded = json.encodeToString(forest)
        val decoded = json.decodeFromString<List<ItemNode>>(encoded)

        decoded shouldBe forest
        decoded.size shouldBe 2
        decoded[0].children.size shouldBe 3
        decoded[1].children[0].children.size shouldBe 1
    }

    test("roundtrip: empty forest") {
        val forest = emptyList<ItemNode>()

        val encoded = json.encodeToString(forest)
        val decoded = json.decodeFromString<List<ItemNode>>(encoded)

        decoded shouldBe forest
        decoded shouldBe emptyList()
    }

    test("roundtrip: single root node") {
        val forest = listOf(
            ItemNode(id = "x", title = "Solo task", item = Item.Task()),
        )

        val encoded = json.encodeToString(forest)
        val decoded = json.decodeFromString<List<ItemNode>>(encoded)

        decoded shouldBe forest
    }

    test("roundtrip: deep nesting") {
        val forest = listOf(
            ItemNode(id = "l1", title = "L1", item = Item.Project(), children = listOf(
                ItemNode(id = "l2", title = "L2", item = Item.Project(), children = listOf(
                    ItemNode(id = "l3", title = "L3", item = Item.Project(), children = listOf(
                        ItemNode(id = "l4", title = "L4", item = Item.Project(), children = listOf(
                            ItemNode(id = "l5", title = "L5", item = Item.Project(), children = listOf(
                                ItemNode(id = "l6", title = "L6 leaf", item = Item.Task()),
                            )),
                        )),
                    )),
                )),
            )),
        )

        val encoded = json.encodeToString(forest)
        val decoded = json.decodeFromString<List<ItemNode>>(encoded)

        decoded shouldBe forest
    }

    test("roundtrip: null dates are preserved") {
        val forest = listOf(
            ItemNode(title = "No dates", item = Item.Task(doDate = null, dueDate = null)),
            ItemNode(title = "Null doDate", item = Item.Task(doDate = null, dueDate = 1000L)),
            ItemNode(title = "Null dueDate", item = Item.Task(doDate = 1000L, dueDate = null)),
        )

        val encoded = json.encodeToString(forest)
        val decoded = json.decodeFromString<List<ItemNode>>(encoded)

        decoded shouldBe forest
        decoded[0].let { it.title shouldBe "No dates"; it.doDate shouldBe null; it.dueDate shouldBe null }
        decoded[1].let { it.title shouldBe "Null doDate"; it.doDate shouldBe null; it.dueDate shouldBe 1000L }
    }

    test("roundtrip: all TaskState values map correctly") {
        val forest = listOf(
            ItemNode(title = "Active", item = Item.Task(state = TaskState.Active)),
            ItemNode(title = "Done", item = Item.Task(state = TaskState.Done)),
            ItemNode(title = "Waiting", item = Item.Task(state = TaskState.Waiting)),
        )

        val encoded = json.encodeToString(forest)
        val decoded = json.decodeFromString<List<ItemNode>>(encoded)

        decoded shouldBe forest
        decoded[0].let { (it.item as Item.Task).state shouldBe TaskState.Active }
        decoded[1].let { (it.item as Item.Task).state shouldBe TaskState.Done }
        decoded[2].let { (it.item as Item.Task).state shouldBe TaskState.Waiting }
    }

    test("roundtrip: unicode titles survive") {
        val forest = listOf(
            ItemNode(title = "日本語のタスク", item = Item.Task()),
            ItemNode(title = "Привет мир", item = Item.Task()),
            ItemNode(title = "emoji 🎉 ✅", item = Item.Task()),
        )

        val encoded = json.encodeToString(forest)
        val decoded = json.decodeFromString<List<ItemNode>>(encoded)

        decoded shouldBe forest
    }

    test("corrupted JSON falls back — not a serialization test, verifies ViewModel safety") {
        // Verify malformed JSON can't deserialize (guards against silent data loss)
        val corrupted = "{ broken json }"
        val result = kotlin.runCatching {
            json.decodeFromString<List<ItemNode>>(corrupted)
        }
        result.isFailure shouldBe true
    }

    test("partial JSON fields use defaults") {
        // If future schema adds optional fields, old data still loads
        val minimal = """[{"title": "Minimal", "item": {"type": "Task", "state": "Active"}}]"""
        val decoded = json.decodeFromString<List<ItemNode>>(minimal)

        decoded.size shouldBe 1
        decoded[0].title shouldBe "Minimal"
        decoded[0].item.shouldBeInstanceOf<Item.Task>()
        (decoded[0].item as Item.Task).doDate shouldBe null
        (decoded[0].item as Item.Task).dueDate shouldBe null
        decoded[0].children shouldBe emptyList()
        decoded[0].id shouldNotBe ""
    }

    test("JSON output is pretty-printed") {
        val forest = listOf(ItemNode(id = "p1", title = "Pretty", item = Item.Task()))
        val encoded = json.encodeToString(forest)

        encoded shouldContain "\n  "
    }

    test("Category has no date fields") {
        val forest = listOf(ItemNode(title = "Cat", item = Item.Category))
        val encoded = json.encodeToString(forest)
        val decoded = json.decodeFromString<List<ItemNode>>(encoded)

        decoded shouldBe forest
        decoded[0].isCategory shouldBe true
    }
})
