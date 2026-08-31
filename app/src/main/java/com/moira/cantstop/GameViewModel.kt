package com.moira.cantstop

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import kotlin.math.abs

const val COLUMN_COUNT = 11
const val RUNNER_COUNT = 3
const val COLUMNS_TO_WIN = 3
const val MAX_PLAYERS = 4
const val UNCLAIMED = -1

val PLAYER_COLORS = listOf(
    Color(0xFFE53935), // red
    Color(0xFF2196F3), // blue
    Color(0xFF4CAF50), // green
    Color(0xFFFFEB3B), // yellow
)

val PLAYER_NAMES = listOf("Red", "Blue", "Green", "Yellow")

/** Column index 0..10 maps to the dice total 2..12. */
fun columnNumber(index: Int): Int = index + 2

/** Column heights for totals 2..12: 3, 5, 7, 9, 11, 13, 11, 9, 7, 5, 3. */
fun columnHeight(index: Int): Int = 13 - 2 * abs(7 - columnNumber(index))

/**
 * Board state only. The dice, the pressing-your-luck and the arguing happen on the table;
 * this class just remembers where the wood is — and writes it to disk after every move, so
 * a game survives the app being killed and only ends when you clear the board.
 */
class GameViewModel(app: Application) : AndroidViewModel(app) {

    private val store = GameStore(app)

    var playerCount by mutableStateOf(MAX_PLAYERS)
        private set

    var currentPlayer by mutableStateOf(0)
        private set

    /**
     * progress[player][column] = the space that player's colored marker occupies,
     * counted from the bottom of the column. 0 means "not on this column yet".
     */
    var progress by mutableStateOf(List(MAX_PLAYERS) { List(COLUMN_COUNT) { 0 } })
        private set

    /** claimedBy[column] = index of the player who closed the column, or UNCLAIMED. */
    var claimedBy by mutableStateOf(List(COLUMN_COUNT) { UNCLAIMED })
        private set

    /** column index -> space the neutral (white) runner sits on during this turn. */
    var runners by mutableStateOf(emptyMap<Int, Int>())
        private set

    /** Undo history is in memory only, so it starts empty after the app is killed. */
    private val history = ArrayDeque<GameState>()

    /** Mirrors history.size as observable state so the Undo button's enabled state stays honest. */
    private var historyDepth by mutableStateOf(0)

    init {
        store.load()?.let(::restore)
    }

    val canUndo: Boolean get() = historyDepth > 0

    val winner: Int?
        get() = (0 until playerCount).firstOrNull { p -> claimedCount(p) >= COLUMNS_TO_WIN }

    fun claimedCount(player: Int): Int = claimedBy.count { it == player }

    private fun snapshot() = GameState(playerCount, currentPlayer, progress, claimedBy, runners)

    private fun restore(s: GameState) {
        playerCount = s.playerCount
        currentPlayer = s.currentPlayer
        progress = s.progress
        claimedBy = s.claimedBy
        runners = s.runners
    }

    private fun push() {
        history.addLast(snapshot())
        if (history.size > 80) history.removeFirst()
        historyDepth = history.size
    }

    private fun persist() = store.save(snapshot())

    /**
     * Tap a column: drop a runner onto it, or step the runner already there up one space.
     * Refuses only what the physical board refuses — a closed column, a fourth runner,
     * or climbing past the top.
     */
    fun advance(column: Int) {
        if (winner != null) return
        if (claimedBy[column] != UNCLAIMED) return
        val top = columnHeight(column)
        val runner = runners[column]
        val next = when {
            runner != null -> if (runner >= top) return else runner + 1
            runners.size >= RUNNER_COUNT -> return
            else -> {
                val from = progress[currentPlayer][column]
                if (from >= top) return
                from + 1
            }
        }
        push()
        runners = runners + (column to next)
        persist()
    }

    /** Long-press a column: step its runner back one space, lifting it off at the start. */
    fun retreat(column: Int) {
        val runner = runners[column] ?: return
        push()
        val floor = progress[currentPlayer][column]
        runners = if (runner - 1 <= floor) runners - column else runners + (column to runner - 1)
        persist()
    }

    /** Stop: bank the runners into the current player's own markers, closing any column topped out. */
    fun stopAndBank() {
        if (winner != null) return
        push()
        if (runners.isNotEmpty()) {
            progress = progress.mapIndexed { p, row ->
                if (p != currentPlayer) row
                else row.toMutableList().also { r -> runners.forEach { (col, pos) -> r[col] = pos } }
            }
            claimedBy = claimedBy.toMutableList().also { c ->
                runners.forEach { (col, pos) -> if (pos >= columnHeight(col)) c[col] = currentPlayer }
            }
            runners = emptyMap()
        }
        advanceTurn()
        persist()
    }

    /** Bust: the runners come off, nothing is banked. */
    fun bust() {
        if (winner != null) return
        push()
        runners = emptyMap()
        advanceTurn()
        persist()
    }

    /** Hand the board to someone else without banking or busting. */
    fun passTurn() {
        push()
        advanceTurn()
        persist()
    }

    private fun advanceTurn() {
        if (winner == null) currentPlayer = (currentPlayer + 1) % playerCount
    }

    fun undo() {
        val s = history.removeLastOrNull() ?: return
        restore(s)
        historyDepth = history.size
        persist()
    }

    /** The only thing that clears the board. Wired to a confirmation dialog in the UI. */
    fun newGame(players: Int = playerCount) {
        history.clear()
        historyDepth = 0
        restore(GameState.fresh(players.coerceIn(2, MAX_PLAYERS)))
        persist()
    }
}
