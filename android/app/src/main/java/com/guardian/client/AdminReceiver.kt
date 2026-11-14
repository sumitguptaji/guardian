package com.guardian.client

import android.app.admin.DeviceAdminReceiver

/**
 * Being an *active* device administrator is what stops the app from being
 * casually uninstalled or force-stopped: Android refuses to remove an app while
 * its device admin is on, so it must first be deactivated (guarded by the PIN).
 * The app stays fully visible in the launcher and app list - this is tamper
 * resistance, not concealment.
 */
class AdminReceiver : DeviceAdminReceiver()
