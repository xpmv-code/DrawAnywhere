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

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.util.Log

/**
 * Answers a single query for the external key relay (system-server hook):
 * should stylus PAGE_UP/PAGE_DOWN key events be intercepted right now?
 *
 * Only exposes one boolean about the overlay canvas state; no user data.
 */
class KeyStateProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != METHOD_INTERCEPT_ACTIVE) return null
        val viewModel = MainService.activeViewModel
        val state = viewModel?.uiState?.value
        val active = MainService.isRunning && viewModel != null &&
            state?.canvasVisible == true && state.canvasPassthrough.not()
        Log.d(TAG, "intercept_active: running=${MainService.isRunning} vm=${viewModel != null} " +
            "visible=${state?.canvasVisible} passthrough=${state?.canvasPassthrough} -> $active")
        return Bundle().apply {
            putBoolean(KEY_ACTIVE, active)
            putBoolean("running", MainService.isRunning)
            putBoolean("has_vm", viewModel != null)
            putBoolean("visible", state?.canvasVisible == true)
            putBoolean("passthrough", state?.canvasPassthrough == true)
        }
    }

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri, values: ContentValues?, selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    companion object {
        private const val TAG = "KeyFilter"
        const val AUTHORITY = "com.shezik.drawanywhere.keystate"
        const val METHOD_INTERCEPT_ACTIVE = "intercept_active"
        const val KEY_ACTIVE = "active"
    }
}
