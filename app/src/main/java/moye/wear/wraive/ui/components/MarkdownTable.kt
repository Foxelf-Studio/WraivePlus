package moye.wear.wraive.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import moye.wear.wraive.ui.tr

/**
 * A markdown table that keeps natural column widths and scrolls horizontally instead of
 * squeezing every cell into the screen width.
 */
@Composable
internal fun MarkdownTable(table: MarkdownTableBlock) {
    if (table.columns == 0) return
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val textStyle = MaterialTheme.typography.bodySmall.copy(
        color = MaterialTheme.colorScheme.onSurface
    )
    val headerStyle: TextStyle = textStyle.copy(fontWeight = FontWeight.SemiBold)
    val widths = remember(table, textStyle, headerStyle, density) {
        val maxCellPx = with(density) { MAX_CELL_WIDTH.roundToPx() }
        val minCellPx = with(density) { MIN_CELL_WIDTH.roundToPx() }
        val horizontalPaddingPx = with(density) { CELL_HORIZONTAL_PADDING.roundToPx() }
        List(table.columns) { column ->
            val cells = buildList {
                add(table.header.getOrElse(column) { "" })
                table.rows.forEach { add(it.getOrElse(column) { "" }) }
            }
            val widest = cells.maxOfOrNull { cell ->
                measurer.measure(
                    text = AnnotatedString(renderTableCell(cell)),
                    style = headerStyle,
                    softWrap = true,
                    constraints = Constraints(maxWidth = maxCellPx)
                ).size.width
            } ?: 0
            with(density) {
                (widest + horizontalPaddingPx).coerceIn(minCellPx, maxCellPx + horizontalPaddingPx).toDp()
            }
        }
    }
    val totalWidth = widths.fold(0.dp) { acc, width -> acc + width } +
        (table.columns - 1).coerceAtLeast(0).dp
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val scrollable = totalWidth > screenWidth - 48.dp
    val borderColor = MaterialTheme.colorScheme.outlineVariant

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
        ) {
            Column(
                Modifier.width(totalWidth)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                TableRow(
                    cells = table.header,
                    widths = widths,
                    alignments = table.alignments,
                    style = headerStyle,
                    background = MaterialTheme.colorScheme.surfaceContainerHigh,
                    borderColor = borderColor
                )
                table.rows.forEach { row ->
                    TableDivider(borderColor)
                    TableRow(
                        cells = row,
                        widths = widths,
                        alignments = table.alignments,
                        style = textStyle,
                        background = Color.Transparent,
                        borderColor = borderColor
                    )
                }
            }
        }
        if (scrollable) {
            Text(
                text = tr("表格可左右滑动"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun TableRow(
    cells: List<String>,
    widths: List<Dp>,
    alignments: List<MarkdownTableAlignment>,
    style: TextStyle,
    background: Color,
    borderColor: Color
) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(background),
        verticalAlignment = Alignment.CenterVertically
    ) {
        cells.forEachIndexed { index, cell ->
            Box(
                modifier = Modifier
                    .width(widths.getOrElse(index) { MIN_CELL_WIDTH })
                    .fillMaxHeight()
                    .padding(horizontal = CELL_HORIZONTAL_PADDING, vertical = 6.dp),
                contentAlignment = Alignment.TopStart
            ) {
                Text(
                    text = renderTableCell(cell),
                    style = style,
                    textAlign = when (alignments.getOrElse(index) { MarkdownTableAlignment.START }) {
                        MarkdownTableAlignment.START -> TextAlign.Start
                        MarkdownTableAlignment.CENTER -> TextAlign.Center
                        MarkdownTableAlignment.END -> TextAlign.End
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (index < cells.lastIndex) {
                Box(Modifier.width(1.dp).fillMaxHeight().background(borderColor))
            }
        }
    }
}

@Composable
private fun TableDivider(color: Color) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(color))
}

private val MIN_CELL_WIDTH = 64.dp
private val MAX_CELL_WIDTH = 220.dp
private val CELL_HORIZONTAL_PADDING = 8.dp
