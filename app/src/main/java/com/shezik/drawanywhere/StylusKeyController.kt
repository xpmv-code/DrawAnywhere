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

import android.util.Log
import com.shezik.drawanywhere.model.StrokeModifier

/**
 * State machine behind stylus side-button handling, shared by every key
 * transport:
 *
 *  - [KeyFilterService] (accessibility key filter — works on stock Android
 *    where third-party key filtering is allowed),
 *  - [StylusKeyReceiver] (broadcast relay from an external system-server
 *    hook, e.g. an LSPosed module — needed on OEM builds such as HyperOS
 *    that only grant key filtering to privileged apps).
 *
 * Behavior while the DrawAnywhere canvas is visible and not in passthrough
 * mode (otherwise keys pass through untouched):
 *
 *  - Key **held** (either button): strokes drawn are eraser strokes
 *    (surfaces as [StrokeModifier.PrimaryButton] to the touch pipeline).
 *  - Upper button **tap** (≤ [TAP_WINDOW_MS], no stroke started during the
 *    hold): toggle current tool ↔ StrokeEraser (restores the saved tool).
 *  - Lower button **tap**: cycle through all tools in toolbar order.
 */
object StylusKeyController {
    private const val TAG = "KeyFilter"

    /** Press→release window for a hold to count as a tap. Measured pen taps
     *  on-device run 16–100ms with a slow user reaching ~390ms; holds while
     *  drawing run 600ms+. 450ms keeps real taps out of the miss zone. */
    const val TAP_WINDOW_MS = 450L

    private class HoldState {
        var active: Boolean = false
        var downAtMs: Long = 0L
        var strokesAtDown: Int = 0
    }

    private val upperHold = HoldState() // PAGE_UP
    private val lowerHold = HoldState() // PAGE_DOWN

    /**
     * Current hold modifier for the stroke pipeline: PrimaryButton while any
     * stylus key is held and interception is active, None otherwise.
     */
    fun activeStrokeModifier(): StrokeModifier {
        if (!isInterceptActive()) return StrokeModifier.None
        return if (upperHold.active || lowerHold.active)
            StrokeModifier.PrimaryButton else StrokeModifier.None
    }

    fun isInterceptActive(): Boolean {
        if (!MainService.isRunning) return false
        val viewModel = MainService.activeViewModel ?: return false
        val state = viewModel.uiState.value
        return state.canvasVisible && !state.canvasPassthrough
    }

    fun onKeyDown(keyCode: Int, downAtMs: Long): Boolean {
        val hold = holdFor(keyCode) ?: return false
        if (!isInterceptActive()) {
            resetHolds()
            return false
        }
        val viewModel = MainService.activeViewModel ?: return false
        if (!hold.active) {
            hold.active = true
            hold.downAtMs = downAtMs
            hold.strokesAtDown = viewModel.strokesStartedCount
        }
        // Key repeats keep the hold as-is.
        return true
    }

    fun onKeyUp(keyCode: Int, upAtMs: Long): Boolean {
        val hold = holdFor(keyCode) ?: return false
        if (!hold.active) return false // stray UP without our DOWN — don't consume
        hold.active = false

        val viewModel = MainService.activeViewModel ?: return false
        val noStrokeDuringHold = viewModel.strokesStartedCount == hold.strokesAtDown
        val isTap = upAtMs - hold.downAtMs <= TAP_WINDOW_MS && noStrokeDuringHold
        Log.d(TAG, "endHold upper=${keyCode == android.view.KeyEvent.KEYCODE_PAGE_UP} " +
            "dt=${upAtMs - hold.downAtMs}ms strokeStarted=${!noStrokeDuringHold} tap=$isTap")
        if (isTap) {
            if (keyCode == android.view.KeyEvent.KEYCODE_PAGE_UP) {
                viewModel.toggleEraserKeyTool()
            } else {
                viewModel.cyclePenType()
            }
        }
        return true
    }

    fun resetHolds() {
        upperHold.active = false
        lowerHold.active = false
    }

    private fun holdFor(keyCode: Int): HoldState? = when (keyCode) {
        android.view.KeyEvent.KEYCODE_PAGE_UP -> upperHold
        android.view.KeyEvent.KEYCODE_PAGE_DOWN -> lowerHold
        else -> null
    }
}
