// =============================================================================
//  SWIPE_ACTIONS_ROW.KT
//  Buttons overlay on top of content. Content always fills full width.
//  Slides: right → Delete peeks, left → Done+Wait peek.
// =============================================================================

package com.example.todo_tree.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val actionWidth = 80.dp
private val rowHeight = 40.dp
private const val snapDuration = 200

@Composable
fun SwipeActionsRow(
    onDone: () -> Unit,
    onWaiting: () -> Unit,
    onDelete: () -> Unit,
    enabled: Boolean,
    resetCount: Int = 0,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        Box(modifier = modifier) { content() }
        return
    }

    val density = LocalDensity.current
    val actionPx = with(density) { actionWidth.toPx() }
    val swipeOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    fun snapTo(target: Float) {
        scope.launch { swipeOffset.animateTo(target, tween(snapDuration)) }
    }

    LaunchedEffect(resetCount) {
        if (resetCount > 0) snapTo(0f)
    }

    BoxWithConstraints(
        modifier = modifier.clipToBounds(),
    ) {
        val screenWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val deleteX = swipeOffset.value.coerceIn(0f, actionPx) - actionPx
        val doneX = screenWidthPx + swipeOffset.value.coerceIn(-2f * actionPx, 0f)
        val waitX = screenWidthPx + actionPx + swipeOffset.value.coerceIn(-2f * actionPx, 0f)

        // Content always fills full width
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight)
                .background(MaterialTheme.colorScheme.surface)
                .pointerInput(actionPx) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitPointerEvent()
                            val startPos = down.changes.first().position
                            var swipeActive = false

                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.first()
                                val dx = change.position.x - startPos.x
                                val dy = change.position.y - startPos.y

                                if (!swipeActive) {
                                    if (abs(dx) > abs(dy) && abs(dx) > 8f) {
                                        swipeActive = true
                                    } else if (abs(dy) > abs(dx) && abs(dy) > 8f) {
                                        break
                                    } else {
                                        continue
                                    }
                                }

                                val target = dx.coerceIn(-2f * actionPx, actionPx)
                                scope.launch { swipeOffset.snapTo(target) }
                                change.consume()
                            } while (event.changes.any { it.pressed })

                            if (!swipeActive) continue

                            val finalOffset = swipeOffset.value
                            when {
                                finalOffset >= actionPx * 0.3f -> snapTo(actionPx)
                                finalOffset <= -actionPx * 1.3f -> snapTo(-2f * actionPx)
                                finalOffset <= -actionPx * 0.3f -> snapTo(-actionPx)
                                else -> snapTo(0f)
                            }
                        }
                    }
                },
        ) {
            content()
        }

        // Delete button — slides over content from left
        Box(
            modifier = Modifier
                .width(actionWidth)
                .height(rowHeight)
                .offset { IntOffset(deleteX.roundToInt(), 0) }
                .background(Color(0xFFE53935))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onDelete(); snapTo(0f) },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text("Delete", color = Color.White, style = MaterialTheme.typography.bodyMedium)
        }

        // Done button — slides over content from right
        Box(
            modifier = Modifier
                .width(actionWidth)
                .height(rowHeight)
                .offset { IntOffset(doneX.roundToInt(), 0) }
                .background(Color(0xFF43A047))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onDone(); snapTo(0f) },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text("Done", color = Color.White, style = MaterialTheme.typography.bodyMedium)
        }

        // Wait button — slides over content from right
        Box(
            modifier = Modifier
                .width(actionWidth)
                .height(rowHeight)
                .offset { IntOffset(waitX.roundToInt(), 0) }
                .background(Color(0xFFF9A825))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onWaiting(); snapTo(0f) },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text("Wait", color = Color.White, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
