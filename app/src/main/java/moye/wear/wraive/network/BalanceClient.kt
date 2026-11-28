package moye.wear.wraive.network

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moye.wear.wraive.model.ProviderConfig
import moye.wear.wraive.model.availableApiKeys
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface BalanceState {
    data object Idle : BalanceState
    data object Loading : BalanceState
    data class Amount(val amount: String, val providerName: String) : BalanceState
    data class Unavailable(val message: String) : BalanceState
    data class Failure(val message: String) : BalanceState
}

/**
 * Queries the account balance of the providers that expose a public balance endpoint.
 * Endpoints follow cc-switch's balance service; providers without one stay unsupported.
 */
object BalanceClient {
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun query(provider: ProviderConfig): BalanceState = withContext(Dispatchers.IO) {
        val apiKey = provider.availableApiKeys().firstOrNull()
            ?: return@withContext BalanceState.Unavailable("未填写 API Key")
        val host = runCatching { URI(provider.baseUrl).host?.lowercase() }.getOrNull()
            ?: return@withContext BalanceState.Unavailable("Base URL 无效")
        runCatching {
            when {
                host.endsWith("api.deepseek.com") -> deepSeek(apiKey)
                host.endsWith("openrouter.ai") -> openRouter(apiKey)
                host.endsWith("api.siliconflow.cn") || host.endsWith("api.siliconflow.com") ->
                    siliconFlow(apiKey)
                host.endsWith("api.stepfun.com") || host.endsWith("api.stepfun.ai") ->
                    stepFun(apiKey)
                host.endsWith("api.novita.ai") -> novita(apiKey)
                host.endsWith("api.xiaomimimo.com") -> mimo(apiKey)
                else -> BalanceState.Unavailable("该供应商未提供余额接口")
            }
        }.getOrElse { error ->
            BalanceState.Failure(error.message ?: "余额查询失败")
        }
    }

    private fun deepSeek(apiKey: String): BalanceState {
        val root = getJson("https://api.deepseek.com/user/balance", apiKey)
        val infos = root.getAsJsonArray("balance_infos") ?: error("响应缺少 balance_infos")
        val wallets = infos.map { it.asJsonObject }
        val wallet = wallets.maxByOrNull { it.double("total_balance") ?: -1.0 }
            ?: error("余额为空")
        val currency = wallet.string("currency") ?: "CNY"
        val total = wallet.double("total_balance") ?: error("响应缺少 total_balance")
        return BalanceState.Amount(money(total, currency), "DeepSeek")
    }

    private fun openRouter(apiKey: String): BalanceState {
        val root = getJson("https://openrouter.ai/api/v1/credits", apiKey)
        val data = root.getAsJsonObject("data") ?: root
        val credits = data.double("total_credits") ?: error("响应缺少 total_credits")
        val usage = data.double("total_usage") ?: error("响应缺少 total_usage")
        return BalanceState.Amount(money(credits - usage, "USD"), "OpenRouter")
    }

    private fun siliconFlow(apiKey: String): BalanceState {
        val cn = true
        val root = getJson("https://api.siliconflow.cn/v1/user/info", apiKey)
        val data = root.getAsJsonObject("data") ?: error("响应缺少 data")
        val balance = data.double("totalBalance") ?: error("响应缺少 totalBalance")
        return BalanceState.Amount(money(balance, if (cn) "CNY" else "USD"), "SiliconFlow")
    }

    private fun stepFun(apiKey: String): BalanceState {
        val root = getJson("https://api.stepfun.com/v1/accounts", apiKey)
        val balance = root.double("balance") ?: error("响应缺少 balance")
        return BalanceState.Amount(money(balance, "CNY"), "StepFun")
    }

    private fun novita(apiKey: String): BalanceState {
        val root = getJson("https://api.novita.ai/v3/user/balance", apiKey)
        val raw = root.double("availableBalance") ?: error("响应缺少 availableBalance")
        // Novita reports 0.0001 USD units.
        return BalanceState.Amount(money(raw / 10_000.0, "USD"), "Novita AI")
    }

    private fun mimo(apiKey: String): BalanceState {
        val root = getJson("https://api.xiaomimimo.com/v1/platform/balance", apiKey)
        val infos = root.getAsJsonArray("balance_infos") ?: error("响应缺少 balance_infos")
        val wallet = infos.map { it.asJsonObject }
            .maxByOrNull { it.double("total_available") ?: -1.0 }
            ?: error("余额为空")
        val total = wallet.double("total_available") ?: error("响应缺少 total_available")
        val frozen = wallet.double("frozen_balance") ?: 0.0
        val currency = wallet.string("currency") ?: "CNY"
        return BalanceState.Amount(money(total - frozen, currency), "Xiaomi MiMo")
    }

    private fun getJson(url: String, apiKey: String): JsonObject {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "application/json")
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("HTTP ${response.code}")
            return JsonParser.parseString(body).asJsonObject
        }
    }

    private fun JsonObject.double(name: String): Double? =
        get(name)?.takeIf { it.isJsonPrimitive }?.asDouble

    private fun JsonObject.string(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive }?.asString

    private fun money(value: Double, currency: String): String {
        val symbol = when (currency.uppercase()) {
            "CNY", "RMB" -> "¥"
            "USD" -> "$"
            else -> "$currency "
        }
        return symbol + String.format(java.util.Locale.US, "%.2f", value)
    }
}
