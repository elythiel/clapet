package xyz.martelle.clapet.home

import android.os.SystemClock
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import xyz.martelle.clapet.R
import xyz.martelle.clapet.notifications.NowPlaying
import xyz.martelle.clapet.theme.LocalClapetColors
import xyz.martelle.clapet.theme.TextPrimary

/** What the player's buttons do. */
class MediaActions(val onPrevious: () -> Unit, val onPlayPause: () -> Unit, val onNext: () -> Unit)

/** Each button takes a third of the width and this height to the touch, whatever its drawn size. */
private val ControlsHeight = 52.dp

/**
 * The player in the square above the cameras, shown only while a session plays or is paused:
 * cover, title, artist, progress, then previous, play/pause and next. The buttons are drawn at
 * 36-40 dp for lack of room; their touch areas fill the whole row.
 */
@Composable
fun NowPlayingCard(nowPlaying: NowPlaying, actions: MediaActions, modifier: Modifier = Modifier) {
    val accent = LocalClapetColors.current.accent
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF2A2440)),
        ) {
            val art = nowPlaying.art
            if (art != null) {
                Image(art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(painterResource(R.drawable.ic_music), contentDescription = null, tint = Color(0xFFB9A3F2), modifier = Modifier.size(26.dp))
            }
        }
        Column {
            Text(
                nowPlaying.title,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 17.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (nowPlaying.artist.isNotBlank()) {
                Text(nowPlaying.artist, color = muted, fontSize = 11.sp, lineHeight = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (nowPlaying.durationMs > 0) Progress(nowPlaying, accent)
        Row(Modifier.fillMaxWidth().height(ControlsHeight)) {
            Control(R.drawable.ic_skip_previous, stringResource(R.string.media_previous), 36.dp, nowPlaying.canSkipPrevious, filled = false, onClick = actions.onPrevious)
            Control(
                if (nowPlaying.playing) R.drawable.ic_pause else R.drawable.ic_play,
                stringResource(if (nowPlaying.playing) R.string.media_pause else R.string.media_play),
                40.dp,
                enabled = true,
                filled = true,
                onClick = actions.onPlayPause,
            )
            Control(R.drawable.ic_skip_next, stringResource(R.string.media_next), 36.dp, nowPlaying.canSkipNext, filled = false, onClick = actions.onNext)
        }
    }
}

/** Moves on its own while playing: the clock is read at draw time, so only the bar redraws. */
@Composable
private fun Progress(nowPlaying: NowPlaying, accent: Color) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(nowPlaying.playing, nowPlaying.positionUpdatedAt) {
        now = SystemClock.elapsedRealtime()
        while (nowPlaying.playing) {
            delay(500)
            now = SystemClock.elapsedRealtime()
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color(0xFF2A2A2E))
            .drawBehind {
                val progress = nowPlaying.progressAt(now) ?: 0f
                drawRect(accent, size = Size(size.width * progress, size.height))
            },
    )
}

@Composable
private fun RowScope.Control(
    @DrawableRes icon: Int,
    label: String,
    drawnSize: Dp,
    enabled: Boolean,
    filled: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalClapetColors.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = null, onClick = onClick)
            .alpha(if (enabled) 1f else 0.38f),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(drawnSize)
                .then(
                    if (filled) {
                        Modifier.background(colors.accent, CircleShape)
                    } else {
                        Modifier.border(1.dp, Color(0xFF3A3A3F), CircleShape)
                    },
                ),
        ) {
            Icon(
                painterResource(icon),
                contentDescription = label,
                tint = if (filled) colors.onAccent else TextPrimary,
                modifier = Modifier.size(if (filled) 18.dp else 16.dp),
            )
        }
    }
}
