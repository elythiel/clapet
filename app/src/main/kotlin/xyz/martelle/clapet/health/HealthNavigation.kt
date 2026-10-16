package xyz.martelle.clapet.health

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import xyz.martelle.clapet.settings.SettingsActivity
import xyz.martelle.clapet.settings.SettingsPage

/**
 * Goes straight to what fixes [item]: Clapet's own cover-screen page for DUMP, the
 * health page for the recents lock (an instruction, no screen), a system screen otherwise.
 */
fun Activity.openHealthFix(item: HealthItem) {
    val shortcut = item.shortcut(this)
    if (shortcut == null) {
        val page = if (item == HealthItem.CoverUnlock) SettingsPage.Unlock else SettingsPage.Health
        startActivity(SettingsActivity.intent(this, page))
        return
    }
    try {
        startActivity(shortcut)
    } catch (e: ActivityNotFoundException) {
        // A Xiaomi screen that resolved but refused to open: app info is always there.
        startActivity(appInfoIntent(this).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
