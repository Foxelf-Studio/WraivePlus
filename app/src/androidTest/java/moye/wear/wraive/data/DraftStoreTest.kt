package moye.wear.wraive.data

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class DraftStoreTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = DraftStore(context, Gson())

    @Test
    fun draftRoundTripsAndClears() {
        val conversationId = "draft-test-${UUID.randomUUID()}"
        val draft = ChatDraft(
            text = "还没发出去的内容",
            attachmentUris = listOf("content://media/external/images/media/42")
        )
        store.save(conversationId, draft)
        assertEquals(draft, store.load(conversationId))

        store.clear(conversationId)
        assertTrue(store.load(conversationId).isEmpty)
    }

    @Test
    fun emptyDraftRemovesStoredEntry() {
        val conversationId = "draft-test-${UUID.randomUUID()}"
        store.save(conversationId, ChatDraft(text = "临时内容"))
        store.save(conversationId, ChatDraft(text = " ", attachmentUris = emptyList()))
        assertTrue(store.load(conversationId).isEmpty)
    }

    @Test
    fun unknownConversationHasEmptyDraft() {
        assertTrue(store.load("missing-${UUID.randomUUID()}").isEmpty)
    }
}
