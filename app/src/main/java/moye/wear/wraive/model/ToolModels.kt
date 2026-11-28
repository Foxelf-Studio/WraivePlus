package moye.wear.wraive.model

import com.google.gson.annotations.SerializedName
import java.util.UUID

enum class SearchProviderType {
    DUCKDUCKGO,
    BING,
    EXA,
    TAVILY,
    ZHIPU,
    LINKUP,
    BRAVE,
    METASO,
    SEARXNG,
    OLLAMA,
    JINA,
    PERPLEXITY,
    BOCHA,
    SERPER,
    GROK,
    CUSTOM
}

data class SearchProviderConfig(
    @SerializedName(value = "id", alternate = ["a"])
    val id: String = UUID.randomUUID().toString(),
    @SerializedName(value = "name", alternate = ["b"])
    val name: String,
    @SerializedName(value = "type", alternate = ["c"])
    val type: SearchProviderType?,
    @SerializedName(value = "baseUrl", alternate = ["d"])
    val baseUrl: String = "",
    @SerializedName(value = "apiKey", alternate = ["e"])
    val apiKey: String = "",
    @SerializedName(value = "enabled", alternate = ["f"])
    val enabled: Boolean = true,
    @SerializedName(value = "resultLimit", alternate = ["g"])
    val resultLimit: Int = 5,
    @SerializedName(value = "customHeaders", alternate = ["h"])
    val customHeaders: Map<String, String> = emptyMap()
)

data class SearchResult(
    val title: String,
    val url: String,
    val snippet: String
)

enum class McpTransport { STREAMABLE_HTTP, SSE }

data class McpServerConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val url: String,
    val transport: McpTransport = McpTransport.STREAMABLE_HTTP,
    val headers: Map<String, String> = emptyMap(),
    val enabled: Boolean = true,
    val timeoutSeconds: Int = 30
)

data class McpTool(
    val serverId: String,
    val name: String,
    val description: String = "",
    val inputSchema: String = "{\"type\":\"object\"}"
)

data class McpToolResult(
    val text: String,
    val isError: Boolean = false
)

data class AppPreferences(
    val dynamicColor: Boolean = true,
    val showReasoning: Boolean = true,
    val showProvider: Boolean = false,
    val markdownEnabled: Boolean = true,
    val backgroundGeneration: Boolean = true,
    val confirmRegenerate: Boolean = true,
    val confirmDelete: Boolean = true,
    val localeTag: String = "system",
    val hapticsEnabled: Boolean = true,
    val autoTitle: Boolean = true,
    val contextCompressionEnabled: Boolean = true,
    val searchResultLimit: Int = 5,
    val autoSpeakReplies: Boolean = false,
    val customFontPath: String = "",
    /** Disables the nav host's right-swipe gesture; system back still navigates. */
    val blockSwipeBack: Boolean = false,
    /** "system", "light" or "dark". */
    val themeMode: String = "system",
    /** UI scale applied through LocalDensity; 1f is the design size. */
    val displayScale: Float = 1f,
    /** Focus the composer and show the keyboard as soon as the input sheet opens. */
    val autoFocusInput: Boolean = true
)
