package moye.wear.wraive.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performRotaryScrollInput
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.swipeDown
import androidx.wear.compose.material3.AppScaffold
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import moye.wear.wraive.data.DraftStore
import moye.wear.wraive.model.ChatMessage
import moye.wear.wraive.model.Conversation
import moye.wear.wraive.model.MessageRole
import moye.wear.wraive.model.MessageStatus
import moye.wear.wraive.model.TranscriptionConfig
import moye.wear.wraive.ui.screens.ChatScreen
import moye.wear.wraive.ui.theme.WraiveTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChatStreamingTest {
    @get:Rule val compose = createComposeRule()

    private val draftStore = DraftStore(
        InstrumentationRegistry.getInstrumentation().targetContext,
        Gson()
    )

    @Test
    fun rotaryScrollSurvivesStreamedRecompositions() {
        val conversation = Conversation(id = "rotary-chat", assistantId = "assistant")
        val reply = mutableStateOf(ChatMessage(
            id = "rotary-reply", conversationId = conversation.id, role = MessageRole.ASSISTANT,
            content = "表冠滚动时继续生成内容。\n".repeat(80), status = MessageStatus.STREAMING
        ))
        compose.setContent {
            WraiveTheme(dynamicColor = false, localeTag = "zh") {
                AppScaffold {
                    ChatScreen(conversation, listOf(reply.value), emptyList(), true, true, false,
                        readAttachment = { emptyList() }, transcriptionConfig = TranscriptionConfig(),
                        startRecording = {}, stopAndTranscribe = { "" }, cancelRecording = {},
                        onSend = { _, _ -> }, onStop = {}, onMessageActions = {}, draftStore = draftStore)
                }
            }
        }
        compose.waitForIdle()
        fun position() = compose.onNodeWithTag("chat-list").fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange].value()

        var previous = position()
        repeat(3) {
            compose.runOnIdle { reply.value = reply.value.copy(content = reply.value.content + "追加内容。\n") }
            compose.onNodeWithTag("chat-list").performRotaryScrollInput {
                repeat(6) {
                    rotateToScrollVertically(-48f)
                    advanceEventTime(16)
                }
            }
            compose.mainClock.advanceTimeBy(500)
            compose.waitForIdle()
            val current = position()
            assertTrue("The crown must keep scrolling toward older messages after recomposition", current < previous)
            previous = current
        }
        compose.onNodeWithContentDescription("回到最新消息").assertIsDisplayed()
    }

    @Test
    fun rotaryScrollResumesAfterClosingInput() {
        val conversation = Conversation(id = "input-chat", assistantId = "assistant")
        val reply = ChatMessage(
            conversationId = conversation.id, role = MessageRole.ASSISTANT,
            content = "关闭输入窗口后继续阅读历史消息。\n".repeat(80)
        )
        compose.setContent {
            WraiveTheme(dynamicColor = false, localeTag = "zh") {
                AppScaffold {
                    ChatScreen(conversation, listOf(reply), emptyList(), false, true, false,
                        readAttachment = { emptyList() }, transcriptionConfig = TranscriptionConfig(),
                        startRecording = {}, stopAndTranscribe = { "" }, cancelRecording = {},
                        onSend = { _, _ -> }, onStop = {}, onMessageActions = {}, draftStore = draftStore)
                }
            }
        }
        compose.onNodeWithContentDescription("输入").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("继续")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.waitForIdle()
        val before = compose.onNodeWithTag("chat-list").fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange].value()
        compose.onNodeWithTag("chat-list").performRotaryScrollInput {
            repeat(6) {
                rotateToScrollVertically(-48f)
                advanceEventTime(16)
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        val after = compose.onNodeWithTag("chat-list").fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue("The chat must regain crown focus after closing input", after < before)
    }

    @Test
    fun longMarkdownReplyCanBeOpened() = openLongReply(markdownEnabled = true)

    @Test
    fun longPlainTextReplyCanBeOpened() = openLongReply(markdownEnabled = false)

    @Test
    fun longCodeReplyCanBeOpened() = openLongReply(
        markdownEnabled = true,
        content = "```kotlin\nval text = \"" + "长文本".repeat(5_000) + "\"\n```"
    )

    @Test
    fun veryTallReplyCanBeOpened() = openLongReply(
        markdownEnabled = true,
        content = "这是一条需要完整保留的回复。\n".repeat(6_000)
    )

    private fun openLongReply(
        markdownEnabled: Boolean,
        content: String = "这是一条较长的回复，包含需要完整保留的会话内容。\n".repeat(1_000)
    ) {
        val conversation = Conversation(id = "long-chat", assistantId = "assistant")
        val message = ChatMessage(
            id = "long-reply", conversationId = conversation.id, role = MessageRole.ASSISTANT,
            content = content
        )
        compose.setContent {
            WraiveTheme(dynamicColor = false, localeTag = "zh") {
                AppScaffold {
                    ChatScreen(conversation, listOf(message), emptyList(), false, true, markdownEnabled,
                        readAttachment = { emptyList() }, transcriptionConfig = TranscriptionConfig(),
                        startRecording = {}, stopAndTranscribe = { "" }, cancelRecording = {},
                        onSend = { _, _ -> }, onStop = {}, onMessageActions = {}, draftStore = draftStore)
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("chat-bottom").assertIsDisplayed()
        compose.onNodeWithTag("chat-list").performScrollToIndex(1)
        compose.onNodeWithTag("message-long-reply").assertIsDisplayed()
    }

    @Test
    fun streamedGrowthKeepsTheBottomStableAndRespectsReadingPosition() {
        val conversation = Conversation(id = "test-chat", assistantId = "assistant", title = "流式回复")
        val reply = mutableStateOf(ChatMessage(
            id = "reply", conversationId = conversation.id, role = MessageRole.ASSISTANT,
            content = "这是持续生成的回复。\n".repeat(30), status = MessageStatus.STREAMING
        ))
        compose.setContent {
            WraiveTheme(dynamicColor = false, localeTag = "zh") {
                AppScaffold {
                    ChatScreen(conversation, listOf(reply.value), emptyList(), true, true, true,
                        readAttachment = { emptyList() }, transcriptionConfig = TranscriptionConfig(),
                        startRecording = {}, stopAndTranscribe = { "" }, cancelRecording = {},
                        onSend = { _, _ -> }, onStop = {}, onMessageActions = {}, draftStore = draftStore)
                }
            }
        }
        compose.waitForIdle()
        val bottomBefore = compose.onNodeWithTag("chat-bottom").fetchSemanticsNode().boundsInRoot.top
        compose.runOnIdle { reply.value = reply.value.copy(content = reply.value.content + "追加内容。\n".repeat(10)) }
        compose.waitForIdle()
        val bottomAfter = compose.onNodeWithTag("chat-bottom").fetchSemanticsNode().boundsInRoot.top
        assertEquals("Following a longer reply must keep its bottom in place", bottomBefore, bottomAfter, 1f)

        compose.onNodeWithTag("chat-list").performTouchInput { swipeDown() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("回到最新消息").assertIsDisplayed()
        val viewport = compose.onNodeWithTag("chat-list").fetchSemanticsNode().boundsInRoot
        val jump = compose.onNodeWithTag("chat-jump-to-bottom").fetchSemanticsNode().boundsInRoot
        assertTrue("Jump button must occupy the lower right of the viewport", jump.center.x > viewport.center.x && jump.top > viewport.center.y)
        assertTrue("Jump button must remain above the bottom input control", jump.bottom < viewport.bottom)
        val readingPosition = compose.onNodeWithTag("message-reply").fetchSemanticsNode().positionInRoot.y
        compose.runOnIdle { reply.value = reply.value.copy(content = reply.value.content + "继续生成。\n".repeat(12)) }
        compose.waitForIdle()
        assertEquals("Reading older content must not be interrupted by new tokens",
            readingPosition, compose.onNodeWithTag("message-reply").fetchSemanticsNode().positionInRoot.y, 1f)

        compose.onNodeWithContentDescription("回到最新消息").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("chat-bottom").assertIsDisplayed()
        compose.runOnIdle { reply.value = reply.value.copy(status = MessageStatus.COMPLETE) }
        compose.waitForIdle()
        compose.onNodeWithTag("chat-bottom").assertIsDisplayed()
    }
}
