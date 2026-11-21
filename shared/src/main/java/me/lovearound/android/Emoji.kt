package me.lovearound.android

/** The emoji of an incoming heart, on screen and in its notification: hearts only. */
object Emoji {
    /** Pinks and reds: the wave's hearts, and what floats up on screen. */
    val pink = listOf("💖", "💕", "💗", "💞", "💓", "💘", "🩷", "❤️", "💝")
    /** «Одно сердечко»: one of these, new each time. */
    val hearts = listOf("❤️", "🧡", "💛", "💚", "💙", "💜", "💖", "💕", "💗")
    val stars = listOf("⭐️", "🌟", "✨", "💫")

    /** «Волна сердечек»: ✨💗💖💞💖💗✨, new each time. */
    fun wave(): String {
        val h = pink.shuffled()
        return "✨" + h[0] + h[1] + h[2] + h[1] + h[0] + "✨"
    }

    /** «Три сердечка»: the wave's middle, 💕💖💕. */
    fun three(): String {
        val h = pink.shuffled()
        return h[0] + h[1] + h[0]
    }
}
