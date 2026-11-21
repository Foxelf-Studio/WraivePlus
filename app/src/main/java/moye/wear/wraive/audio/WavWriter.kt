package moye.wear.wraive.audio

import java.io.File
import java.io.RandomAccessFile

/**
 * Writes a 44-byte PCM WAV header in front of raw 16-bit mono samples.
 */
internal fun writeWavHeader(file: File, dataSize: Int, sampleRate: Int) {
    RandomAccessFile(file, "rw").use { output ->
        output.seek(0)
        output.write("RIFF".toByteArray(Charsets.US_ASCII))
        output.writeLeInt(36 + dataSize)
        output.write("WAVE".toByteArray(Charsets.US_ASCII))
        output.write("fmt ".toByteArray(Charsets.US_ASCII))
        output.writeLeInt(16)
        output.writeLeShort(1)
        output.writeLeShort(1)
        output.writeLeInt(sampleRate)
        output.writeLeInt(sampleRate * 2)
        output.writeLeShort(2)
        output.writeLeShort(16)
        output.write("data".toByteArray(Charsets.US_ASCII))
        output.writeLeInt(dataSize)
    }
}

private fun RandomAccessFile.writeLeInt(value: Int) {
    write(
        byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte()
        )
    )
}

private fun RandomAccessFile.writeLeShort(value: Int) {
    write(
        byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte()
        )
    )
}
