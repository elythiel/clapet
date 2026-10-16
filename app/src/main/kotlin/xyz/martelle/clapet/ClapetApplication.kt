package xyz.martelle.clapet

import android.app.Application
import android.content.Context
import xyz.martelle.clapet.settings.SettingsRepository

class ClapetApplication : Application() {
    val settings: SettingsRepository by lazy { SettingsRepository(this) }
}

val Context.settingsRepository: SettingsRepository
    get() = (applicationContext as ClapetApplication).settings
