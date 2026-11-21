package moye.wear.wraive.data

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moye.wear.wraive.model.AppPreferences
import moye.wear.wraive.model.CloudBackupConfig
import moye.wear.wraive.model.NetworkProxyConfig
import moye.wear.wraive.model.ProviderConfig
import moye.wear.wraive.model.SpeechConfig
import moye.wear.wraive.model.TranscriptionConfig
import moye.wear.wraive.model.availableApiKeys
import java.io.InputStream
import java.io.OutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class BackupService(
    private val repository: AppRepository,
    private val preferencesStore: PreferencesStore,
    private val gson: Gson
) {
    suspend fun exportBytes(): ByteArray {
        val output = ByteArrayOutputStream()
        export(output)
        return output.toByteArray()
    }

    suspend fun importBytes(bytes: ByteArray) {
        import(ByteArrayInputStream(bytes))
    }

    suspend fun export(output: OutputStream) = withContext(Dispatchers.IO) {
        val bundle = BackupBundle(
            exportedAt = System.currentTimeMillis(),
            preferences = preferencesStore.preferences.value,
            data = repository.snapshot()
        )
        output.writer(Charsets.UTF_8).use { gson.toJson(bundle, it) }
    }

    suspend fun import(input: InputStream) = withContext(Dispatchers.IO) {
        val root = input.reader(Charsets.UTF_8).use {
            JsonParser.parseReader(it).asJsonObject
        }
        val version = root.get("formatVersion")?.asInt ?: 1
        require(version in 1..FORMAT_VERSION) {
            "不支持的备份版本 $version"
        }
        root.getAsJsonObject("preferences")?.fillMissing(
            gson.toJsonTree(AppPreferences()).asJsonObject
        )
        val data = root.getAsJsonObject("data") ?: error("备份缺少数据")
        listOf(
            "providers", "models", "assistants", "conversations", "messages", "memories",
            "worldBookEntries", "quickPhrases", "searchProviders", "mcpServers",
            "promptTransforms", "requestLogs"
        ).forEach { name ->
            if (!data.has(name) || data.get(name).isJsonNull) {
                data.add(name, com.google.gson.JsonArray())
            }
        }
        mapOf(
            "speechConfig" to SpeechConfig(),
            "cloudBackupConfig" to CloudBackupConfig(),
            "networkProxyConfig" to NetworkProxyConfig(),
            "transcriptionConfig" to TranscriptionConfig()
        ).forEach { (name, default) ->
            if (!data.has(name) || data.get(name).isJsonNull) data.add(name, gson.toJsonTree(default))
        }
        val bundle = gson.fromJson(root, BackupBundle::class.java)
        repository.restore(bundle.data)
        preferencesStore.replace(preferencesStore.normalized(bundle.preferences))
    }

    data class BackupBundle(
        val formatVersion: Int = FORMAT_VERSION,
        val exportedAt: Long,
        val preferences: AppPreferences,
        val data: RepositorySnapshot
    )

    private companion object {
        const val FORMAT_VERSION = 2
    }
}

private fun JsonObject.fillMissing(defaults: JsonObject) {
    defaults.entrySet().forEach { (name, value) ->
        if (!has(name) || get(name).isJsonNull) add(name, value)
    }
}

class ProviderShareCodec(private val gson: Gson) {
    fun encode(provider: ProviderConfig): String {
        val json = gson.toJson(provider).toByteArray(Charsets.UTF_8)
        val iv = ByteArray(GCM_IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, shareKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        val encrypted = cipher.doFinal(json)
        val payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(iv + encrypted)
        return PREFIX_V2 + payload
    }

    fun decode(value: String): ProviderConfig {
        val text = value.trim()
        require(text.isNotEmpty()) { "分享文本为空" }
        val json = when {
            text.startsWith(PREFIX_V2) -> decrypt(text.removePrefix(PREFIX_V2))
            // Older plain-base64 shares stay importable.
            text.startsWith(PREFIX_V1) -> String(
                Base64.getUrlDecoder().decode(text.removePrefix(PREFIX_V1)),
                Charsets.UTF_8
            )
            else -> throw IllegalArgumentException("不是 Wraive 分享文本")
        }
        val provider = runCatching { gson.fromJson(json, ProviderConfig::class.java) }
            .getOrNull() ?: error("分享文本解析失败")
        val rotation = runCatching { provider.keyRotation.name }
            .mapCatching(moye.wear.wraive.model.KeyRotationStrategy::valueOf)
            .getOrDefault(moye.wear.wraive.model.KeyRotationStrategy.ROUND_ROBIN)
        return provider.copy(
            id = provider.id.orEmpty().ifBlank { java.util.UUID.randomUUID().toString() },
            name = provider.name.orEmpty().ifBlank { "Provider" },
            baseUrl = provider.baseUrl.orEmpty(),
            apiKey = provider.availableApiKeys().firstOrNull().orEmpty(),
            apiKeys = provider.availableApiKeys().drop(1),
            keyRotation = rotation,
            group = provider.group.orEmpty(),
            customHeaders = provider.customHeaders.orEmpty(),
            customBody = provider.customBody.orEmpty()
        )
    }

    private fun decrypt(payload: String): String {
        val bytes = runCatching { Base64.getUrlDecoder().decode(payload) }
            .getOrElse { throw IllegalArgumentException("分享文本格式无效") }
        require(bytes.size > GCM_IV_BYTES) { "分享文本已损坏" }
        val iv = bytes.copyOfRange(0, GCM_IV_BYTES)
        val body = bytes.copyOfRange(GCM_IV_BYTES, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, shareKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        val plain = runCatching { cipher.doFinal(body) }.getOrElse {
            throw IllegalArgumentException("分享文本无法解密，可能已损坏或来自其他应用")
        }
        return String(plain, Charsets.UTF_8)
    }

    private companion object {
        const val PREFIX_V1 = "wraive://provider?data="
        const val PREFIX_V2 = "wraive://provider?v=2&data="
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
        val shareKey: SecretKeySpec by lazy {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest("wraive-provider-share-v2".toByteArray(Charsets.UTF_8))
            SecretKeySpec(digest, "AES")
        }
    }
}
