package me.lovearound.android

/** Addresses and keys every part of the app shares; the iPhone app's Shared/Config.swift. */
object Config {
    /** needed-server on Cloud Run: REST, see the server's docs/schema.md. */
    const val API_BASE = "https://api.lovearound.me/"
    /** Firebase Web API key: public, identifies the project to Firebase Auth. */
    const val FIREBASE_API_KEY = "AIzaSyBZJfln_KXsxRxo3fvVaV1ePmiVUIn5YXY"
    /** The web app, where people manage their ways in. */
    const val WEB_APP = "app.lovearound.me"
    /** The site: invite links /i/<code>, the sign-in page /signin/, docs. */
    const val SITE = "https://lovearound.me"
    const val PRIVACY_PAGE = "$SITE/privacy"
    const val SUPPORT_PAGE = "$SITE/support"
    /** Links the app opens: lovearound://add/<code>, lovearound://signin?…. */
    const val URL_SCHEME = "lovearound"

    /** Sent as `X-Client` with every request, so the server logs which version sent what. */
    val client: String get() = "android/" + BuildConfig.VERSION_NAME
}
