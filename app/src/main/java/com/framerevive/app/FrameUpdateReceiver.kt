package com.framerevive.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller

/** Result of a silent, device-owner install session; the controls page shows it under Debug. */
class FrameUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
        val text = if (status == PackageInstaller.STATUS_SUCCESS) "Update installed without a prompt. Reopen the controls link."
            else "Silent update failed (status $status) $message".trim()
        context.getSharedPreferences("frame-settings", Context.MODE_PRIVATE).edit().putString("updateResult", text).apply()
    }
}
