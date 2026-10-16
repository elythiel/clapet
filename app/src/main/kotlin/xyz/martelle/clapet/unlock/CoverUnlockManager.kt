package xyz.martelle.clapet.unlock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import xyz.martelle.clapet.settings.SettingsRepository

/**
 * Replays the cover allow list whenever it may be stale: at boot, when an app is installed
 * or updated, when the home opens, and on demand. Owned by the application (process lifetime).
 */
class CoverUnlockManager(
    context: Context,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val unlocker = CoverUnlocker(appContext)
    private val mutex = Mutex()

    private val _lastResult = MutableStateFlow<UnlockResult?>(null)

    /** Outcome of the latest attempt in this process; null until one has run. */
    val lastResult: StateFlow<UnlockResult?> = _lastResult.asStateFlow()

    fun hasDumpPermission(): Boolean = unlocker.hasDumpPermission()

    /** Attempts never overlap: a request made while one runs waits for it, then replays with fresh data. */
    fun requestUnlock(): Job = scope.launch {
        mutex.withLock {
            val result = withContext(Dispatchers.IO) { unlocker.unlock() }
            if (result is UnlockResult.Done) settings.recordUnlock(System.currentTimeMillis(), result.appCount)
            _lastResult.value = result
        }
    }

    /**
     * PACKAGE_ADDED/REPLACED no longer reach manifest receivers (Android 8+), so they are
     * registered here; the accessibility service keeps the process alive to receive them.
     */
    fun watchPackageChanges() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        appContext.registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    requestUnlock()
                }
            },
            filter,
            Context.RECEIVER_NOT_EXPORTED,
        )
    }
}
