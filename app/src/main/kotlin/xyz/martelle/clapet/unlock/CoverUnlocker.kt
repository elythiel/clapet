package xyz.martelle.clapet.unlock

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.RemoteException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

sealed interface UnlockResult {
    data class Done(val appCount: Int) : UnlockResult
    data object MissingPermission : UnlockResult
    data class Failed(val reason: String) : UnlockResult
}

/**
 * Lets every installed app start on the cover screen. HyperOS keeps an allow list in
 * WindowManagerService, set through its dump command (the same path as
 * `adb shell dumpsys window -setForceDisplayCompatMode <pkg1:pkg2:…> allowstart`).
 * The list lives in memory only: it is lost at every reboot and must be replayed.
 *
 * Calling a service's dump requires the DUMP permission, granted once through Shizuku or
 * adb (it is a development permission, and stays granted across reboots).
 */
class CoverUnlocker(context: Context) {

    private val appContext = context.applicationContext

    fun hasDumpPermission(): Boolean =
        appContext.checkSelfPermission(Manifest.permission.DUMP) == PackageManager.PERMISSION_GRANTED

    /** Blocking (package scan and binder call): run it off the main thread. */
    fun unlock(): UnlockResult {
        if (!hasDumpPermission()) return UnlockResult.MissingPermission
        val packages = installedPackages()
        val window = windowService() ?: return UnlockResult.Failed("window service unavailable")
        val output = File(appContext.cacheDir, "unlock-dump.txt")
        return try {
            FileOutputStream(output).use { out ->
                window.dump(out.fd, arrayOf(SET_ALLOW_LIST, packages.joinToString(":"), ALLOW_START))
            }
            // Without DUMP, WindowManagerService answers in the dump text instead of throwing.
            if (output.readText().contains("Permission Denial")) {
                UnlockResult.MissingPermission
            } else {
                UnlockResult.Done(packages.size)
            }
        } catch (e: RemoteException) {
            UnlockResult.Failed(e.toString())
        } catch (e: IOException) {
            UnlockResult.Failed(e.toString())
        } finally {
            output.delete()
        }
    }

    /** Every package, launcher icon or not: system screens apps open (photo picker, permission prompts) need it too. */
    private fun installedPackages(): List<String> =
        appContext.packageManager
            .getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
            .map { it.packageName }
            .sorted()

    /** ServiceManager is not public API; it is reachable by reflection (greylisted) and has no public equivalent. */
    @SuppressLint("PrivateApi")
    private fun windowService(): IBinder? = try {
        Class.forName("android.os.ServiceManager")
            .getMethod("getService", String::class.java)
            .invoke(null, Context.WINDOW_SERVICE) as IBinder?
    } catch (e: ReflectiveOperationException) {
        null
    }

    private companion object {
        const val SET_ALLOW_LIST = "-setForceDisplayCompatMode"
        const val ALLOW_START = "allowstart"
    }
}
