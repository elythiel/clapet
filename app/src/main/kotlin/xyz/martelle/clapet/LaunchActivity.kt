package xyz.martelle.clapet

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import xyz.martelle.clapet.cover.coverGeometry
import xyz.martelle.clapet.home.HomeActivity
import xyz.martelle.clapet.settings.SettingsActivity

/**
 * The single Clapet icon, listed in both app drawers: the cover screen's FlipLauncher
 * and the inner screen. On the cover screen it opens the home, elsewhere the settings.
 * Draws nothing (Theme.NoDisplay) and finishes at once.
 */
// Not a splash screen: a router that shows no UI, so lint's splash check does not apply.
@SuppressLint("CustomSplashScreen")
class LaunchActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target = if (coverGeometry() != null) HomeActivity::class.java else SettingsActivity::class.java
        startActivity(Intent(this, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}
