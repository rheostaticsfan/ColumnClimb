package com.moira.cantstop

import android.content.Context

/**
 * The whole board as one immutable value. Used both for undo snapshots and for saving to disk.
 */
data class GameState(
    val playerCount: Int,
    val currentPlayer: Int,
    val progress: List<List<Int>>,
    val claimedBy: List<Int>,
    val runners: Map<Int, Int>,
) {
    companion object {
        fun fresh(playerCount: Int = MAX_PLAYERS) = GameState(
            playerCount = playerCount,
            currentPlayer = 0,
            progress = List(MAX_PLAYERS) { List(COLUMN_COUNT) { 0 } },
            claimedBy = List(COLUMN_COUNT) { UNCLAIMED },
            runners = emptyMap(),
        )
    }
}

/**
 * Saves the board to SharedPreferences as a single short string, e.g.
 *
 *     1|4|0|-1,-1,-1,-1,0,-1,-1,-1,-1,1,-1|0,2,2,0,11,...;1,0,4,...|2:3,5:8,7:4
 *      ^ ^ ^ ^                             ^                        ^
 *      | | | claimed-by per column         progress per player      neutral runners
 *      | | current player
 *      | player count
 *      format version
 *
 * Anything malformed is treated as "no saved game" rather than crashing, so a bad or
 * outdated save just starts a fresh board.
 */
class GameStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(state: GameState) {
        prefs.edit().putString(KEY, encode(state)).apply()
    }

    fun load(): GameState? = try {
        prefs.getString(KEY, null)?.let(::decode)
    } catch (e: ClassCastException) {
        null
    }

    private fun encode(s: GameState): String = listOf(
        VERSION.toString(),
        s.playerCount.toString(),
        s.currentPlayer.toString(),
        s.claimedBy.joinToString(","),
        s.progress.joinToString(";") { row -> row.joinToString(",") },
        s.runners.entries.joinToString(",") { (col, pos) -> "$col:$pos" },
    ).joinToString("|")

    private fun decode(raw: String): GameState? {
        val parts = raw.split("|")
        if (parts.size != 6 || parts[0].toIntOrNull() != VERSION) return null

        val playerCount = parts[1].toIntOrNull()?.takeIf { it in 2..MAX_PLAYERS } ?: return null
        val currentPlayer = parts[2].toIntOrNull()?.takeIf { it in 0 until playerCount } ?: return null

        val claimedBy = parts[3].split(",").map { it.toIntOrNull() ?: return null }
        if (claimedBy.size != COLUMN_COUNT) return null
        if (claimedBy.any { it != UNCLAIMED && it !in 0 until playerCount }) return null

        val progress = parts[4].split(";").map { row -> row.split(",").map { it.toIntOrNull() ?: return null } }
        if (progress.size != MAX_PLAYERS || progress.any { it.size != COLUMN_COUNT }) return null
        progress.forEach { row ->
            row.forEachIndexed { col, v -> if (v < 0 || v > columnHeight(col)) return null }
        }

        val runners = mutableMapOf<Int, Int>()
        if (parts[5].isNotEmpty()) {
            parts[5].split(",").forEach { pair ->
                val kv = pair.split(":")
                if (kv.size != 2) return null
                val col = kv[0].toIntOrNull() ?: return null
                val pos = kv[1].toIntOrNull() ?: return null
                if (col !in 0 until COLUMN_COUNT || pos < 1 || pos > columnHeight(col)) return null
                runners[col] = pos
            }
        }
        if (runners.size > RUNNER_COUNT) return null

        // Cross-field sanity: a runner can't sit on a closed column, nor at or below its own
        // player's marker. Normal play never produces either, but a restored backup might.
        if (runners.any { (col, pos) ->
                claimedBy[col] != UNCLAIMED || pos <= progress[currentPlayer][col]
            }
        ) return null

        return GameState(playerCount, currentPlayer, progress, claimedBy, runners)
    }

    private companion object {
        const val PREFS = "cant_stop_board"
        const val KEY = "game"
        const val VERSION = 1
    }
}
