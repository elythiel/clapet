package xyz.martelle.clapet

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import xyz.martelle.clapet.apps.InstalledApps
import xyz.martelle.clapet.notifications.NotificationHub
import xyz.martelle.clapet.settings.SettingsRepository
import xyz.martelle.clapet.unlock.CoverUnlockManager

class ClapetApplication : Application() {

    /** Work that must outlive any screen (allow list replays). Lives as long as the process. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings: SettingsRepository by lazy { SettingsRepository(this) }
    val unlock: CoverUnlockManager by lazy { CoverUnlockManager(this, settings, appScope) }
    val installedApps: InstalledApps by lazy { InstalledApps(this, appScope) }
    val notifications: NotificationHub by lazy { NotificationHub(this) }

    override fun onCreate() {
        super.onCreate()
        unlock.watchPackageChanges()
    }
}

val Context.settingsRepository: SettingsRepository
    get() = (applicationContext as ClapetApplication).settings

val Context.installedApps: InstalledApps
    get() = (applicationContext as ClapetApplication).installedApps

val Context.notificationHub: NotificationHub
    get() = (applicationContext as ClapetApplication).notifications

val Context.coverUnlock: CoverUnlockManager
    get() = (applicationContext as ClapetApplication).unlock
