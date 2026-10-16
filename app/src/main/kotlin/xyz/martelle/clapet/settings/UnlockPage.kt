package xyz.martelle.clapet.settings

import android.content.ClipData
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import xyz.martelle.clapet.R

/** What the cover-screen page shows. Permission and Shizuku state are re-read when the screen resumes. */
@Immutable
data class UnlockUiState(
    val hasDumpPermission: Boolean,
    val shizukuRunning: Boolean,
    val status: UnlockStatus,
    /** Reason of the latest failed attempt in this process, if any. */
    val lastFailure: String?,
)

@Composable
internal fun unlockSummary(state: UnlockUiState): String = when {
    !state.hasDumpPermission -> stringResource(R.string.unlock_missing_permission)
    state.status.lastAtMillis == 0L -> stringResource(R.string.unlock_never)
    else -> pluralStringResource(
        R.plurals.unlock_summary,
        state.status.appCount,
        state.status.appCount,
        formatUnlockTime(state.status.lastAtMillis),
    )
}

@Composable
private fun formatUnlockTime(millis: Long): String {
    val context = LocalContext.current
    return DateUtils.formatDateTime(
        context,
        millis,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
    )
}

@Composable
internal fun UnlockPage(
    state: UnlockUiState,
    onUnlockNow: () -> Unit,
    grantWithShizuku: suspend () -> Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
    ) {
        PageTitle(stringResource(R.string.settings_unlock), subtitle = stringResource(R.string.unlock_subtitle))
        SettingsGroup {
            ListItem(
                headlineContent = {
                    Text(stringResource(if (state.hasDumpPermission) R.string.unlock_done else R.string.unlock_missing_permission))
                },
                supportingContent = {
                    Text(
                        if (state.hasDumpPermission) unlockSummary(state) else stringResource(R.string.unlock_missing_permission_hint),
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
        Spacer(Modifier.height(16.dp))
        if (state.hasDumpPermission) {
            Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onUnlockNow) { Text(stringResource(R.string.unlock_now)) }
                if (state.lastFailure != null) {
                    Text(
                        stringResource(R.string.unlock_failed, state.lastFailure),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        } else {
            GrantPermission(state.shizukuRunning, grantWithShizuku)
        }
    }
}

@Composable
private fun GrantPermission(shizukuRunning: Boolean, grantWithShizuku: suspend () -> Boolean) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    var granting by remember { mutableStateOf(false) }
    var shizukuFailed by remember { mutableStateOf(false) }
    val command = stringResource(R.string.unlock_adb_command)

    Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.unlock_with_shizuku), style = MaterialTheme.typography.titleSmall)
        if (shizukuRunning) {
            Button(
                enabled = !granting,
                onClick = {
                    granting = true
                    scope.launch {
                        shizukuFailed = !grantWithShizuku()
                        granting = false
                    }
                },
            ) { Text(stringResource(R.string.unlock_grant_with_shizuku)) }
            if (shizukuFailed) {
                Text(
                    stringResource(R.string.unlock_shizuku_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        } else {
            Text(
                stringResource(R.string.unlock_shizuku_not_running),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.unlock_with_adb), style = MaterialTheme.typography.titleSmall)
        SettingsGroup {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 12.dp)) {
                Text(
                    command,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                )
                TextButton(onClick = {
                    scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(command, command))) }
                }) { Text(stringResource(R.string.unlock_copy)) }
            }
        }
    }
}
