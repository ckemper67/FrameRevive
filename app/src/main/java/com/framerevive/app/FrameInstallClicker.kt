package com.framerevive.app

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Confirms Android's installation prompt for FrameRevive itself, so an owner can update the frame from the
 * controls page without a pointer on the touch-less panel. It only acts on the system installer window that
 * mentions FrameRevive, and it does nothing for any other package or dialog.
 */
class FrameInstallClicker : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val root = rootInActiveWindow ?: return
        if (root.packageName != INSTALLER) return
        val labels = StringBuilder()
        collect(root, labels)
        val text = labels.toString()
        // Never confirm an installation that is not ours.
        if (!text.contains("FrameRevive", ignoreCase = true)) return
        val button = findButton(root) ?: return
        if (button.isEnabled && button.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            getSharedPreferences("frame-settings", MODE_PRIVATE).edit()
                .putString("updateResult", "Android's install prompt was confirmed automatically.").apply()
        }
    }

    private fun collect(node: AccessibilityNodeInfo, out: StringBuilder) {
        node.text?.let { out.append(it).append('\n') }
        for (i in 0 until node.childCount) node.getChild(i)?.let { collect(it, out) }
    }

    private fun findButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val label = node.text?.toString()?.trim()?.uppercase()
        if (label == "INSTALL" || label == "UPDATE" || label == "CONTINUE") return node
        for (i in 0 until node.childCount) node.getChild(i)?.let { child -> findButton(child)?.let { return it } }
        return null
    }

    override fun onInterrupt() = Unit

    companion object { const val INSTALLER = "com.android.packageinstaller" }
}
