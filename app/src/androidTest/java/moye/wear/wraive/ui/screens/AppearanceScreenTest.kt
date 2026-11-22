package moye.wear.wraive.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.wear.compose.material3.AppScaffold
import moye.wear.wraive.model.AppPreferences
import moye.wear.wraive.ui.theme.WraiveTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AppearanceScreenTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun swipeBackSwitchSitsAboveDisplaySection() {
        rule.setContent {
            WraiveTheme(dynamicColor = false, localeTag = "zh-CN") {
                AppScaffold {
                    AppearanceScreen(AppPreferences()) { }
                }
            }
        }
        rule.waitForIdle()

        val toggle = rule.onNodeWithText("屏蔽右滑返回").fetchSemanticsNode().boundsInRoot
        val display = rule.onNodeWithText("显示").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "swipe switch (top=${toggle.top}) should sit above the display section (top=${display.top})",
            toggle.top < display.top
        )
    }
}
