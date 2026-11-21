package me.lovearound.android

import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
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
