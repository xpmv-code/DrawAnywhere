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
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Accessibility key filter: the in-app transport for stylus side-button
 * events on devices that allow third-party key filtering (stock Android).
 *
 * All decision logic lives in [StylusKeyController] so that alternate
 * transports (e.g. [StylusKeyReceiver], fed by an external system-server
 * hook on OEM builds like HyperOS where key filtering is restricted to
 * privileged apps) share the exact same behavior.
 */
class KeyFilterService : AccessibilityService() {

    companion object {
        private const val TAG = "KeyFilter"

        private var activeService: KeyFilterService? = null

        /**
         * Current hold modifier for the stroke pipeline. Prefers the
         * accessibility transport when connected; falls back to the
         * controller state (broadcast transport).
         */
        fun activeStrokeModifier() = StylusKeyController.activeStrokeModifier()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Some OEM builds ignore XML accessibilityFlags; make key filtering explicit.
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            notificationTimeout = 100
        }
        setServiceInfo(info)
        activeService = this
        Log.i(TAG, "connected: flags=${serviceInfo.flags} caps=${serviceInfo.capabilities} " +
            "eventTypes=${serviceInfo.eventTypes}")
    }

    override fun onDestroy() {
        if (activeService === this) {
            activeService = null
            StylusKeyController.resetHolds()
        }
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onKeyEvent(event: KeyEvent): Boolean {
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> StylusKeyController.onKeyDown(event.keyCode, event.eventTime)
            KeyEvent.ACTION_UP -> StylusKeyController.onKeyUp(event.keyCode, event.eventTime)
            else -> false
        }
    }
}
