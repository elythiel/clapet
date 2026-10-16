package xyz.martelle.clapet.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import xyz.martelle.clapet.R

@Composable
internal fun PageTitle(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier.padding(start = 8.dp, top = 16.dp, bottom = 16.dp)) {
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A Material card holding related rows; the blurred wallpaper shows around it. */
@Composable
internal fun SettingsGroup(modifier: Modifier = Modifier, selectable: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(if (selectable) Modifier.selectableGroup() else Modifier, content = content)
    }
}

private val TransparentListItem
    @Composable get() = ListItemDefaults.colors(containerColor = Color.Transparent)

/** A settings menu row: icon, title, current state, an optional trailing mark, then a chevron. */
@Composable
internal fun SettingsEntry(
    @DrawableRes icon: Int,
    title: String,
    summary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                trailing()
                Icon(
                    painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .size(18.dp),
                )
            }
        },
        colors = TransparentListItem,
        modifier = modifier.clickable(onClick = onClick),
    )
}

/** One choice of a radio group; place it in a `SettingsGroup(selectable = true)`. */
@Composable
internal fun RadioOption(
    label: String,
    hint: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = { Text(hint) },
        // The whole row is the radio button for accessibility; the visual one does not handle clicks.
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        colors = TransparentListItem,
        modifier = modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    )
}
