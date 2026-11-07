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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receives stylus side-button events relayed by an external system-server
 * hook (LSPosed module) and feeds them into [StylusKeyController]. The hook
 * drops PAGE_UP/PAGE_DOWN from the stylus while the DrawAnywhere canvas is
 * visible and broadcasts them here instead.
 */
class StylusKeyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_STYLUS_KEY) return
        val keyCode = intent.getIntExtra(EXTRA_KEYCODE, -1)
        val keyAction = intent.getIntExtra(EXTRA_ACTION, -1)
        val eventTime = intent.getLongExtra(EXTRA_EVENT_TIME, 0L)
        when (keyAction) {
            android.view.KeyEvent.ACTION_DOWN -> StylusKeyController.onKeyDown(keyCode, eventTime)
            android.view.KeyEvent.ACTION_UP -> StylusKeyController.onKeyUp(keyCode, eventTime)
        }
    }

    companion object {
        const val ACTION_STYLUS_KEY = "com.shezik.drawanywhere.STYLUS_KEY"
        const val EXTRA_KEYCODE = "keycode"
        const val EXTRA_ACTION = "action"
        const val EXTRA_EVENT_TIME = "eventTime"
    }
}
