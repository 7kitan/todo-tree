// =============================================================================
//  TASK_FAB.KT
//  FAB with radial speed dial — items fan in a right semicircle (top→bottom).
// =============================================================================

package com.example.todo_tree.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// ==== Internal data ====

private data class FabAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@Composable
fun TaskFab(
    expanded: Boolean,
    onToggle: () -> Unit,
    onAddRoot: () -> Unit,
    onAddSubtask: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val radiusPx = with(density) { 72.dp.toPx() }

    val actions = remember {
        listOf(
            FabAction("Add root", Icons.Filled.Add) { onAddRoot() },
            FabAction("Add subtask", Icons.AutoMirrored.Filled.KeyboardArrowRight) { onAddSubtask() },
            FabAction("Delete", Icons.Filled.Delete) { onDelete() },
        )
    }

    Box(modifier = modifier) {
        FloatingActionButton(
            onClick = onToggle,
            shape = CircleShape,
        ) {
            Icon(
                imageVector = if (expanded) Icons.Filled.Close else Icons.Filled.Add,
                contentDescription = if (expanded) "Close" else "Actions",
            )
        }

        // Right semicircle: -90° (top) → 0° (right) → +90° (bottom)
        for (index in actions.indices) {
            val action = actions[index]
            key(action.label) {
                val t = index.toDouble() / (actions.size - 1)
                val angle = -PI / 2 + PI * t  // -90° to +90°
                val x = (-radiusPx * cos(angle).toFloat()).roundToInt()
                val y = (radiusPx * sin(angle).toFloat()).roundToInt()

                AnimatedVisibility(
                    visible = expanded,
                    modifier = Modifier.offset { IntOffset(x, y) },
                    enter = scaleIn(tween(300, delayMillis = index * 40)) +
                            fadeIn(tween(300, delayMillis = index * 40)),
                    exit = scaleOut(tween(200)) + fadeOut(tween(200)),
                ) {
                    SmallFloatingActionButton(
                        onClick = {
                            onToggle()
                            action.onClick()
                        },
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Icon(action.icon, action.label, Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}
