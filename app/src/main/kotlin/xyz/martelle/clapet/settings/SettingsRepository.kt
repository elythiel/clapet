package xyz.martelle.clapet.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.math.max

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Persists the launcher's appearance. Every screen observes the same flows, so changes apply live. */
class SettingsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val dataStore = appContext.settingsDataStore

    val wallpaperFile: File = File(appContext.filesDir, WALLPAPER_FILE_NAME)

    val accent: Flow<AccentSettings> = dataStore.data
        .map { prefs ->
            AccentSettings(
                mode = prefs[Keys.accentMode].toEnum(AccentSettings.Default.mode),
                color = prefs[Keys.accentColor] ?: AccentSettings.Default.color,
            )
        }
        .distinctUntilChanged()

    val wallpaper: Flow<WallpaperSettings> = dataStore.data
        .map { prefs ->
            WallpaperSettings(
                source = prefs[Keys.wallpaperSource].toEnum(WallpaperSettings.Default.source),
                dimPercent = prefs[Keys.wallpaperDim] ?: WallpaperSettings.Default.dimPercent,
                imageRevision = prefs[Keys.wallpaperRevision] ?: WallpaperSettings.Default.imageRevision,
            )
        }
        .distinctUntilChanged()

    val pseudoHome: Flow<PseudoHomeSettings> = dataStore.data
        .map { prefs ->
            PseudoHomeSettings(
                enabled = prefs[Keys.pseudoHomeEnabled] ?: PseudoHomeSettings.Default.enabled,
                pausedUntilMillis = prefs[Keys.pseudoHomePausedUntil] ?: PseudoHomeSettings.Default.pausedUntilMillis,
            )
        }
        .distinctUntilChanged()

    suspend fun setPseudoHomeEnabled(enabled: Boolean) {
        dataStore.edit {
            it[Keys.pseudoHomeEnabled] = enabled
            // Switching the pseudo-home back on also ends any pause.
            it[Keys.pseudoHomePausedUntil] = 0L
        }
    }

    suspend fun pausePseudoHome(untilMillis: Long) {
        dataStore.edit { it[Keys.pseudoHomePausedUntil] = untilMillis }
    }

    suspend fun setAccent(mode: AccentMode, color: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.accentMode] = mode.name
            prefs[Keys.accentColor] = color
        }
    }

    suspend fun setWallpaperSource(source: WallpaperSource) {
        dataStore.edit { it[Keys.wallpaperSource] = source.name }
    }

    suspend fun setWallpaperDim(percent: Int) {
        dataStore.edit { it[Keys.wallpaperDim] = percent.coerceIn(0, WallpaperSettings.MAX_DIM_PERCENT) }
    }

    /**
     * Copies the picked image into app storage, so it survives the photo picker's
     * temporary read grant. Returns false when the image cannot be decoded.
     */
    suspend fun importWallpaper(uri: Uri): Boolean {
        val saved = withContext(Dispatchers.IO) {
            try {
                val source = ImageDecoder.createSource(appContext.contentResolver, uri)
                val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val longestSide = max(info.size.width, info.size.height)
                    if (longestSide > MAX_WALLPAPER_SIDE) {
                        val scale = MAX_WALLPAPER_SIDE.toFloat() / longestSide
                        decoder.setTargetSize(
                            (info.size.width * scale).toInt(),
                            (info.size.height * scale).toInt(),
                        )
                    }
                }
                val tmp = File(wallpaperFile.parentFile, "$WALLPAPER_FILE_NAME.tmp")
                tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 90, it) }
                tmp.renameTo(wallpaperFile)
            } catch (e: IOException) {
                false
            } catch (e: SecurityException) {
                false
            }
        }
        if (saved) {
            dataStore.edit {
                it[Keys.wallpaperSource] = WallpaperSource.Gallery.name
                // A new revision tells the UI to reload the file, which keeps the same path.
                it[Keys.wallpaperRevision] = System.currentTimeMillis()
            }
        }
        return saved
    }

    private object Keys {
        val accentMode = stringPreferencesKey("accent_mode")
        val accentColor = intPreferencesKey("accent_color")
        val wallpaperSource = stringPreferencesKey("wallpaper_source")
        val wallpaperDim = intPreferencesKey("wallpaper_dim")
        val wallpaperRevision = longPreferencesKey("wallpaper_revision")
        val pseudoHomeEnabled = booleanPreferencesKey("pseudo_home_enabled")
        val pseudoHomePausedUntil = longPreferencesKey("pseudo_home_paused_until")
    }

    private companion object {
        const val WALLPAPER_FILE_NAME = "wallpaper.webp"
        const val MAX_WALLPAPER_SIDE = 2048
    }
}

private inline fun <reified T : Enum<T>> String?.toEnum(default: T): T =
    enumValues<T>().firstOrNull { it.name == this } ?: default
