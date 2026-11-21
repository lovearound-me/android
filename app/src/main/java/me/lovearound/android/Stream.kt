package me.lovearound.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * WS /v1/stream: incoming events, reconnecting with backoff, while the app
 * is in front (the Mac and iPhone apps' Stream). In the background, hearts
 * come as notifications.
 */
class Stream(context: Context, private val auth: Auth, private val scope: CoroutineScope) {
    var onSignal: () -> Unit = {}
    /** Take signals to anyone too; set before `start` or followed by `restart`. */
    var anyone = true
    var onAuthLost: () -> Unit = {}
    /** The book may have changed: on every (re)connect, and on a "sync" hint. */
    var onSync: () -> Unit = {}
    /** True once the server said "ready" on the current socket, false when it is gone. */
    var onOnline: (Boolean) -> Unit = {}

    // A socket can die without an error (Wi-Fi gone, a router dropped it):
    // a ping every 30 s and no pong closes it, and it comes back.
    private val client = http.newBuilder().pingInterval(30, TimeUnit.SECONDS).readTimeout(0, TimeUnit.SECONDS).build()
    private var socket: WebSocket? = null
    private var generation = 0
    private var backoff = 1L
    private var running = false
    private var refreshToken = false
    private var pending: Job? = null
    private var online = false
        set(value) {
            if (field != value) { field = value; onOnline(value) }
        }

    init {
        // Back online or on another network: the old socket is likely dead.
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        var known: Network? = null
        connectivity.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                scope.launch {
                    val changed = known != null && known != network
                    known = network
                    if (changed || !online) restart()
                }
            }
        })
    }

    fun start() {
        if (running) return
        running = true
        backoff = 1
        connect()
    }

    fun stop() {
        running = false
        teardown()
    }

    fun restart() {
        if (!running) return
        teardown()
        backoff = 1
        connect()
    }

    private fun teardown() {
        generation++
        online = false
        pending?.cancel()
        pending = null
        socket?.close(1001, null)
        socket = null
    }

    private fun connect() {
        generation++
        val gen = generation
        pending = scope.launch {
            val token = try {
                auth.token(forceRefresh = refreshToken).also { refreshToken = false }
            } catch (e: AuthError) {
                if (e.endsSession) { onAuthLost(); return@launch }
                scheduleReconnect(gen); return@launch
            } catch (e: Exception) {
                scheduleReconnect(gen); return@launch
            }
            if (!running || gen != generation) return@launch
            val url = Config.API_BASE.replace("https://", "wss://") + "v1/stream" + if (anyone) "" else "?anyone=false"
            val request = Request.Builder().url(url)
                .header("Authorization", "Bearer $token")
                .header("X-Client", Config.client)
                .build()
            socket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    scope.launch { if (gen == generation) handle(text) }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    scope.launch {
                        if (gen != generation) return@launch
                        if (response?.code == 401) refreshToken = true
                        scheduleReconnect(gen)
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    scope.launch { if (gen == generation) scheduleReconnect(gen) }
                }
            })
        }
    }

    private fun handle(text: String) {
        val msg = try { JSONObject(text) } catch (e: Exception) { return }
        when (msg.optString("type")) {
            "ready" -> { backoff = 1; online = true; onSync() }
            "sync" -> onSync()
            "event" -> {
                // Unknown types and versions are ignored, so old apps survive new events.
                val event = msg.optJSONObject("event") ?: return
                if (event.optString("type") == "signal" && event.optInt("v") == 1) onSignal()
            }
        }
    }

    private fun scheduleReconnect(gen: Int) {
        if (!running || gen != generation) return
        teardown()
        val next = generation
        val wait = backoff
        backoff = minOf(backoff * 2, 60)
        pending = scope.launch {
            delay(wait * 1000)
            if (running && generation == next) connect()
        }
    }
}
