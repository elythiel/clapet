package xyz.martelle.clapet.health

import android.Manifest
import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import xyz.martelle.clapet.notifications.ClapetNotificationListener
import xyz.martelle.clapet.pseudohome.CoverHomeService
import xyz.martelle.clapet.unlock.CoverUnlocker

/** Reads each setting at call time: they all change outside the app, so nothing is cached. */
class HealthChecker(context: Context) {

    private val appContext = context.applicationContext
    private val packageName = appContext.packageName

    fun read(): HealthReport = HealthReport(
        HealthItem.entries.associateWith { item ->
            when (item) {
                HealthItem.CoverHome -> isCoverHomeServiceEnabled()
                HealthItem.CoverUnlock -> CoverUnlocker(appContext).hasDumpPermission()
                HealthItem.AutoStart -> isAutoStartAllowed()
                HealthItem.Battery -> isBatteryUnrestricted()
                HealthItem.Notifications -> appContext.getSystemService(NotificationManager::class.java)
                    .isNotificationListenerAccessGranted(ComponentName(appContext, ClapetNotificationListener::class.java))
                HealthItem.Calendar ->
                    appContext.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
            }
        },
    )

    private fun isCoverHomeServiceEnabled(): Boolean {
        val ours = ComponentName(appContext, CoverHomeService::class.java)
        val enabled = Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return enabled.orEmpty().split(':').any { ComponentName.unflattenFromString(it) == ours }
    }

    /**
     * Xiaomi operation 10008. The int-op overload of checkOpNoThrow is hidden: reached by
     * reflection. If HyperOS changes it, the row reads as set rather than raising a false alarm.
     */
    @SuppressLint("DiscouragedPrivateApi")
    private fun isAutoStartAllowed(): Boolean = try {
        val appOps = appContext.getSystemService(AppOpsManager::class.java)
        val mode = AppOpsManager::class.java
            .getMethod("checkOpNoThrow", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java)
            .invoke(appOps, MIUI_OP_AUTO_START, Process.myUid(), packageName) as Int
        mode == AppOpsManager.MODE_ALLOWED
    } catch (e: ReflectiveOperationException) {
        true
    }

    /** Android's battery optimisation and, when HyperOS has it, its own "No restrictions" list. */
    private fun isBatteryUnrestricted(): Boolean {
        val android = appContext.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        val xiaomi = Settings.System.getString(appContext.contentResolver, MILLET_NO_RESTRICT_APP)
        return android && (xiaomi == null || XiaomiSettings.commaListContains(xiaomi, packageName))
    }

    private companion object {
        const val MIUI_OP_AUTO_START = 10008
        const val MILLET_NO_RESTRICT_APP = "MILLET_NO_RESTRICT_APP"
    }
}
