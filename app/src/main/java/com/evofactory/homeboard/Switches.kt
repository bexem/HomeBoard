package com.evofactory.homeboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

/**
 * A [title] with a small switch after it, which OK turns on or off. [description], where given,
 * says more under the title. Its width follows [modifier], so it sits beside other content where
 * the caller gives it none.
 */
@Composable
internal fun SwitchRow(
    title: String,
    on: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier,
    description: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onToggle,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = colors.onSurfaceVariant,
            focusedContainerColor = colors.onSurface,
            focusedContentColor = colors.inverseOnSurface,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (description != null) {
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(alpha = 0.7f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            SwitchMark(on, LocalContentColor.current)
        }
    }
}

/**
 * A small switch: filled in [color] with a [knob] on the right when [on], an outline with the knob
 * on the left when off. The knob is the row's background, so it stands out on the fill: light on a
 * selected row, whose text is dark.
 */
@Composable
internal fun SwitchMark(on: Boolean, color: Color) {
    Box(
        modifier = Modifier
            .size(width = 36.dp, height = 20.dp)
            .then(if (on) Modifier.background(SwitchOn, CircleShape) else Modifier.border(2.dp, color.copy(alpha = 0.6f), CircleShape))
            .padding(3.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .size(14.dp)
                .background(if (on) SwitchKnob else color.copy(alpha = 0.6f), CircleShape),
        )
    }
}
