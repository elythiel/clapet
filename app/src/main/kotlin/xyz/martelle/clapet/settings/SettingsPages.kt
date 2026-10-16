package xyz.martelle.clapet.settings

import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController
import java.util.Date
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import xyz.martelle.clapet.R
import xyz.martelle.clapet.theme.AccentPalette
import xyz.martelle.clapet.theme.LocalClapetColors

@Composable
internal fun SettingsMenu(
    accent: AccentSettings,
    wallpaper: WallpaperSettings,
    pseudoHome: PseudoHomeSettings,
    appInfo: AppInfo,
    onPseudoHomeEnabledChange: (Boolean) -> Unit,
    onOpen: (SettingsPage) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        PageTitle(stringResource(R.string.settings_title))
        SettingsGroup {
            SwitchEntry(
                title = stringResource(R.string.settings_pseudo_home),
                summary = pseudoHomeSummary(pseudoHome),
                checked = pseudoHome.enabled,
                onCheckedChange = onPseudoHomeEnabledChange,
            )
        }
        Spacer(Modifier.height(12.dp))
        SettingsGroup {
            SettingsEntry(
                icon = R.drawable.ic_palette,
                title = stringResource(R.string.settings_accent),
                summary = accentSummary(accent),
                onClick = { onOpen(SettingsPage.Accent) },
            ) {
                Box(Modifier.size(16.dp).background(LocalClapetColors.current.accent, CircleShape))
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            SettingsEntry(
                icon = R.drawable.ic_wallpaper,
                title = stringResource(R.string.settings_wallpaper),
                summary = wallpaperSummary(wallpaper),
                onClick = { onOpen(SettingsPage.Wallpaper) },
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = stringResource(R.string.settings_footer, stringResource(R.string.app_name), appInfo.versionName, appInfo.packageName),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, bottom = 16.dp),
        )
    }
}

/** Recomposes when a pause ends, so the summary never claims a pause that is over. */
@Composable
private fun pseudoHomeSummary(settings: PseudoHomeSettings): String {
    val now by produceState(System.currentTimeMillis(), settings.pausedUntilMillis) {
        val remaining = settings.pausedUntilMillis - System.currentTimeMillis()
        if (remaining > 0) delay(remaining)
        value = System.currentTimeMillis()
    }
    val context = LocalContext.current
    return when {
        !settings.enabled -> stringResource(R.string.pseudo_home_off)
        settings.isActiveAt(now) -> stringResource(R.string.pseudo_home_on)
        else -> stringResource(
            R.string.pseudo_home_paused,
            DateFormat.getTimeFormat(context).format(Date(settings.pausedUntilMillis)),
        )
    }
}

@Composable
private fun accentSummary(accent: AccentSettings): String = when (accent.mode) {
    AccentMode.Auto -> stringResource(R.string.accent_mode_auto)
    AccentMode.Custom -> stringResource(R.string.accent_mode_custom)
    AccentMode.Palette -> {
        val name = AccentPalette.firstOrNull { it.argb == accent.color }?.name
        if (name != null) {
            stringResource(R.string.accent_summary_palette, stringResource(name))
        } else {
            stringResource(R.string.accent_mode_palette)
        }
    }
}

@Composable
private fun wallpaperSummary(wallpaper: WallpaperSettings): String = when (wallpaper.source) {
    WallpaperSource.Black -> stringResource(R.string.wallpaper_black)
    WallpaperSource.Gallery -> stringResource(R.string.wallpaper_summary_gallery, wallpaper.dimPercent)
}

@Composable
internal fun AccentPage(
    accent: AccentSettings,
    onChange: (AccentMode, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
    ) {
        PageTitle(stringResource(R.string.settings_accent), subtitle = stringResource(R.string.accent_subtitle))
        SettingsGroup(selectable = true) {
            AccentMode.entries.forEach { mode ->
                RadioOption(
                    label = stringResource(mode.label),
                    hint = stringResource(mode.hint),
                    selected = accent.mode == mode,
                    onClick = { onChange(mode, accent.color) },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        when (accent.mode) {
            AccentMode.Auto -> Unit
            AccentMode.Palette -> PaletteSwatches(selected = accent.color, onPick = { onChange(AccentMode.Palette, it) })
            AccentMode.Custom -> CustomColorPicker(initial = accent.color, onPick = { onChange(AccentMode.Custom, it) })
        }
        Spacer(Modifier.height(20.dp))
        AccentPreview(Modifier.padding(horizontal = 8.dp, vertical = 8.dp))
    }
}

private val AccentMode.label: Int
    get() = when (this) {
        AccentMode.Auto -> R.string.accent_mode_auto
        AccentMode.Palette -> R.string.accent_mode_palette
        AccentMode.Custom -> R.string.accent_mode_custom
    }

private val AccentMode.hint: Int
    get() = when (this) {
        AccentMode.Auto -> R.string.accent_mode_auto_hint
        AccentMode.Palette -> R.string.accent_mode_palette_hint
        AccentMode.Custom -> R.string.accent_mode_custom_hint
    }

@Composable
private fun PaletteSwatches(selected: Int, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .padding(horizontal = 8.dp)
            .selectableGroup(),
    ) {
        AccentPalette.forEach { swatch ->
            val isSelected = swatch.argb == selected
            val name = stringResource(swatch.name)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onPick(swatch.argb) })
                    .semantics { contentDescription = name }
                    .then(if (isSelected) Modifier.border(2.dp, Color(swatch.argb), CircleShape) else Modifier),
            ) {
                Box(Modifier.size(34.dp).background(Color(swatch.argb), CircleShape))
            }
        }
    }
}

