package xyz.martelle.clapet.cover

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.runtime.Composable

/**
 * The cover-screen status bar. HyperOS draws it (114 px on the tested phone) but reports it
 * hidden to apps, so `WindowInsets.statusBars` is 0 there: use its size regardless of visibility.
 */
@OptIn(ExperimentalLayoutApi::class)
val WindowInsets.Companion.coverStatusBar: WindowInsets
    @Composable get() = statusBarsIgnoringVisibility.only(WindowInsetsSides.Top)
