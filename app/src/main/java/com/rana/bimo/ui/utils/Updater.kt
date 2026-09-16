package com.rana.bimo.ui.utils

import android.content.Context
import android.util.Log

/**
 * Update checker - currently disabled for BIMO.
 * TODO: Point to BIMO release API once a GitHub repository is set up.
 */
object Updater {
    private val TAG = "BimoUpdater"
    var lastCheckTime = -1L
        private set

    suspend fun tryCheckUpdate(context: Context, force: Boolean = false): String? {
        // Update checking is disabled for BIMO until a release repository is configured
        Log.d(TAG, "Update checker disabled for BIMO")
        return null
    }
}