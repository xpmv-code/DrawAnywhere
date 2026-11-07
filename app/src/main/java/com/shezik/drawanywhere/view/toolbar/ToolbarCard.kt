package com.shezik.drawanywhere.view.toolbar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun DraggableToolbarCard(
    modifier: Modifier = Modifier,
    haptics: HapticFeedback,
    onPositionChange: (Offset) -> Unit,
    onPositionSaved: () -> Unit,
    onToolbarInteracted: () -> Unit,
    onDragStart: (() -> Unit)? = null,
    onDragPosition: ((Offset) -> Unit)? = null,
    onDragEnd: (() -> Boolean)? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier
            .testTag("toolbar_card")
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent()
                        onToolbarInteracted()
                    }
                }
            }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDragStart?.invoke()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onPositionChange(dragAmount)
                        onDragPosition?.invoke(change.position)
                    },
                    onDragEnd = {
                        val shouldSave = onDragEnd?.invoke() ?: true
                        if (shouldSave) onPositionSaved()
                    },
                    onDragCancel = {
                        onDragEnd?.invoke()
                    }
                )
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        shape = CircleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)
        )
    ) {
        content()
    }
}
