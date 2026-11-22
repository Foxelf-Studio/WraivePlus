package moye.wear.wraive.ui.components

internal sealed interface MarkdownBlock {
    data class Text(val markdown: String) : MarkdownBlock
    data class Table(val table: MarkdownTableBlock) : MarkdownBlock
}

internal data class MarkdownTableBlock(
    val header: List<String>,
    val rows: List<List<String>>,
    val alignments: List<MarkdownTableAlignment>
) {
    val columns: Int get() = header.size
}

internal enum class MarkdownTableAlignment { START, CENTER, END }

/**
 * Splits markdown into text and table blocks. A table needs a header row followed by a
 * separator row (`| --- | :---: |`), matching the common GitHub table syntax.
 */
internal fun splitTableBlocks(markdown: String): List<MarkdownBlock> {
    val lines = markdown.split('\n')
    val blocks = mutableListOf<MarkdownBlock>()
    val text = StringBuilder()

    fun flushText() {
        if (text.isNotBlank()) blocks += MarkdownBlock.Text(text.toString())
        text.clear()
    }

    var index = 0
    while (index < lines.size) {
        val next = lines.getOrNull(index + 1)
        if (next != null && lines[index].contains('|') && isSeparatorRow(next)) {
            var end = index + 2
            while (end < lines.size && lines[end].isNotBlank() && lines[end].contains('|')) {
                end++
            }
            val table = parseTable(lines.subList(index, end))
            if (table != null) {
                flushText()
                blocks += MarkdownBlock.Table(table)
                index = end
                continue
            }
        }
        text.appendLine(lines[index])
        index++
    }
    flushText()
    return blocks
}

private fun parseTable(lines: List<String>): MarkdownTableBlock? {
    if (lines.size < 2) return null
    val header = splitRow(lines[0])
    val separator = splitRow(lines[1])
    if (header.isEmpty() || separator.isEmpty()) return null
    if (!separator.all(::isSeparatorCell)) return null

    val columns = maxOf(header.size, separator.size)
    val alignments = List(columns) { column ->
        val cell = separator.getOrElse(column) { "---" }
        when {
            cell.startsWith(":") && cell.endsWith(":") -> MarkdownTableAlignment.CENTER
            cell.endsWith(":") -> MarkdownTableAlignment.END
            else -> MarkdownTableAlignment.START
        }
    }
    val rows = lines.drop(2)
        .filter { it.isNotBlank() && it.contains('|') }
        .map { splitRow(it).padTo(columns) }
    return MarkdownTableBlock(
        header = header.padTo(columns),
        rows = rows,
        alignments = alignments
    )
}

private fun isSeparatorRow(line: String): Boolean {
    if (!line.contains('-')) return false
    val cells = splitRow(line)
    return cells.isNotEmpty() && cells.all(::isSeparatorCell)
}

private fun isSeparatorCell(cell: String): Boolean =
    Regex("^:?-{3,}:?$").matches(cell.trim())

private fun splitRow(line: String): List<String> {
    var value = line.trim()
    if (value.startsWith("|")) value = value.drop(1)
    if (value.endsWith("|") && !value.endsWith("\\|")) value = value.dropLast(1)
    if (value.isEmpty()) return emptyList()

    val cells = mutableListOf<String>()
    val current = StringBuilder()
    var index = 0
    while (index < value.length) {
        val char = value[index]
        when {
            char == '\\' && index + 1 < value.length && value[index + 1] == '|' -> {
                current.append('|')
                index++
            }
            char == '|' -> {
                cells += current.toString().trim()
                current.clear()
            }
            else -> current.append(char)
        }
        index++
    }
    cells += current.toString().trim()
    return cells
}

private fun List<String>.padTo(size: Int): List<String> =
    if (this.size >= size) take(size) else this + List(size - size) { "" }

/**
 * Table cells are rendered as single Compose text runs. Strip inline markers but keep the
 * chemistry notation readable by converting `<sup>`, `<sub>`, `^{...}` and `_{...}`.
 */
internal fun renderTableCell(cell: String): String {
    var text = cell
    text = text.replace(Regex("(?i)<br\\s*/?>"), "\n")
    text = text.replace(Regex("(?is)<sup>(.*?)</sup>")) { toScript(it.groupValues[1], SUPERSCRIPTS) }
    text = text.replace(Regex("(?is)<sub>(.*?)</sub>")) { toScript(it.groupValues[1], SUBSCRIPTS) }
    text = text.replace(Regex("\\^\\{([^{}]*)\\}")) { toScript(it.groupValues[1], SUPERSCRIPTS) }
    text = text.replace(Regex("_\\{([^{}]*)\\}")) { toScript(it.groupValues[1], SUBSCRIPTS) }
    text = text.replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
    text = text.replace(Regex("__(.+?)__"), "$1")
    text = text.replace(Regex("`([^`]*)`"), "$1")
    text = text.replace(Regex("\\[([^]]*)]\\([^)]*\\)"), "$1")
    text = text.replace(Regex("<[^>]+>"), "")
    return text.trim()
}

private fun toScript(value: String, mapping: Map<Char, Char>): String =
    value.map { mapping[it] ?: it }.joinToString("")

private val SUPERSCRIPTS = mapOf(
    '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
    '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
    '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾',
    'n' to 'ⁿ', 'i' to 'ⁱ'
)

private val SUBSCRIPTS = mapOf(
    '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
    '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
    '+' to '₊', '-' to '₋', '(' to '₍', ')' to '₎'
)
