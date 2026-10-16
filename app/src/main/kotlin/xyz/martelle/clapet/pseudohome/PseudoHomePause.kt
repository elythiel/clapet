package xyz.martelle.clapet.pseudohome

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import xyz.martelle.clapet.settingsRepository

/** How long a pause leaves FlipLauncher alone. */
private const val PAUSE_DURATION_MS = 5 * 60 * 1000L

/**
 * Lets the user reach FlipLauncher (to empty it, for instance) without switching the service off.
 * Asks for the system home rather than moving Clapet back, which would reveal the last app
 * instead: on the cover screen HyperOS routes HOME to FlipLauncher, and the paused service lets it be.
 */
fun ComponentActivity.pausePseudoHomeAndStepAside() {
    lifecycleScope.launch {
        settingsRepository.pausePseudoHome(untilMillis = System.currentTimeMillis() + PAUSE_DURATION_MS)
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
