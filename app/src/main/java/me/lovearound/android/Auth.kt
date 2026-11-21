package me.lovearound.android

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

/** A signed-in Firebase account. Only the refresh token is kept; ID tokens live in memory. */
@Serializable
data class Session(val uid: String, val email: String? = null, val refreshToken: String)

class AuthError(val code: String) : Exception() {
    companion object {
        val SignedOut = AuthError("SIGNED_OUT")
        val Network = AuthError("NETWORK")
    }

    override val message: String
        get() {
            if (this.code == "SIGNED_OUT") return tr("Нужно войти заново")
            if (this.code == "NETWORK") return tr("Нет связи, проверь интернет")
            return when (val first = code.split(" ").first()) {
                "TOO_MANY_ATTEMPTS_TRY_LATER" -> tr("Слишком много попыток, попробуй чуть позже")
                "USER_DISABLED" -> tr("Этот аккаунт отключён")
                "INVALID_PASSWORD", "INVALID_LOGIN_CREDENTIALS" ->
                    tr("Неверный пароль. Если раньше вход был через Google или Apple, нажми «Отмена» и войди так же")
                "WEAK_PASSWORD" -> tr("Пароль слишком простой")
                "INVALID_EMAIL", "MISSING_EMAIL" -> tr("Проверь адрес почты")
                "MISSING_PASSWORD" -> tr("Введи пароль")
                "EMAIL_EXISTS" -> tr("С этой почтой уже есть аккаунт. Войди так же, как в прошлый раз.")
                else -> tr("Не получилось войти ({code})", "code" to first)
            }
        }

    /** Errors after which the stored session is useless. */
    val endsSession: Boolean
        get() = code == "SIGNED_OUT" || listOf("TOKEN_EXPIRED", "USER_DISABLED", "USER_NOT_FOUND", "INVALID_REFRESH_TOKEN", "INVALID_GRANT_TYPE")
            .any { code.startsWith(it) }
}

/**
 * Firebase Auth over its REST API, as in the Mac and iPhone apps: Google
 * (Credential Manager gives the ID token), email with a password, and
 * Apple through the site's sign-in page (SiteSignIn).
 */
