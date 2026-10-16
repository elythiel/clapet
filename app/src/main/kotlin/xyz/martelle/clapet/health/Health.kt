package xyz.martelle.clapet.health

import androidx.compose.runtime.Immutable
import org.json.JSONArray
import org.json.JSONException

/** What keeps Clapet working on HyperOS. Order is the display order. */
enum class HealthItem(val required: Boolean) {
    /** The pseudo-home accessibility service. */
    CoverHome(required = true),

    /** DUMP, for the cover-screen allow list (milestone 3). */
    CoverUnlock(required = true),

    /** Xiaomi "Autostart": without it, HyperOS drops the boot broadcast. */
    AutoStart(required = true),

    /** No battery restriction, on both the Android and the Xiaomi side. */
    Battery(required = true),

    /** Locking Clapet in recents protects it from "clear all"; nice to have. */
    RecentsLock(required = false),
}

@Immutable
data class HealthReport(val ok: Map<HealthItem, Boolean>) {
    val total: Int get() = ok.size
    val okCount: Int get() = ok.count { it.value }

    /** The first required setting that is off: the one the home dot leads to. */
    val firstBroken: HealthItem? get() = HealthItem.entries.firstOrNull { it.required && ok[it] == false }
}

/** Formats of the undocumented Xiaomi settings Clapet reads. */
object XiaomiSettings {

    /** `locked_apps`: `[{"u":0,"pkgs":["a","b"]},{"u":-100,"pkgs":[…]}]`, one entry per user. */
    fun lockedAppsContain(json: String?, userId: Int, packageName: String): Boolean {
        if (json.isNullOrBlank()) return false
        return try {
            val users = JSONArray(json)
            (0 until users.length())
                .map { users.getJSONObject(it) }
                .filter { it.optInt("u", Int.MIN_VALUE) == userId }
                .any { entry ->
                    val packages = entry.optJSONArray("pkgs") ?: return@any false
                    (0 until packages.length()).any { packages.optString(it) == packageName }
                }
        } catch (e: JSONException) {
            false
        }
    }

    /** `MILLET_NO_RESTRICT_APP`: comma-separated package names. */
    fun commaListContains(list: String?, packageName: String): Boolean =
        list.orEmpty().split(',').any { it.trim() == packageName }
}
