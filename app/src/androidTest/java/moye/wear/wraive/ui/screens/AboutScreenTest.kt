package moye.wear.wraive.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.wear.compose.material3.AppScaffold
import moye.wear.wraive.ui.theme.WraiveTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AboutScreenTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun repositoriesListUpstreamAboveFork() {
        rule.setContent {
            WraiveTheme(dynamicColor = false, localeTag = "zh-CN") {
                AppScaffold { AboutScreen() }
            }
        }
        rule.waitForIdle()

        val upstream = rule.onNodeWithText("原仓库地址").fetchSemanticsNode().boundsInRoot
        val fork = rule.onNodeWithText("本仓库地址").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "upstream repo (top=${upstream.top}) should sit above fork (top=${fork.top})",
            upstream.top < fork.top
        )
        rule.onNodeWithText("github.com/youshen2/Wraive").assertIsDisplayed()
        rule.onNodeWithText("github.com/Foxelf-Studio/WraivePlus").assertIsDisplayed()
    }
}
