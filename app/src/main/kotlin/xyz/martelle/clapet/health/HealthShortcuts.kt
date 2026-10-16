package xyz.martelle.clapet.health

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri
import xyz.martelle.clapet.notifications.ClapetNotificationListener

/**
 * The system screen that fixes [item], or null when Clapet handles it itself (DUMP, the
 * calendar permission prompt) or when there is no screen (recents lock, explained instead). Falls back to the app info
 * screen when a Xiaomi screen is missing.
 */
fun HealthItem.shortcut(context: Context): Intent? = when (this) {
    HealthItem.CoverHome -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    HealthItem.CoverUnlock, HealthItem.RecentsLock, HealthItem.Calendar -> null
    HealthItem.AutoStart ->
        Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT).orAppInfo(context)
    HealthItem.Battery -> batteryShortcut(context)
    // Straight to Clapet's switch; the list of all listeners otherwise.
    HealthItem.Notifications -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
        .putExtra(
            Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
            ComponentName(context, ClapetNotificationListener::class.java).flattenToString(),
        )
        .takeIf { it.resolveActivity(context.packageManager) != null }
        ?: Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
}

/** "Allow restricted settings" lives behind the ⋮ of this screen (apps installed outside a store). */
fun appInfoIntent(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())

/**
 * Android's standard prompt. On HyperOS it opens Xiaomi's per-app battery screen
 * (PowerDetailActivity) once Android's side is set, which is where "No restrictions" lives.
 */
@SuppressLint("BatteryLife")
private fun batteryShortcut(context: Context): Intent =
    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${context.packageName}".toUri())

private fun Intent.orAppInfo(context: Context): Intent =
    if (resolveActivity(context.packageManager) != null) this else appInfoIntent(context)
