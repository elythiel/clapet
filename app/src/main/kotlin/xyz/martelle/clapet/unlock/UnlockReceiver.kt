package xyz.martelle.clapet.unlock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import xyz.martelle.clapet.ClapetApplication

/**
 * Replays the allow list after a reboot (it lives in memory only) and after Clapet itself
 * is updated. Both are protected broadcasts: only the system can send them.
 */
class UnlockReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val pending = goAsync()
                (context.applicationContext as ClapetApplication).unlock
                    .requestUnlock()
                    .invokeOnCompletion { pending.finish() }
            }
        }
    }
}
