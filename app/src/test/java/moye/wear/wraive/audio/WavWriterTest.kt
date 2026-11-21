package moye.wear.wraive.audio

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class WavWriterTest {
    @Test
    fun writesSixteenBitMonoPcmHeader() {
        val file = File.createTempFile("wraive-wav", ".wav")
        try {
            file.writeBytes(ByteArray(44))
            writeWavHeader(file, dataSize = 32_000, sampleRate = 16_000)
            val bytes = file.readBytes()
            assertEquals("RIFF", ascii(bytes, 0, 4))
            assertEquals(36 + 32_000, leInt(bytes, 4))
            assertEquals("WAVE", ascii(bytes, 8, 4))
            assertEquals("fmt ", ascii(bytes, 12, 4))
            assertEquals(16, leInt(bytes, 16))
            assertEquals(1, leShort(bytes, 20))
            assertEquals(1, leShort(bytes, 22))
            assertEquals(16_000, leInt(bytes, 24))
            assertEquals(32_000, leInt(bytes, 28))
            assertEquals(2, leShort(bytes, 32))
            assertEquals(16, leShort(bytes, 34))
            assertEquals("data", ascii(bytes, 36, 4))
            assertEquals(32_000, leInt(bytes, 40))
        } finally {
            file.delete()
        }
    }

    private fun ascii(bytes: ByteArray, offset: Int, length: Int) =
        String(bytes, offset, length, Charsets.US_ASCII)

    private fun leInt(bytes: ByteArray, offset: Int) =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    private fun leShort(bytes: ByteArray, offset: Int) =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
}
