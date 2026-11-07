/*
DrawAnywhere: An Android application that lets you draw on top of other apps.
Copyright (C) 2025-2026 shezik

This program is free software: you can redistribute it and/or modify it under the
terms of the GNU Affero General Public License as published by the Free Software
Foundation, either version 3 of the License, or any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License along
with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.shezik.drawanywhere

import android.accessibilityservice.AccessibilityService
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.shezik.drawanywhere.model.StrokeModifier

/**
 * Filters PAGE_UP / PAGE_DOWN key events emitted by stylus side buttons on
 * devices whose stylus buttons surface as key events instead of
 * MotionEvent.buttonState (e.g. CHUWI H7 on Xiaomi Pad 5 Pro 12.4, whose
 * `himax-stylus.kl` maps scancodes 331/332 to those keys).
 *
 * Behavior (only while the DrawAnywhere canvas is visible and not in
 * passthrough mode — otherwise keys pass through untouched, so other apps
 * such as Mi Notes keep working):
 *
 *  - Key **held** (either button): strokes drawn are eraser strokes
 *    (surfaces as [StrokeModifier.PrimaryButton] to the touch pipeline).
 *  - Upper button **tap** (≤ [TAP_WINDOW_MS], no stroke started during the
 *    hold): toggle current tool ↔ StrokeEraser (restores the saved tool).
 *  - Lower button **tap**: cycle through all tools in toolbar order.
 */
class KeyFilterService : AccessibilityService() {

    companion object {
        /** Press→release window for a hold to count as a tap. */
        private const val TAP_WINDOW_MS = 300L

        private var activeService: KeyFilterService? = null

        /**
         * Current hold modifier for the stroke pipeline: PrimaryButton while any
         * stylus key is held and interception is active, None otherwise.
         */
        fun activeStrokeModifier(): StrokeModifier {
            val service = activeService ?: return StrokeModifier.None
            if (!service.isInterceptActive()) return StrokeModifier.None
            return if (service.upperHold.active || service.lowerHold.active)
                StrokeModifier.PrimaryButton else StrokeModifier.None
        }
    }

    private class HoldState {
        var active: Boolean = false
        var downAtMs: Long = 0L
        var strokesAtDown: Int = 0
    }

    private val upperHold = HoldState() // PAGE_UP
    private val lowerHold = HoldState() // PAGE_DOWN

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeService = this
    }

    override fun onDestroy() {
        if (activeService === this) activeService = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    private fun isInterceptActive(): Boolean {
        if (!MainService.isRunning) return false
        val viewModel = MainService.activeViewModel ?: return false
        val state = viewModel.uiState.value
        return state.canvasVisible && !state.canvasPassthrough
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val isUpper = event.keyCode == KeyEvent.KEYCODE_PAGE_UP
        val isLower = event.keyCode == KeyEvent.KEYCODE_PAGE_DOWN
        if (!isUpper && !isLower) return false

        if (!isInterceptActive()) {
            resetHolds()
            return false // let the key through (e.g. page turn in Mi Notes)
        }

        val viewModel = MainService.activeViewModel ?: return false
        when (event.action) {
            KeyEvent.ACTION_DOWN -> beginHold(
                if (isUpper) upperHold else lowerHold, event.eventTime, viewModel)
            KeyEvent.ACTION_UP -> endHold(
                if (isUpper) upperHold else lowerHold, event.eventTime, viewModel, isUpper)
            else -> return false
        }
        return true // consume: overlay canvas is up, keys belong to us
    }

    private fun beginHold(hold: HoldState, downAtMs: Long, viewModel: DrawViewModel) {
        if (!hold.active) {
            hold.active = true
            hold.downAtMs = downAtMs
            hold.strokesAtDown = viewModel.strokesStartedCount
        }
        // Repeats keep the hold as-is.
    }

    private fun endHold(hold: HoldState, upAtMs: Long, viewModel: DrawViewModel, isUpper: Boolean) {
        if (!hold.active) return // stray UP without our DOWN — don't consume
        hold.active = false

        val noStrokeDuringHold = viewModel.strokesStartedCount == hold.strokesAtDown
        if (upAtMs - hold.downAtMs <= TAP_WINDOW_MS && noStrokeDuringHold) {
            if (isUpper) viewModel.toggleEraserKeyTool() else viewModel.cyclePenType()
        }
    }

    private fun resetHolds() {
        upperHold.active = false
        lowerHold.active = false
    }
}
