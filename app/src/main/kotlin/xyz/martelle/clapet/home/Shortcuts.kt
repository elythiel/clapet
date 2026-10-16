package xyz.martelle.clapet.home

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/** An app the home screen can open. Label and icon come from the system (HyperOS-themed icon). */
@Immutable
data class Shortcut(val packageName: String, val label: String, val icon: ImageBitmap)

/**
 * Resolves each query to the app that handles it, once per app. Blocking: call off the main thread.
 * Placeholder until the favorites of milestone 5.
 */
fun loadShortcuts(context: Context, queries: List<Intent>): List<Shortcut> {
    val pm = context.packageManager
    return queries
        .mapNotNull { pm.resolveActivity(it, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName }
        .distinct()
        .filter { pm.getLaunchIntentForPackage(it) != null }
        .map { packageName ->
            val info = pm.getApplicationInfo(packageName, 0)
            Shortcut(
                packageName = packageName,
                label = pm.getApplicationLabel(info).toString(),
                icon = pm.getApplicationIcon(info).toBitmap().asImageBitmap(),
            )
        }
}

fun Context.launch(shortcut: Shortcut) {
    val intent = packageManager.getLaunchIntentForPackage(shortcut.packageName) ?: return
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
