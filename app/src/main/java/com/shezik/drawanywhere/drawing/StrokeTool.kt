package com.shezik.drawanywhere.drawing

import androidx.compose.ui.geometry.Offset

/**
 * Lifecycle of a single stroke gesture: start → move (zero or more) → finish.
 *
 * Tools are instantiated per-gesture by [com.shezik.drawanywhere.model.PenType.createTool].
 * The same [ToolContext] is shared across all tools.
 */
interface StrokeTool {
    /** @param pressure Raw stylus pressure (0..1); tools that don't use it may ignore it. */
    fun onStart(point: Offset, pressure: Float = 1f)
    fun onMove(point: Offset, pressure: Float = 1f)
    fun onFinish()
}
