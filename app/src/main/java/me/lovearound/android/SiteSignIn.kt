package me.lovearound.android

import android.content.Context
import android.net.Uri
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigInteger
import java.util.Base64
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Sign-in through the site's page (lovearound.me/signin), for Apple, which
 * has no sign-in of its own on Android. The app opens the page with a
 * random `state` and a one-time public key; the page signs in and hands the
 * session back as lovearound://signin?state=…&epk=…&box=…, sealed to that
 * key (see the comment at the top of site/src/signin/index.html): ECDH
 * P-256, HKDF-SHA256 with info "lovearound signin v1", AES-256-GCM with the
 * state as associated data. Only this app, holding the private key, can
 * open it.
 */
object SiteSignIn {
    private const val PREFS = "site-signin"

    class Opened(val uid: String, val email: String?, val refreshToken: String)

    /** The page's address; the private key waits in the app's own storage until the link comes back. */
    fun start(context: Context): Uri {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        val pair = generator.generateKeyPair()
        val state = b64(ByteArray(24).also { SecureRandom().nextBytes(it) })
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("state", state)
            .putString("key", b64(pair.private.encoded))
            .apply()
        return Uri.parse(Config.SITE + "/signin/").buildUpon()
            .appendQueryParameter("state", state)
            .appendQueryParameter("key", b64(raw(pair.public as ECPublicKey)))
            .build()
    }

    /** The session in lovearound://signin?…, or null if it isn't the one this app asked for. */
    fun finish(context: Context, uri: Uri): Opened? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val state = prefs.getString("state", null) ?: return null
        val key = prefs.getString("key", null) ?: return null
        if (uri.getQueryParameter("state") != state) return null
        val epk = uri.getQueryParameter("epk") ?: return null
        val box = uri.getQueryParameter("box") ?: return null
        prefs.edit().clear().apply()
        return open(unb64(key), state, epk, box)
    }

    /** The site's box opened with the app's private key (PKCS#8); null if it isn't ours. */
    internal fun open(key: ByteArray, state: String, epk: String, box: String): Opened? {
        return try {
            val factory = KeyFactory.getInstance("EC")
            val private = factory.generatePrivate(PKCS8EncodedKeySpec(key)) as ECPrivateKey
            val point = unb64(epk)
            if (point.size != 65 || point[0] != 4.toByte()) return null
            val theirs = factory.generatePublic(ECPublicKeySpec(
                ECPoint(BigInteger(1, point.copyOfRange(1, 33)), BigInteger(1, point.copyOfRange(33, 65))),
                private.params))
            val agreement = KeyAgreement.getInstance("ECDH")
            agreement.init(private)
            agreement.doPhase(theirs, true)
            val aes = hkdf(agreement.generateSecret(), "lovearound signin v1".toByteArray())
            val sealed = unb64(box)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aes, "AES"), GCMParameterSpec(128, sealed, 0, 12))
            cipher.updateAAD(state.toByteArray())
            val session = json.parseToJsonElement(String(cipher.doFinal(sealed, 12, sealed.size - 12))).jsonObject
            fun field(name: String) = session[name]?.jsonPrimitive?.contentOrNull?.ifEmpty { null }
            Opened(field("uid") ?: return null, field("email"), field("rt") ?: return null)
        } catch (e: Exception) {
            null
        }
    }

    /** HKDF-SHA256 with no salt, 32 bytes out: one block of expand. */
    private fun hkdf(secret: ByteArray, info: ByteArray): ByteArray {
        val extract = Mac.getInstance("HmacSHA256")
        extract.init(SecretKeySpec(ByteArray(32), "HmacSHA256"))
        val prk = extract.doFinal(secret)
        val expand = Mac.getInstance("HmacSHA256")
        expand.init(SecretKeySpec(prk, "HmacSHA256"))
        return expand.doFinal(info + byteArrayOf(1))
    }

    private fun raw(key: ECPublicKey): ByteArray {
        fun fixed(n: BigInteger): ByteArray {
            val bytes = n.toByteArray().dropWhile { it == 0.toByte() }.toByteArray()
            return ByteArray(32 - bytes.size) + bytes
        }
        return byteArrayOf(4) + fixed(key.w.affineX) + fixed(key.w.affineY)
    }

    private fun b64(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    internal fun unb64(text: String): ByteArray = Base64.getUrlDecoder().decode(text)
}
