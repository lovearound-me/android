package me.lovearound.android

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit

/** What the server or the network said no with. */
sealed class ApiError : Exception() {
    data class Status(val code: Int, val error: String) : ApiError()
    data object Network : ApiError()

    /** A 13th close person: «Рядом» holds 12. */
    val tooManyClose: Boolean get() = this is Status && code == 409 && error == "too many close"

    val status: Int get() = (this as? Status)?.code ?: 0

    override val message: String
        get() {
            if (this is Network) return tr("Нет связи, проверь интернет")
            val s = this as Status
            if (s.code == 400 && s.error == "that is your own invite") return tr("Это твоя собственная ссылка")
            if (s.code == 400 && s.error == "not an invite code") return tr("Не похоже на ссылку-приглашение")
            if (s.code == 400 && s.error == "too many contacts") return tr("В списке уже 200 человек")
            if (tooManyClose) return tr("Рядом уже 12 человек: сначала убери кого-нибудь.")
            return when (s.code) {
                401 -> tr("Сервер не принял вход, попробуй чуть позже")
                404 -> tr("Не нашли")
                409 -> tr("Уже есть в списке")
                429 -> tr("Слишком много попыток, попробуй через час")
                else -> tr("Сервер не ответил, попробуй ещё раз")
            }
        }
}

val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

val http: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .build()

/** needed-server's REST API (server repo, docs/schema.md), with a Firebase ID token. */
class Api(private val auth: Auth) {
    suspend fun me(): Me = json.decodeFromString(request("GET", "v1/me"))

    suspend fun contacts(): List<Contact> {
        @Serializable data class ListOf(val contacts: List<Contact>)
        return json.decodeFromString<ListOf>(request("GET", "v1/contacts")).contacts
    }

    /** Answers the same whether or not the code belongs to anyone. */
    suspend fun addContact(code: String, label: String, close: Boolean, color: Int): Contact =
        json.decodeFromString(request("POST", "v1/contacts", buildJsonObject {
            put("code", code); put("label", label); put("close", close); put("color", color)
        }))

    suspend fun updateContact(id: String, label: String? = null, position: Int? = null, close: Boolean? = null, color: Int? = null) {
        request("PATCH", "v1/contacts/$id", buildJsonObject {
            label?.let { put("label", it) }
            position?.let { put("position", it) }
            close?.let { put("close", it) }
            color?.let { put("color", it) }
        })
    }

    suspend fun deleteContact(id: String) { request("DELETE", "v1/contacts/$id") }

    suspend fun signal(contactId: String) {
        request("POST", "v1/events", buildJsonObject {
            put("type", "signal")
            put("to", buildJsonObject { put("contact", contactId) })
        })
    }

    /** 202 even with nobody online to take it; 429 if one already went this hour. */
    suspend fun signalAnyone() {
        request("POST", "v1/events", buildJsonObject {
            put("type", "signal")
            put("to", buildJsonObject { put("anyone", true) })
        })
    }

    suspend fun devices(): List<Device> {
        @Serializable data class ListOf(val devices: List<Device>)
        return json.decodeFromString<ListOf>(request("GET", "v1/devices")).devices
    }

    /** The token comes back only here, once. */
    suspend fun addDevice(name: String, capabilities: List<String>): Pair<Device, String> {
        @Serializable data class Created(val device: Device, val token: String)
        val created = json.decodeFromString<Created>(request("POST", "v1/devices", buildJsonObject {
            put("name", name); put("kind", "custom")
            put("capabilities", kotlinx.serialization.json.JsonArray(capabilities.map { JsonPrimitive(it) }))
        }))
        return created.device to created.token
    }

    suspend fun deleteDevice(id: String) { request("DELETE", "v1/devices/$id") }

    suspend fun updateDevice(id: String, change: JsonObject): Device =
        json.decodeFromString(request("PATCH", "v1/devices/$id", change))

    /** A board's new token, shown only now; the old one stops working at once. */
    suspend fun newDeviceToken(id: String): String {
        @Serializable data class Issued(val token: String)
        return json.decodeFromString<Issued>(request("POST", "v1/devices/$id/token")).token
    }

    /** The card for six digits a device shows; 404 for a wrong or old code. */
    suspend fun findPairing(code: String): PairingCard =
        json.decodeFromString(request("POST", "v1/pairing/find", buildJsonObject { put("code", code) }))

    suspend fun confirmPairing(code: String) {
        request("POST", "v1/pairing/confirm", buildJsonObject { put("code", code) })
    }

    /**
     * Where this phone's notifications go, and when not (docs/schema.md,
     * /v1/push). The token is Firebase Cloud Messaging's address of the app
     * on this phone.
     */
    suspend fun putPush(token: String, anyone: Boolean, pausedUntil: Instant?, quietHours: QuietHours?) {
        request("PUT", "v1/push", buildJsonObject {
            put("token", token)
            put("platform", "android")
            put("sandbox", false)
            put("anyone", anyone)
            put("paused_until", pausedUntil?.let { JsonPrimitive(it.toString()) } ?: JsonNull)
            put("quiet_hours", quietHours?.let { json.encodeToJsonElement(QuietHours.serializer(), it) } ?: JsonNull)
        })
    }

    /** This phone takes no more notifications for the account. */
    suspend fun deletePush(token: String) {
        request("DELETE", "v1/push", buildJsonObject { put("token", token); put("platform", "android") })
    }

    suspend fun deleteMe() { request("DELETE", "v1/me") }

    private suspend fun request(method: String, path: String, body: JsonElement? = null): String = withContext(Dispatchers.IO) {
        for (attempt in 0..1) {
            val token = auth.token(forceRefresh = attempt > 0)
            val builder = Request.Builder()
                .url(Config.API_BASE + path)
                .header("Authorization", "Bearer $token")
                .header("X-Client", Config.client)
            val payload = body?.toString()?.toRequestBody("application/json".toMediaType())
            builder.method(method, payload ?: if (method == "POST" || method == "PUT" || method == "PATCH") ByteArray(0).toRequestBody() else null)
            val response = try {
                http.newCall(builder.build()).execute()
            } catch (e: IOException) {
                throw ApiError.Network
            }
            response.use {
                val text = it.body?.string() ?: ""
                // Once more with a fresh token. Refused again, it stays a 401 and
                // the session is kept: the fault is on the server's side.
                if (it.code == 401 && attempt == 0) return@use
                if (it.code !in 200..299) {
                    val error = try {
                        json.parseToJsonElement(text).jsonObject["error"]?.jsonPrimitive?.content ?: ""
                    } catch (e: Exception) { "" }
                    throw ApiError.Status(it.code, error)
                }
                return@withContext text
            }
        }
        throw ApiError.Status(401, "unauthorized")
    }
}
