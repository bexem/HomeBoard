package com.evofactory.homeboard

import java.util.Random

/**
 * The order this session shows: the camera on the Home tab, the top bar's actions, the Home Assistant
 * cards. Rolled once when the launcher starts after the TV has been off for a while, and again after
 * the configured number of hours of it being left on — never on a timer that fires while someone is
 * using the remote, so nothing moves under the selection.
 */
object Shuffle {
    /**
     * The seed to use now, rolling a new one when this counts as a new session or the hours are up.
     * Called once, when the launcher starts.
     */
    fun rollIfDue(prefs: LauncherPrefs): Int {
        val now = System.currentTimeMillis()
        val last = prefs.shuffleRolledAt
        val elapsed = now - last
        val newSession = last == 0L || elapsed > SHUFFLE_SESSION_GAP_MS
        val hoursUp = prefs.shuffleHours > 0 && elapsed > prefs.shuffleHours * 3_600_000L
        AppLog.i("Shuffle", "last=$last elapsed=${elapsed / 1000}s newSession=$newSession hoursUp=$hoursUp stored=${prefs.shuffleSeed}")
        if (newSession || hoursUp) {
            val seed = Random().nextInt()
            prefs.shuffleSeed = seed
            prefs.shuffleRolledAt = now
            AppLog.i("Shuffle", "rolled $seed")
            return seed
        }
        return prefs.shuffleSeed
    }

    /** [items] in the session's order, when shuffling that area is on; otherwise untouched */
    fun <T> order(items: List<T>, seed: Int, enabled: Boolean, salt: Int = 0): List<T> =
        if (!enabled || items.size < 2) items else items.shuffled(Random((seed xor (salt * 0x9E3779B9.toInt())).toLong()))
}
