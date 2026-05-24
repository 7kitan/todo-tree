// =============================================================================
//  SWIPE_ACTIONS_ROW.KT
//  Per-row swipe with peek: left → Done+Waiting, right → Delete.
//  Custom anchored drag for precise control of peek + button taps.
// =============================================================================

package com.example.todo_tree.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val actionWidth = 80.dp
private const val snapDuration = 200

@Composable
fun SwipeActionsRow(
    onDone: () -> Unit,
    onWaiting: () -> Unit,
    onDelete: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        Box(modifier = modifier) { content() }
        return
    }

    val density = LocalDensity.current
    val actionPx = with(density) { actionWidth.toPx() }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    fun snapBack() {
        scope.launch { offsetX.animateTo(0f, tween(snapDuration)) }
    }

    Box(
        modifier = modifier.clipToBounds(),
    ) {
        // ==== Background action buttons (rendered behind the content) ====

        Row(
            modifier = Modifier.matchParentSize(),
        ) {
            // Delete button (left side → visible when swiped right)
            Box(
                modifier = Modifier.width(actionWidth).fillMaxHeight()
                    .background(Color(0xFFE53935))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onDelete(); snapBack() },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text("Delete", color = Color.White, style = MaterialTheme.typography.bodyMedium)
            }

            // Spacer fills the center (where the content sits)
            Box(Modifier.weight(1f)) {}

            // Done + Waiting buttons (right side → visible when swiped left)
            Box(
                modifier = Modifier.width(actionWidth).fillMaxHeight()
                    .background(Color(0xFF43A047))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onDone(); snapBack() },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text("Done", color = Color.White, style = MaterialTheme.typography.bodyMedium)
            }
            Box(
                modifier = Modifier.width(actionWidth).fillMaxHeight()
                    .background(Color(0xFFF9A825))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onWaiting(); snapBack() },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text("Wait", color = Color.White, style = MaterialTheme.typography.bodyMedium)
            }
        }

        // ==== Foreground content (draggable, fills full width) ====

        Box(
            modifier = Modifier.fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .graphicsLayer { shadowElevation = 2f }
                .pointerInput(actionPx) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitPointerEvent()
                            val startX = down.changes.first().position.x
                            var started = false

                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.first()
                                val delta = change.position.x - startX - offsetX.value

                                if (!started && abs(delta) > 8f) started = true
                                if (!started) continue

                                val newOffset = (offsetX.value + change.position.x - startX).coerceIn(-actionPx * 2.5f, actionPx * 2.5f)
                                scope.launch { offsetX.snapTo(newOffset) }
                                change.consume()
                            } while (event.changes.any { it.pressed })

                            val finalOffset = offsetX.value
                            when {
                                finalOffset > actionPx * 0.5f -> { onDelete(); snapBack() }
                                finalOffset < -actionPx * 0.5f -> { onDone(); snapBack() }
                                finalOffset < -actionPx * 0.2f || finalOffset > actionPx * 0.2f ->
                                    scope.launch { offsetX.animateTo(0f, tween(snapDuration)) }
                                else -> snapBack()
                            }
                        }
                    }
                },
        ) {
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(actionWidth))
                Box(Modifier.weight(1f)) { content() }
                Spacer(Modifier.width(actionWidth * 2))
            }
        }
    }
}
