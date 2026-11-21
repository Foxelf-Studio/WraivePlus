package moye.wear.wraive.data

import com.google.gson.Gson
import moye.wear.wraive.model.ProviderConfig
import moye.wear.wraive.model.ProviderProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class ProviderShareCodecTest {
    private val gson = Gson()
    private val codec = ProviderShareCodec(gson)
    private val provider = ProviderConfig(
        name = "Test",
        protocol = ProviderProtocol.OPENAI_CHAT,
        baseUrl = "https://api.example.com",
        apiKey = "sk-secret-value",
        apiKeys = listOf("sk-second")
    )

    @Test
    fun roundTripRestoresKeys() {
        val encoded = codec.encode(provider)
        assertTrue(encoded.startsWith("wraive://provider?v=2&data="))
        assertFalse(encoded.contains("sk-secret-value"))
        assertFalse(encoded.contains("api.example.com"))

        val decoded = codec.decode(encoded)
        assertEquals("Test", decoded.name)
        assertEquals("https://api.example.com", decoded.baseUrl)
        assertEquals("sk-secret-value", decoded.apiKey)
        assertEquals(listOf("sk-second"), decoded.apiKeys)
    }

    @Test
    fun legacyPlainShareStillDecodes() {
        val json = gson.toJson(provider)
        val legacy = "wraive://provider?data=" + Base64.getUrlEncoder().withoutPadding()
            .encodeToString(json.toByteArray(Charsets.UTF_8))
        assertEquals("sk-secret-value", codec.decode(legacy).apiKey)
    }

    @Test
    fun tamperedTextFails() {
        val tampered = codec.encode(provider).dropLast(4) + "AAAA"
        assertThrows(IllegalArgumentException::class.java) { codec.decode(tampered) }
    }

    @Test
    fun unrelatedTextFails() {
        assertThrows(IllegalArgumentException::class.java) { codec.decode("hello") }
    }
}
