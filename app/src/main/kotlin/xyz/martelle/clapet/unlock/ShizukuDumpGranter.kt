package xyz.martelle.clapet.unlock

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.RemoteException
import kotlinx.coroutines.suspendCancellableCoroutine
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume

/** Grants DUMP once through Shizuku. After that, Clapet needs Shizuku no more, even after a reboot. */
class ShizukuDumpGranter(context: Context) {

    private val appContext = context.applicationContext

    /** Shizuku is installed and its server runs (it does not restart by itself after a reboot). */
    fun isRunning(): Boolean = Shizuku.pingBinder()

    /** Asks Shizuku for access if needed, then grants DUMP. Returns true when DUMP was granted. */
    suspend fun grantDump(): Boolean {
        if (!isRunning()) return false
        if (!hasShizukuPermission() && !requestShizukuPermission()) return false
        return runShellService { it.grantDumpToClapet() == 0 } ?: false
    }

    private fun hasShizukuPermission(): Boolean =
        !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

    private suspend fun requestShizukuPermission(): Boolean = suspendCancellableCoroutine { continuation ->
        val listener = object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                if (requestCode != REQUEST_CODE) return
                Shizuku.removeRequestPermissionResultListener(this)
                continuation.resume(grantResult == PackageManager.PERMISSION_GRANTED)
            }
        }
        Shizuku.addRequestPermissionResultListener(listener)
        continuation.invokeOnCancellation { Shizuku.removeRequestPermissionResultListener(listener) }
        Shizuku.requestPermission(REQUEST_CODE)
    }

    /** Binds the shell user service, runs [block] once, then unbinds and stops it. */
    private suspend fun <T> runShellService(block: (IShellService) -> T): T? = suspendCancellableCoroutine { continuation ->
        val args = Shizuku.UserServiceArgs(ComponentName(appContext, ShellService::class.java))
            .processNameSuffix("shell")
            .debuggable(appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
            .daemon(false)
            .version(SHELL_SERVICE_VERSION)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                val result = try {
                    block(IShellService.Stub.asInterface(binder))
                } catch (e: RemoteException) {
                    null
                }
                Shizuku.unbindUserService(args, this, true)
                if (continuation.isActive) continuation.resume(result)
            }

            override fun onServiceDisconnected(name: ComponentName) {
                if (continuation.isActive) continuation.resume(null)
            }
        }
        continuation.invokeOnCancellation { Shizuku.unbindUserService(args, connection, true) }
        Shizuku.bindUserService(args, connection)
    }

    private companion object {
        const val REQUEST_CODE = 1
        /** Bump when ShellService changes, so Shizuku restarts it instead of reusing an old one. */
        const val SHELL_SERVICE_VERSION = 1
    }
}
