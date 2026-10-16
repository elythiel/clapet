package xyz.martelle.clapet.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import xyz.martelle.clapet.R
import xyz.martelle.clapet.health.HealthGroup
import xyz.martelle.clapet.health.HealthItem
import xyz.martelle.clapet.health.HealthReport
import xyz.martelle.clapet.theme.LocalClapetColors

@get:StringRes
val HealthItem.label: Int
    get() = when (this) {
        HealthItem.CoverHome -> R.string.health_cover_home
        HealthItem.CoverUnlock -> R.string.health_cover_unlock
        HealthItem.AutoStart -> R.string.health_auto_start
        HealthItem.Battery -> R.string.health_battery
        HealthItem.Calendar -> R.string.health_calendar
        HealthItem.Notifications -> R.string.health_notifications
    }

@get:StringRes
private val HealthItem.hint: Int
    get() = when (this) {
        HealthItem.CoverHome -> R.string.health_cover_home_hint
        HealthItem.CoverUnlock -> R.string.health_cover_unlock_hint
        HealthItem.AutoStart -> R.string.health_auto_start_hint
        HealthItem.Battery -> R.string.health_battery_hint
        HealthItem.Calendar -> R.string.health_calendar_hint
        HealthItem.Notifications -> R.string.health_notifications_hint
    }

@Composable
internal fun healthSummary(report: HealthReport): String =
    pluralStringResource(R.plurals.health_summary, report.okCount, report.okCount, report.total)

@Composable
internal fun HealthPage(
    report: HealthReport,
    onFix: (HealthItem) -> Unit,
    onOpenAppInfo: () -> Unit,
    /** Null when the pseudo-home is off: FlipLauncher is already on screen when closed. */
    onOpenXiaomiHome: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
    ) {
        PageTitle(stringResource(R.string.health_title), subtitle = healthSummary(report))
        HealthGroup.entries.forEach { group ->
            val items = HealthItem.entries.filter { it.group == group }
            GroupLabel(group)
            SettingsGroup {
                items.forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    HealthRow(
                        item = item,
                        ok = report.ok[item] == true,
                        onFix = { onFix(item) },
                        onOpenAppInfo = onOpenAppInfo,
                    )
                }
            }
        }
        if (onOpenXiaomiHome != null) {
            Spacer(Modifier.height(16.dp))
            SettingsGroup {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.health_xiaomi_home)) },
                    supportingContent = { Text(stringResource(R.string.health_xiaomi_home_hint)) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                OutlinedButton(
                    onClick = onOpenXiaomiHome,
                    modifier = Modifier.padding(start = 16.dp, bottom = 16.dp),
                ) { Text(stringResource(R.string.health_xiaomi_home_open)) }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun GroupLabel(group: HealthGroup) {
    val (title, hint) = when (group) {
        HealthGroup.KeepRunning -> R.string.health_group_keep_running to R.string.health_subtitle
        HealthGroup.Features -> R.string.health_group_features to R.string.health_group_features_hint
    }
    Column(Modifier.padding(start = 8.dp, top = 16.dp, bottom = 8.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HealthRow(item: HealthItem, ok: Boolean, onFix: () -> Unit, onOpenAppInfo: () -> Unit) {
    val accent = LocalClapetColors.current.accent
    ListItem(
        leadingContent = {
            Icon(
                painterResource(if (ok) R.drawable.ic_health_ok else R.drawable.ic_health_ko),
                contentDescription = stringResource(if (ok) R.string.health_ok else R.string.health_ko),
                // An optional setting left off is not an alert: neutral, not accent.
                tint = when {
                    ok -> Color(0xFF8FD19E)
                    item.required -> accent
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(22.dp),
            )
        },
        headlineContent = { Text(stringResource(item.label)) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(item.hint))
                if (!ok) {
                    // Both are « restricted settings » for an app installed outside a store.
                    val restricted = item == HealthItem.CoverHome || item == HealthItem.Notifications
                    if (restricted) {
                        Text(
                            stringResource(R.string.health_cover_home_restricted),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onFix) { Text(stringResource(R.string.health_fix)) }
                        if (restricted) {
                            TextButton(onClick = onOpenAppInfo) { Text(stringResource(R.string.health_app_info)) }
                        }
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
