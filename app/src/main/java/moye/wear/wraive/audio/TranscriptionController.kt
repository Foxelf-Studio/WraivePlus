package moye.wear.wraive.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moye.wear.wraive.model.TranscriptionConfig
import moye.wear.wraive.model.TranscriptionEngine
import moye.wear.wraive.network.parseJson
import moye.wear.wraive.network.requireSuccess
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

class TranscriptionController(
    context: Context,
    private val httpClient: OkHttpClient,
    private val gson: Gson
) {
    private val cacheDir = context.applicationContext.cacheDir
    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null
    @Volatile private var pcmRecorder: AudioRecord? = null
    @Volatile private var pcmThread: Thread? = null
    @Volatile private var pcmActive = false
    private var pcmFile: File? = null

    fun startRecording(config: TranscriptionConfig) {
        cancel()
        if (config.engine == TranscriptionEngine.DEEPSEEK) {
            startPcmRecording()
        } else {
            startMediaRecording()
        }
    }

    @Suppress("DEPRECATION")
    private fun startMediaRecording() {
        val file = File.createTempFile("wraive-recording-", ".awb", cacheDir)
        val mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.AMR_WB)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_WB)
            setAudioSamplingRate(16_000)
            setAudioEncodingBitRate(23_850)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        recordingFile = file
        recorder = mediaRecorder
    }

    /**
     * DeepSeek ASR only accepts 16-bit PCM WAV or Ogg Opus, so this path records raw PCM with
     * [AudioRecord] and writes a WAV container. It also caps audio at the API's 120-second limit.
     */
    private fun startPcmRecording() {
        val file = File.createTempFile("wraive-recording-", ".wav", cacheDir)
        val minBuffer = AudioRecord.getMinBufferSize(
            PCM_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        require(minBuffer > 0) {
            file.delete()
            "此设备不支持 16kHz 录音"
        }
        val bufferSize = max(minBuffer, PCM_SAMPLE_RATE * 2 / 5)
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            PCM_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        require(audioRecord.state == AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            file.delete()
            "无法初始化录音"
        }
        val output = FileOutputStream(file)
        output.write(ByteArray(WAV_HEADER_SIZE))
        try {
            audioRecord.startRecording()
        } catch (error: Throwable) {
            runCatching { output.close() }
            runCatching { audioRecord.release() }
            file.delete()
            throw error
        }
        pcmFile = file
        pcmRecorder = audioRecord
        pcmActive = true
        val thread = Thread({
            val buffer = ByteArray(bufferSize)
            var written = 0L
            try {
                while (pcmActive && written < MAX_PCM_BYTES) {
                    val remaining = (MAX_PCM_BYTES - written).toInt().coerceAtMost(buffer.size)
                    if (remaining <= 0) break
                    val read = audioRecord.read(buffer, 0, remaining)
                    if (read > 0) {
                        output.write(buffer, 0, read)
                        written += read
                    } else if (read < 0) {
                        break
                    }
                }
            } catch (_: Throwable) {
                // A stop() from another thread interrupts read(); the WAV header still gets fixed up.
            } finally {
                runCatching { output.flush() }
                runCatching { output.close() }
                runCatching { audioRecord.stop() }
                runCatching { audioRecord.release() }
                runCatching { writeWavHeader(file, written.toInt(), PCM_SAMPLE_RATE) }
                if (pcmThread === Thread.currentThread()) {
                    pcmThread = null
                    pcmRecorder = null
                }
            }
        }, "wraive-pcm-recorder")
        pcmThread = thread
        thread.start()
    }

    suspend fun stopAndTranscribe(config: TranscriptionConfig): String =
        withContext(Dispatchers.IO) {
            if (config.engine == TranscriptionEngine.DEEPSEEK) {
                stopPcmAndTranscribe(config)
            } else {
                stopMediaAndTranscribe(config)
            }
        }

    private fun stopPcmAndTranscribe(config: TranscriptionConfig): String {
        val file = pcmFile ?: error("没有正在进行的录音")
        pcmFile = null
        stopPcmRecording()
        try {
            require(file.length() > WAV_HEADER_SIZE) { "录音内容为空" }
            require(config.apiKey.isNotBlank()) { "请先填写 DeepSeek API Key" }
            return transcribeDeepSeek(file, config)
        } finally {
            file.delete()
        }
    }

    private fun stopMediaAndTranscribe(config: TranscriptionConfig): String {
        val active = recorder ?: error("没有正在进行的录音")
        val file = recordingFile ?: error("录音文件不存在")
        recorder = null
        recordingFile = null
        try {
            active.stop()
            active.release()
            require(file.length() > 0) { "录音内容为空" }
            require(config.apiKey.isNotBlank()) { "请先填写转写服务 API Key" }
            return when (config.engine) {
                TranscriptionEngine.OPENAI -> transcribeOpenAi(file, config)
                TranscriptionEngine.GOOGLE -> transcribeGoogle(file, config)
                TranscriptionEngine.DEEPSEEK -> error("DeepSeek 使用 PCM 录音")
                TranscriptionEngine.SYSTEM -> error("系统语音输入不需要录音上传")
            }
        } finally {
            runCatching { active.release() }
            file.delete()
        }
    }

    fun cancel() {
        recorder?.let {
            runCatching { it.stop() }
            it.release()
        }
        recorder = null
        recordingFile?.delete()
        recordingFile = null
        stopPcmRecording()
        pcmFile?.delete()
        pcmFile = null
    }

    private fun stopPcmRecording() {
        pcmActive = false
        runCatching { pcmRecorder?.stop() }
        val thread = pcmThread
        if (thread != null && thread !== Thread.currentThread()) {
            runCatching { thread.join(3_000) }
        }
        pcmRecorder = null
        pcmThread = null
    }

    private fun transcribeOpenAi(file: File, config: TranscriptionConfig): String {
        val endpoint = config.endpoint.ifBlank {
            "https://api.openai.com/v1/audio/transcriptions"
        }
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("model", config.model.ifBlank { "gpt-4o-mini-transcribe" })
            .addFormDataPart("language", config.languageCode.substringBefore('-'))
            .addFormDataPart(
                "file",
                file.name,
                file.readBytes().toRequestBody("audio/amr-wb".toMediaType())
            )
            .build()
        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer ${config.apiKey}")
            .post(body)
            .build()
        return httpClient.newCall(request).execute().requireSuccess().use { response ->
            parseJson(response.body.string()).asJsonObject.get("text")?.asString
                ?: error("转写服务没有返回文本")
        }
    }

    private fun transcribeDeepSeek(file: File, config: TranscriptionConfig): String {
        val endpoint = config.endpoint.ifBlank { DEEPSEEK_ASR_ENDPOINT }
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                file.name,
                file.readBytes().toRequestBody("audio/wav".toMediaType())
            )
            .build()
        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer ${config.apiKey}")
            .post(body)
            .build()
        return httpClient.newCall(request).execute().requireSuccess().use { response ->
            parseJson(response.body.string()).asJsonObject.get("text")?.asString
                ?: error("转写服务没有返回文本")
        }
    }

    private fun transcribeGoogle(file: File, config: TranscriptionConfig): String {
        val endpoint = config.endpoint.ifBlank {
            "https://speech.googleapis.com/v1/speech:recognize"
        }.toHttpUrl().newBuilder().addQueryParameter("key", config.apiKey).build()
        val body = JsonObject().apply {
            add("config", JsonObject().apply {
                addProperty("encoding", "AMR_WB")
                addProperty("sampleRateHertz", 16_000)
                addProperty("languageCode", config.languageCode)
                config.model.takeIf(String::isNotBlank)?.let { addProperty("model", it) }
            })
            add("audio", JsonObject().apply {
                addProperty(
                    "content",
                    Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                )
            })
        }
        val request = Request.Builder().url(endpoint)
            .post(gson.toJson(body).toRequestBody("application/json".toMediaType()))
            .build()
        return httpClient.newCall(request).execute().requireSuccess().use { response ->
            val root = parseJson(response.body.string()).asJsonObject
            root.getAsJsonArray("results").orEmpty().joinToString(" ") { result ->
                result.asJsonObject.getAsJsonArray("alternatives")
                    ?.firstOrNull()?.asJsonObject?.get("transcript")?.asString.orEmpty()
            }.trim().ifBlank { error("转写服务没有返回文本") }
        }
    }

    private companion object {
        const val PCM_SAMPLE_RATE = 16_000
        const val WAV_HEADER_SIZE = 44
        const val MAX_PCM_BYTES = 3_840_000L
        const val DEEPSEEK_ASR_ENDPOINT = "https://api.deepseek.com/v1/tools/asr"
    }
}

private fun JsonArray?.orEmpty(): Iterable<com.google.gson.JsonElement> = this ?: emptyList()
