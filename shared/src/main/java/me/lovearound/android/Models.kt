package me.lovearound.android

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** A person in the address book, as the server gives it to a device. */
@Serializable
data class Contact(
    /** The server's id for this entry; the person's own id never leaves it. */
    val id: String,
    val label: String,
    val position: Int = 0,
    /** A close person: a circle first. Older servers leave it out. */
    val close: Boolean? = null,
    /** The owner's color for them, an index into `Palette`. */
    val color: Int? = null,
    /** The person's Love ID link, as it was added. */
    @SerialName("invite_url") val inviteUrl: String? = null,
) {
    val isClose: Boolean get() = close ?: false
}

/** One of the person's own devices: a watch, a lamp, a board. */
@Serializable
data class Device(
    val id: String,
    val kind: String? = null,
    val name: String,
    val capabilities: List<String> = emptyList(),
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
    /** What a device paired by code asked for: the most it may do. */
    @SerialName("requested_capabilities") val requestedCapabilities: List<String>? = null,
    @SerialName("accept_anyone") val acceptAnyone: Boolean? = null,
    @SerialName("quiet_hours") val quietHours: QuietHours? = null,
) {
    val receives: Boolean get() = "receive" in capabilities
    val sends: Boolean get() = "send" in capabilities
    val paired: Boolean get() = requestedCapabilities != null
    fun may(capability: String): Boolean = requestedCapabilities?.contains(capability) ?: true
}

/**
 * A daily window with no hearts at all, in the time zone of the phone that
 * set it. Nothing is held for later.
 */
@Serializable
data class QuietHours(val from: String, val to: String, val tz: String) {
    /** Whether `instant` falls in the window, `from` inclusive: as the server's quiet.rs. */
    fun contains(instant: Instant = Instant.now()): Boolean {
        val from = minutes(from) ?: return false
        val to = minutes(to) ?: return false
        if (from == to) return false
        val zone = try { ZoneId.of(tz) } catch (e: Exception) { ZoneId.systemDefault() }
        val local = ZonedDateTime.ofInstant(instant, zone)
        val now = local.hour * 60 + local.minute
        return if (from < to) now in from until to else (now >= from || now < to)
    }

    companion object {
        fun minutes(hhmm: String): Int? {
            val parts = hhmm.split(":").mapNotNull { it.toIntOrNull() }
            if (parts.size != 2 || parts[0] !in 0..23 || parts[1] !in 0..59) return null
            return parts[0] * 60 + parts[1]
        }
    }
}

@Serializable
data class Me(val uid: String, @SerialName("invite_url") val inviteUrl: String)

/** What a device showing a code asked for (POST /v1/pairing/find). */
@Serializable
data class PairingCard(val name: String, val kind: String? = null, val capabilities: List<String> = emptyList())
