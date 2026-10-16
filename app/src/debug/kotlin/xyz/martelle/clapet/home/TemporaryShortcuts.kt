package xyz.martelle.clapet.home

import android.content.Intent
import androidx.core.net.toUri

/**
 * Debug builds only: a few apps picked by Android category (no package names), to test
 * launching from the cover screen until the favorites of milestone 5 replace them.
 */
val TemporaryShortcutQueries: List<Intent> = listOf(
    Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING),
    Intent(Intent.ACTION_DIAL, "tel:".toUri()),
    Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY),
    Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC),
    Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR),
)