/** Free picker. Saving waits for the finger to settle, so dragging does not hammer the store. */
@OptIn(FlowPreview::class)
@Composable
private fun CustomColorPicker(initial: Int, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    val controller = rememberColorPickerController()
    val currentOnPick by rememberUpdatedState(onPick)
    var picked by remember { mutableStateOf<Color?>(null) }
    LaunchedEffect(Unit) {
        snapshotFlow { picked }
            .filterNotNull()
            .debounce(250)
            .collect { currentOnPick(it.toArgb()) }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp),
    ) {
        HsvColorPicker(
            modifier = Modifier.size(200.dp),
            controller = controller,
            initialColor = Color(initial),
            onColorChanged = { if (it.fromUser) picked = it.color },
        )
        BrightnessSlider(
            modifier = Modifier.fillMaxWidth().height(28.dp),
            controller = controller,
        )
    }
}

@Composable
private fun AccentPreview(modifier: Modifier = Modifier) {
    val colors = LocalClapetColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier,
    ) {
        Text(
            stringResource(R.string.accent_preview),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .heightIn(min = 18.dp)
                .background(colors.accent, RoundedCornerShape(9.dp))
                .padding(horizontal = 6.dp),
        ) {
            Text(
                stringResource(R.string.accent_preview_badge),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = colors.onAccent,
            )
        }
        Text(
            stringResource(R.string.accent_preview_text),
            style = MaterialTheme.typography.bodySmall,
            color = colors.accent,
            maxLines = 1,
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .heightIn(min = 26.dp)
                .background(colors.accent, RoundedCornerShape(13.dp))
                .padding(horizontal = 10.dp),
        ) {
            Text(
                stringResource(R.string.accent_preview_button),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = colors.onAccent,
            )
        }
    }
}

@Composable
internal fun WallpaperPage(
    wallpaper: WallpaperSettings,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var importFailed by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { importFailed = !actions.importWallpaper(uri) }
    }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
    ) {
        PageTitle(stringResource(R.string.settings_wallpaper))
        SettingsGroup(selectable = true) {
            RadioOption(
                label = stringResource(R.string.wallpaper_black),
                hint = stringResource(R.string.wallpaper_black_hint),
                selected = wallpaper.source == WallpaperSource.Black,
                onClick = { actions.onWallpaperSourceChange(WallpaperSource.Black) },
            )
            RadioOption(
                label = stringResource(R.string.wallpaper_gallery),
                hint = stringResource(R.string.wallpaper_gallery_hint),
                selected = wallpaper.source == WallpaperSource.Gallery,
                onClick = { actions.onWallpaperSourceChange(WallpaperSource.Gallery) },
            )
        }
        if (wallpaper.source == WallpaperSource.Gallery) {
            Spacer(Modifier.height(16.dp))
            Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) }) {
                    Text(stringResource(R.string.wallpaper_pick))
                }
                if (importFailed) {
                    Text(
                        stringResource(R.string.wallpaper_import_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else if (!wallpaper.hasImage) {
                    Text(
                        stringResource(R.string.wallpaper_no_image),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                DimSlider(percent = wallpaper.dimPercent, onChange = actions.onWallpaperDimChange)
            }
        }
    }
}

/** Steps of 5 %: each step is saved at once, so the home wallpaper follows while dragging. */
@Composable
private fun DimSlider(percent: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    var value by remember(percent) { mutableIntStateOf(percent) }
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.wallpaper_dim), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.wallpaper_dim_value, value),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { raw ->
                val stepped = (raw / 5f).roundToInt() * 5
                if (stepped != value) {
                    value = stepped
                    onChange(stepped)
                }
            },
            valueRange = 0f..WallpaperSettings.MAX_DIM_PERCENT.toFloat(),
            steps = WallpaperSettings.MAX_DIM_PERCENT / 5 - 1,
        )
        Text(
            stringResource(R.string.wallpaper_dim_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
