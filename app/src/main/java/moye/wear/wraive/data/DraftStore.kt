package moye.wear.wraive.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * Unfinished message input for a conversation.
 *
 * Only the source URIs of pending attachments are persisted; the attachments themselves are
 * re-read on restore so the draft stays small even when images are attached.
 */
data class ChatDraft(
    @SerializedName("text") val text: String = "",
    @SerializedName("attachmentUris") val attachmentUris: List<String> = emptyList()
) {
    val isEmpty: Boolean get() = text.isBlank() && attachmentUris.isEmpty()
}

class DraftStore(context: Context, private val gson: Gson) {
    private val store = context.getSharedPreferences("wraive_drafts", Context.MODE_PRIVATE)

    fun load(conversationId: String): ChatDraft {
        val json = store.getString(key(conversationId), null) ?: return ChatDraft()
        return runCatching {
            val loaded = gson.fromJson(json, ChatDraft::class.java)
            ChatDraft(
                text = loaded.text.orEmpty(),
                attachmentUris = loaded.attachmentUris.orEmpty()
            )
        }.getOrDefault(ChatDraft())
    }

    fun save(conversationId: String, draft: ChatDraft) {
        val prefKey = key(conversationId)
        if (draft.isEmpty) {
            store.edit().remove(prefKey).apply()
            return
        }
        store.edit().putString(prefKey, gson.toJson(draft)).apply()
    }

    fun clear(conversationId: String) {
        store.edit().remove(key(conversationId)).apply()
    }

    private fun key(conversationId: String) = "draft_$conversationId"
}
