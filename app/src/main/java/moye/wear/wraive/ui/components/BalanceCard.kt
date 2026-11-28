package moye.wear.wraive.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import moye.wear.wraive.network.BalanceState
import moye.wear.wraive.ui.tr

/** Compact balance readout for the provider behind the active assistant. */
@Composable
fun BalanceCard(
    providerName: String,
    state: BalanceState,
    onRefresh: () -> Unit
) {
    val value = when (state) {
        BalanceState.Idle -> "—"
        BalanceState.Loading -> "查询中…"
        is BalanceState.Amount -> state.amount
        is BalanceState.Unavailable -> state.message
        is BalanceState.Failure -> state.message
    }
    val valueColor = when (state) {
        is BalanceState.Failure -> MaterialTheme.colorScheme.error
        is BalanceState.Unavailable -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "$providerName ${tr("余额")}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = valueColor,
                maxLines = 2
            )
            if (state is BalanceState.Amount) {
                Text(
                    text = tr("点击右侧按钮刷新"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        IconButton(
            onClick = onRefresh,
            enabled = state !is BalanceState.Loading
        ) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = tr("刷新余额"),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
