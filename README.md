# todo-tree

Task manager with infinite nested subtrees, keyboard-first navigation, and due-date constraints. Written by [DeepSeek V4 Flash](https://chat.opencode.ai).

## Project Structure

```
shared/src/commonMain/kotlin/com/example/todo_tree/
├── App.kt                  # Root composable + MaterialTheme
├── TimeUtil.kt             # expect fun currentTimeMillis()
├── model/
│   ├── TaskNode.kt         # Core data class
│   └── TaskTree.kt         # Immutable tree operations
├── viewmodel/
│   └── TaskViewModel.kt    # State holder + sample forest
└── ui/
    ├── TaskTreeScreen.kt   # Main screen, scroll, gestures, keyboard
    ├── TaskTreeItems.kt    # TaskRow composable
    ├── EditTaskSheet.kt    # Edit modal + date pickers
    └── Navigation.kt       # Flatten, find, parent, siblings, key dispatch
```

## Dependencies

| Package | Purpose |
|---------|---------|
| `compose.runtime` | Compose runtime (state, effects) |
| `compose.foundation` | Layout, gestures, pointer input |
| `compose.material3` | Material Design 3 components |
| `compose.ui` | Graphics, input, modifiers |
| `compose.components.resources` | Font bundling via compose resources |
| `androidx.lifecycle.viewmodel-compose` | ViewModel in Compose |
| `androidx.lifecycle.runtime-compose` | Lifecycle-aware coroutines |
| `material-icons-core:1.7.3` | Check, expand/collapse icons |

## Running

- **Desktop**: `./gradlew :desktopApp:run`
- **Android**: `./gradlew :androidApp:assembleDebug`
- **Web (Wasm)**: `./gradlew :webApp:wasmJsBrowserDevelopmentRun`
- **Web (JS)**: `./gradlew :webApp:jsBrowserDevelopmentRun`

## Tests

- **Desktop**: `./gradlew :shared:jvmTest`
- **Android host**: `./gradlew :shared:testAndroidHostTest`
- **Web (Wasm)**: `./gradlew :shared:wasmJsTest`
- **Web (JS)**: `./gradlew :shared:jsTest`
