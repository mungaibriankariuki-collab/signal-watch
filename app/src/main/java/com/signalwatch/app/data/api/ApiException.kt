package com.signalwatch.app.data.api

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import retrofit2.HttpException

class ApiException(val httpCode: Int, val reason: String, message: String) : Exception(message) {

    val isQuotaExceeded: Boolean
        get() = reason == "quotaExceeded" || reason == "dailyLimitExceeded"

    /** The chat is over or gone: liveChatEnded, liveChatNotFound, liveChatDisabled, or a bare 403/404. */
    val isChatGone: Boolean
        get() = !isQuotaExceeded && reason != "rateLimitExceeded" &&
            (reason in CHAT_GONE_REASONS || httpCode == 403 || httpCode == 404)

    companion object {
        private val CHAT_GONE_REASONS = setOf("liveChatEnded", "liveChatNotFound", "liveChatDisabled", "forbidden")

        fun from(e: HttpException, json: Json): ApiException {
            val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
            val error = body?.let { runCatching { json.decodeFromString<ApiErrorEnvelope>(it).error }.getOrNull() }
            val reason = error?.errors?.firstOrNull()?.reason.orEmpty()
            val message = error?.message?.takeIf { it.isNotBlank() } ?: "HTTP ${e.code()}"
            return ApiException(e.code(), reason, message)
        }
    }
}
