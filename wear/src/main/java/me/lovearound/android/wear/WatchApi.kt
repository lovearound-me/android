package me.lovearound.android.wear

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.lovearound.android.ApiError
import me.lovearound.android.Config
import me.lovearound.android.Contact
import me.lovearound.android.http
import me.lovearound.android.json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** The device was removed in «Устройства» (or its account deleted): sign in again. */
val ApiError.sessionGone: Boolean get() = status == 401

/** The token works, but this is switched off in the watch's card for now. */
val ApiError.switchedOff: Boolean get() = status == 403

/**
 * needed-server REST for a device (docs/schema.md). Everything goes with the
 * watch's own device token, except pairing, which has none yet.
 */
class WatchApi(private val token: String) {
    suspend fun contacts(): List<Contact> {
        @Serializable data class ListOf(val contacts: List<Contact>)
        return json.decodeFromString<ListOf>(call("GET", "v1/contacts", token)).contacts
    }

    suspend fun signal(contactId: String) {
        call("POST", "v1/events", token, buildJsonObject {
            put("type", "signal")
            put("to", buildJsonObject { put("contact", contactId) })
        })
    }

    /** 202 even with nobody to take it; 429 if one already went this hour. */
    suspend fun signalAnyone() {
        call("POST", "v1/events", token, buildJsonObject {
            put("type", "signal")
            put("to", buildJsonObject { put("anyone", true) })
        })
    }

    /** «Выйти»: the watch leaves the account's devices, its token stops working. */
    suspend fun removeSelf() { call("DELETE", "v1/devices/self", token) }

    /** What the watch's card on the phone shows: what it does, and the most it may do. */
    @Serializable
    data class Me(
        val capabilities: List<String>,
        @SerialName("requested_capabilities") val requested: List<String>? = null,
    ) {
        val receives: Boolean get() = "receive" in capabilities
    }

    suspend fun me(): Me = json.decodeFromString(call("GET", "v1/devices/self", token))

    /** «Получать сердечки»: the same switch as «Принимает» in its card on the phone. */
    suspend fun setCapabilities(capabilities: List<String>): Me =
        json.decodeFromString(call("PATCH", "v1/devices/self", token, buildJsonObject {
            put("capabilities", JsonArray(capabilities.map { JsonPrimitive(it) }))
        }))

    /** Hearts come to this watch through FCM; `silent`: «Вибрация» is off. */
    suspend fun registerPush(fcmToken: String, silent: Boolean) {
        call("PUT", "v1/devices/self/push", token, buildJsonObject {
            put("token", fcmToken)
            put("platform", "android")
            if (silent) put("silent", true)
        })
    }

    @Serializable
    data class PairingCode(
        /** Six digits to show. */
        val code: String,
        /** Kept here: only it gets the token, the digits alone never do. */
        val secret: String,
        /** lovearound.me/login/devices#<code>, for the QR. */
        val url: String,
        @SerialName("expires_in") val expiresIn: Int,
        @SerialName("poll_every") val pollEvery: Int,
    )

    sealed class PairingState {
        data object Waiting : PairingState()
        data class Paired(val session: Session) : PairingState()
        /** Expired, used or never existed: ask for a new code. */
        data object Gone : PairingState()
    }

    companion object {
        /** What this watch asks for: it shows people and sends, and takes hearts. */
        val deviceAsk: JsonObject
            get() = buildJsonObject {
                put("name", watchName())
                put("kind", "watch")
                put("capabilities", JsonArray(listOf(JsonPrimitive("receive"), JsonPrimitive("send"))))
            }

        /** The name its card on the phone starts with; the owner can rename it there. */
        fun watchName(): String = when (Build.MANUFACTURER.lowercase()) {
            "samsung" -> "Galaxy Watch"
            "google" -> if (Build.MODEL.startsWith("sdk", ignoreCase = true)) "Wear OS" else "Pixel Watch"
            else -> "Wear OS"
        }

        suspend fun startPairing(): PairingCode = json.decodeFromString(call("POST", "v1/pairing", null, deviceAsk))

        suspend fun pollPairing(secret: String): PairingState {
            @Serializable data class Paired(val token: String, val account: String? = null)
            val (status, text) = raw("POST", "v1/pairing/token", null, buildJsonObject { put("secret", secret) })
            return when (status) {
                202 -> PairingState.Waiting
                200 -> json.decodeFromString<Paired>(text).let { PairingState.Paired(Session(it.token, it.account)) }
                404, 410 -> PairingState.Gone
                else -> { check(status, text); PairingState.Waiting }
            }
        }

        private suspend fun call(method: String, path: String, bearer: String?, body: JsonElement? = null): String {
            val (status, text) = raw(method, path, bearer, body)
            check(status, text)
            return text
        }

        private fun check(status: Int, text: String) {
            if (status in 200..299) return
            val error = try {
                json.parseToJsonElement(text).jsonObject["error"]?.jsonPrimitive?.content ?: ""
            } catch (e: Exception) { "" }
            throw ApiError.Status(status, error)
        }

        private suspend fun raw(method: String, path: String, bearer: String?, body: JsonElement?): Pair<Int, String> =
            withContext(Dispatchers.IO) {
                val builder = Request.Builder().url(Config.API_BASE + path).header("X-Client", Config.client)
                bearer?.let { builder.header("Authorization", "Bearer $it") }
                val payload = body?.toString()?.toRequestBody("application/json".toMediaType())
                builder.method(method, payload ?: if (method in setOf("POST", "PUT", "PATCH")) ByteArray(0).toRequestBody() else null)
                try {
                    http.newCall(builder.build()).execute().use { it.code to (it.body?.string() ?: "") }
                } catch (e: IOException) {
                    throw ApiError.Network
                }
            }
    }
}
