package xyz.martelle.clapet.health

import androidx.compose.runtime.Immutable

/** How the permissions page sorts its rows. */
enum class HealthGroup {
    /** What HyperOS needs not to close Clapet or to let it work at all. */
    KeepRunning,

    /** What a feature needs; without it, only that feature is missing. */
    Features,
}

/** The system accesses Clapet relies on. Order is the display order, within each group. */
enum class HealthItem(val required: Boolean, val group: HealthGroup = HealthGroup.KeepRunning) {
    /** The pseudo-home accessibility service. */
    CoverHome(required = true),

    /** DUMP, for the cover-screen allow list (milestone 3). */
    CoverUnlock(required = true),

    /** Xiaomi "Autostart": without it, HyperOS drops the boot broadcast. */
    AutoStart(required = true),

    /** No battery restriction, on both the Android and the Xiaomi side. */
    Battery(required = true),

    /** READ_CALENDAR, for the upcoming events of the header; without it, date and alarm only. */
    Calendar(required = false, group = HealthGroup.Features),

    /** Notification access: previews under the apps and the cover player (milestone 7). */
    Notifications(required = false, group = HealthGroup.Features),
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

    /** `MILLET_NO_RESTRICT_APP`: comma-separated package names. */
    fun commaListContains(list: String?, packageName: String): Boolean =
        list.orEmpty().split(',').any { it.trim() == packageName }
}
