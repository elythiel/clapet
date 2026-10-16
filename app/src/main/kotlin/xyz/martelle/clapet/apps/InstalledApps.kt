package xyz.martelle.clapet.apps

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.Collator

/** One launcher icon. [key] (the flattened component) identifies it in the favorites. */
@Immutable
data class AppEntry(
    val key: String,
    val label: String,
    val letter: Char,
    val icon: ImageBitmap,
    val component: ComponentName,
)

/**
 * Every launchable app of the user, sorted by label, kept up to date when apps are
 * installed, removed or changed. Icons come from PackageManager, already themed by HyperOS.
 * Owned by the application: loading icons once serves every screen.
 */
class InstalledApps(context: Context, private val scope: CoroutineScope) {

    private val appContext = context.applicationContext
    private val launcherApps = appContext.getSystemService(LauncherApps::class.java)
    private val user = Process.myUserHandle()
    private val mutex = Mutex()
    private val _apps = MutableStateFlow<List<AppEntry>?>(null)
    private var watching = false

    /** Null until the first load. */
    val apps: StateFlow<List<AppEntry>?> = _apps.asStateFlow()

    /** Starts loading and watching; safe to call from every screen that needs the list. */
    fun start() {
        if (watching) return
        watching = true
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        reload()
    }

    private fun reload() {
        scope.launch(Dispatchers.IO) {
            mutex.withLock { _apps.value = load() }
        }
    }

    private fun load(): List<AppEntry> {
        val pm = appContext.packageManager
        val collator = Collator.getInstance()
        return launcherApps.getActivityList(null, user)
            .filter { it.componentName.packageName != appContext.packageName }
            .map { info ->
                val label = info.label.toString()
                AppEntry(
                    key = info.componentName.flattenToString(),
                    label = label,
                    letter = AlphabetIndex.letterOf(label),
                    // PackageManager applies the HyperOS icon theme (frame included);
                    // LauncherActivityInfo.getIcon returns the raw, unthemed icon.
                    icon = pm.getActivityIcon(info.componentName).toBitmap().asImageBitmap(),
                    component = info.componentName,
                )
            }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) = reload()
        override fun onPackageRemoved(packageName: String, user: UserHandle) = reload()
        override fun onPackageChanged(packageName: String, user: UserHandle) = reload()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
    }

    fun launch(app: AppEntry) {
        launcherApps.startMainActivity(app.component, user, null, null)
    }

    fun openAppInfo(app: AppEntry) {
        launcherApps.startAppDetailsActivity(app.component, user, null, null)
    }
}
