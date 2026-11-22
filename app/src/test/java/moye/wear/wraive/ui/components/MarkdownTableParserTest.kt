package moye.wear.wraive.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTableParserTest {
    @Test
    fun parsesTableWithAlignments() {
        val markdown = """
            Intro
            | 条件 | 方程式 | 说明 |
            | :--- | :---: | ---: |
            | 稀 HNO3 | Fe + 4HNO3 | 生成 Fe^{3+} |
            | 浓 HNO3 | — | 钝化 |
        """.trimIndent()

        val blocks = splitTableBlocks(markdown)
        assertEquals(2, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.Text)
        val table = (blocks[1] as MarkdownBlock.Table).table
        assertEquals(listOf("条件", "方程式", "说明"), table.header)
        assertEquals(2, table.rows.size)
        assertEquals(MarkdownTableAlignment.START, table.alignments[0])
        assertEquals(MarkdownTableAlignment.CENTER, table.alignments[1])
        assertEquals(MarkdownTableAlignment.END, table.alignments[2])
        assertEquals("Fe³⁺", renderTableCell(table.rows[0][2]))
        assertEquals("Fe", renderTableCell("**Fe**"))
    }

    @Test
    fun keepsEscapedPipesInsideCells() {
        val markdown = """
            | A | B |
            | --- | --- |
            | a \| b | `x\|y` |
        """.trimIndent()

        val table = (splitTableBlocks(markdown).single() as MarkdownBlock.Table).table
        assertEquals("A", table.header[0])
        assertEquals("a | b", table.rows[0][0])
        assertEquals("x|y", renderTableCell(table.rows[0][1]))
    }

    @Test
    fun invalidSeparatorStaysText() {
        val markdown = """
            | A | B |
            | -- | -- |
            | 1 | 2 |
        """.trimIndent()

        val blocks = splitTableBlocks(markdown)
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.Text)
    }

    @Test
    fun padsShortRows() {
        val markdown = """
            | A | B | C |
            | --- | --- | --- |
            | 1 | 2 |
        """.trimIndent()

        val table = (splitTableBlocks(markdown).single() as MarkdownBlock.Table).table
        assertEquals(3, table.columns)
        assertEquals(listOf("1", "2", ""), table.rows[0])
    }
}