class Auth(context: Context) {
    private val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)
    var session: Session? = null
        private set
    @Volatile private var idToken: String? = null
    @Volatile private var expiresAt = 0L
    private val lock = Mutex()
    /** A test run's ID token, given at launch: nothing is saved or refreshed. */
    private var borrowed = false

    init {
        prefs.getString("session", null)?.let {
            session = try { json.decodeFromString(Session.serializer(), it) } catch (e: Exception) { null }
        }
    }

    /** Debug builds only: sign in for an hour with a test account's ID token. */
    fun borrow(token: String) {
        val claims = claims(token)
        session = Session(uid = claims.optString("user_id"), email = claims.optString("email").ifEmpty { null }, refreshToken = "")
        idToken = token
        expiresAt = claims.optLong("exp", System.currentTimeMillis() / 1000 + 1800) * 1000
        borrowed = true
    }

    /** A valid ID token, refreshed when it is about to expire. */
    suspend fun token(forceRefresh: Boolean = false): String {
        if (borrowed) {
            val t = idToken
            if (t == null || expiresAt < System.currentTimeMillis()) throw AuthError.SignedOut
            return t
        }
        lock.withLock {
            val t = idToken
            if (!forceRefresh && t != null && expiresAt - System.currentTimeMillis() > 300_000) return t
            return refresh()
        }
    }

    private suspend fun refresh(): String {
        val s = session ?: throw AuthError.SignedOut
        val form = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", s.refreshToken)
            .build()
        val req = Request.Builder()
            .url("https://securetoken.googleapis.com/v1/token?key=${Config.FIREBASE_API_KEY}")
            .post(form).build()
        val body = call(req)
        val id = body.optString("id_token").ifEmpty { throw AuthError("BAD_RESPONSE") }
        val refresh = body.optString("refresh_token").ifEmpty { throw AuthError("BAD_RESPONSE") }
        idToken = id
        expiresAt = System.currentTimeMillis() + (body.optString("expires_in").toLongOrNull() ?: 3600) * 1000
        if (refresh != s.refreshToken) {
            session = s.copy(refreshToken = refresh)
            save()
        }
        return id
    }

    /**
     * Google's ID token, from Credential Manager, for a Firebase session. One
     * account per email: an email whose account was made another way is
     * refused (EMAIL_EXISTS), and the person signs in that way.
     */
    suspend fun signInWithGoogle(googleIdToken: String) {
        val body = identity("accounts:signInWithIdp", JSONObject()
            .put("postBody", "id_token=$googleIdToken&providerId=google.com")
            .put("requestUri", "http://localhost")
            .put("returnIdpCredential", true)
            .put("returnSecureToken", true))
        if (body.optBoolean("needConfirmation")) throw AuthError("EMAIL_EXISTS")
        adopt(body)
    }

    /**
     * Email and password, as on the site: signs in, or makes the account if
     * the email has none. With email enumeration protection a wrong password
     * and a missing account look the same, so making it tells them apart.
     */
    suspend fun signInWithEmail(email: String, password: String) {
        val body = JSONObject().put("email", email).put("password", password).put("returnSecureToken", true)
        try {
            adopt(identity("accounts:signInWithPassword", body))
        } catch (e: AuthError) {
            if (listOf("INVALID_LOGIN_CREDENTIALS", "EMAIL_NOT_FOUND", "INVALID_PASSWORD").none { e.code.startsWith(it) }) throw e
            try {
                adopt(identity("accounts:signUp", body))
            } catch (made: AuthError) {
                // The account is there, so it was the password.
                if (made.code.startsWith("EMAIL_EXISTS")) throw AuthError("INVALID_PASSWORD")
                throw made
            }
        }
    }

    /** «Забыли пароль?»: Firebase mails a link for a new one. */
    suspend fun sendPasswordReset(email: String) {
        identity("accounts:sendOobCode", JSONObject().put("requestType", "PASSWORD_RESET").put("email", email))
    }

    /** A session the site's sign-in page handed back (Apple, or any other way). */
    fun adopt(uid: String, email: String?, refreshToken: String) {
        if (uid.isEmpty() || refreshToken.isEmpty()) throw AuthError("BAD_RESPONSE")
        adopt(Session(uid, email, refreshToken))
    }

    private fun adopt(body: JSONObject) {
        val uid = body.optString("localId")
        val refresh = body.optString("refreshToken")
        val id = body.optString("idToken")
        if (uid.isEmpty() || refresh.isEmpty() || id.isEmpty()) throw AuthError("BAD_RESPONSE")
        adopt(Session(uid, body.optString("email").ifEmpty { null }, refresh))
        idToken = id
        expiresAt = System.currentTimeMillis() + (body.optString("expiresIn").toLongOrNull() ?: 3600) * 1000
    }

    private fun adopt(s: Session) {
        session = s
        idToken = null
        expiresAt = 0
        borrowed = false
        save()
    }

    /** How this session signed in: "apple.com", "google.com" or "password"; null before the first token. */
    val provider: String?
        get() = idToken?.let { claims(it).optJSONObject("firebase")?.optString("sign_in_provider")?.ifEmpty { null } }

    fun signOut() {
        session = null
        idToken = null
        expiresAt = 0
        if (!borrowed) prefs.edit().remove("session").apply()
        borrowed = false
    }

    private fun save() {
        val s = session ?: return
        if (borrowed) return
        prefs.edit().putString("session", json.encodeToString(Session.serializer(), s)).apply()
    }

    private suspend fun identity(method: String, body: JSONObject): JSONObject {
        val req = Request.Builder()
            .url("https://identitytoolkit.googleapis.com/v1/$method?key=${Config.FIREBASE_API_KEY}")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        return call(req)
    }

    private suspend fun call(req: Request): JSONObject = withContext(Dispatchers.IO) {
        val response = try { http.newCall(req).execute() } catch (e: IOException) { throw AuthError.Network }
        response.use {
            val text = it.body?.string() ?: ""
            val body = try { JSONObject(text) } catch (e: Exception) { JSONObject() }
            if (it.code !in 200..299) {
                throw AuthError(body.optJSONObject("error")?.optString("message")?.ifEmpty { null } ?: "HTTP_${it.code}")
            }
            body
        }
    }

    companion object {
        /** An ID token's payload, unverified: only for what the app shows. */
        fun claims(token: String): JSONObject {
            val parts = token.split(".")
            if (parts.size != 3) return JSONObject()
            return try {
                JSONObject(String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)))
            } catch (e: Exception) { JSONObject() }
        }
    }
}
