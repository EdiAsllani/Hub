package com.edi.hub.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** 38% emphasis. Three signals carry "not yet": emphasis, a thinner icon, and a dotted rule. */
const val GHOSTED_ALPHA = 0.38f

/**
 * A destination that is visible but not built yet.
 *
 * `NavigationBar` has no disabled-item state, so one is invented here and reused by the reserved
 * entries in the FAB menu — one rule, two places. A ghosted slot drops to 38% emphasis, thins its
 * icon, and carries a dotted underline where a live destination would grow a solid active
 * indicator. The dotted rule is the signal that survives greyscale and colour inversion, so the
 * treatment never leans on colour alone.
 *
 * Tapping produces no ripple, no selection and no navigation — the indicator draws itself as a
 * dashed outline for 200 ms and [onTap] raises the snackbar. The slot stays in the TalkBack focus
 * order and announces itself as dimmed rather than disappearing, because a hidden tab is a tab the
 * user never learns is coming.
 */
@Composable
fun GhostedNavItem(
    label: String,
    icon: ImageVector,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    armed: Boolean = false,
) {
    val outline by animateFloatAsState(
        targetValue = if (armed) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "ghostedIndicator",
    )
    val tint = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier
            .selectable(
                selected = false,
                // No indication: a ripple would promise a navigation that is not coming.
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap,
            )
            .semantics { contentDescription = "$label, dimmed, not available yet" }
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .width(64.dp)
                .height(32.dp)
                .drawBehind { drawDashedIndicator(tint.copy(alpha = outline * GHOSTED_ALPHA)) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint.copy(alpha = GHOSTED_ALPHA),
                // Thinner than a live slot's icon; the ghosted set is the outlined pair.
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = tint.copy(alpha = GHOSTED_ALPHA),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        DottedRule(color = tint.copy(alpha = GHOSTED_ALPHA), modifier = Modifier.width(24.dp))
    }
}

/** Where a live destination would carry a solid active indicator. */
@Composable
private fun DottedRule(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(2.dp)
            .drawBehind {
                drawLine(
                    color = color,
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = size.height,
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx())),
                )
            },
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDashedIndicator(color: Color) {
    if (color.alpha == 0f) return
    drawRoundRect(
        color = color,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
        style = Stroke(
            width = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
        ),
    )
}
