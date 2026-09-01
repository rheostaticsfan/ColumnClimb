package com.moira.cantstop

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val Gap = 2.dp
private const val TALLEST_COLUMN = 13

/** Number footer height, as a fraction of a cell — so the fit maths stays self-consistent. */
private const val FOOTER_RATIO = 0.75f

/** Roughly the cell size on a phone, the reference for scaling line weights. */
private val PhoneCell = 32.dp

/**
 * Which quadrant of a cell each player's marker occupies — fixed per player, not per
 * "whoever turned up first", so a colour is always in the same place.
 *
 * The order is clockwise from top-left, which is also the turn order, which is also the order
 * of the score pills in the header. One sequence, no rule to remember.
 *
 * It also decides which colour pairs share an EDGE and which only touch at a corner, and the
 * palette in Theme.kt is spaced to suit: every edge-adjacent pair is at least 1.95:1 apart in
 * greyscale, while the two diagonal pairs (players 0/2 and 1/3) are allowed to run closer,
 * since position and hue already tell those apart.
 */
private val QUADRANT_ORDER = listOf(
    Alignment.TopStart,     // player 0
    Alignment.TopEnd,       // player 1
    Alignment.BottomEnd,    // player 2
    Alignment.BottomStart,  // player 3
)

/**
 * Player markers are rounded squares filling a quadrant of the cell, ~19% of its area each —
 * nearly the quarter that four of them would tile, held back just enough that the cell's own
 * outline still shows through. That's roughly three times the coloured area of round dots,
 * which is what makes them readable on a colour e-ink panel where colour arrives at half
 * resolution. A lone marker is centred instead.
 *
 * Squares also mean the neutral runner, which stays a circle, is told apart by SHAPE rather
 * than only by colour — one more channel the panel can't wash out.
 */
private const val MARKER_SIDE = 0.44f
private const val MARKER_INSET = 0.03f

/**
 * @param leftAction  drawn in the empty space above columns 2-3, two columns wide.
 * @param rightAction drawn in the mirrored space above columns 11-12.
 *
 * Both are centred on the gap between column 7's top two cells. The pyramid leaves those
 * shoulders empty, and nothing there is a tap target — the columns are bottom-aligned, so a
 * short column's tap area doesn't extend up into the blank space above it.
 */
@Composable
fun BoardView(
    vm: GameViewModel,
    modifier: Modifier = Modifier,
    leftAction: @Composable () -> Unit = {},
    rightAction: @Composable () -> Unit = {},
) {
    // clipToBounds: on a viewport under ~136 dp tall (split-screen) the 8 dp cell floor wins
    // and the board would otherwise paint over the header and controls.
    BoxWithConstraints(modifier.clipToBounds()) {
        // Fit the whole pyramid: 11 columns across, 13 spaces plus a number footer down.
        // Vertically that's 13 cells + 13 gaps + a footer of FOOTER_RATIO * cell.
        val byWidth = (maxWidth - Gap * (COLUMN_COUNT - 1)) / COLUMN_COUNT
        val byHeight = (maxHeight - Gap * TALLEST_COLUMN) / (TALLEST_COLUMN + FOOTER_RATIO)
        val cell = minOf(byWidth, byHeight).coerceAtLeast(8.dp)

        // Line weights scale with the cell, not with the screen: a 1 dp outline that reads
        // fine on a 32 dp phone cell is a hairline on an 85 dp e-ink tablet cell.
        val lineScale = (cell / PhoneCell).coerceIn(1f, 2.2f)

        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(Gap),
            verticalAlignment = Alignment.Bottom,
        ) {
            for (col in 0 until COLUMN_COUNT) {
                ColumnView(vm = vm, col = col, cell = cell, lineScale = lineScale)
            }
        }

        // Where the Row above actually ended up, so the buttons can be pinned to columns.
        val step = cell + Gap
        val boardWidth = cell * COLUMN_COUNT + Gap * (COLUMN_COUNT - 1)
        val boardHeight = cell * TALLEST_COLUMN + Gap * TALLEST_COLUMN + cell * FOOTER_RATIO
        val boardLeft = (maxWidth - boardWidth) / 2
        val boardTop = (maxHeight - boardHeight) / 2

        val actionWidth = cell * 2 + Gap
        // Never below 48 dp: Material's minimumInteractiveComponentSize would force the button
        // node to 48 anyway and then centre a smaller surface inside it, throwing the
        // alignment off by the difference.
        val actionHeight = (44.dp * LocalUiScale.current).coerceAtLeast(48.dp)
        // Centre of the gap between column 7's top cell and the one below it. Coerced because
        // on a very short viewport (split-screen) the cell floor makes this go negative, and
        // clipToBounds would shave the top off the buttons.
        val actionTop = (boardTop + cell + Gap / 2 - actionHeight / 2).coerceAtLeast(0.dp)

        Box(
            modifier = Modifier
                .offset(x = boardLeft, y = actionTop)
                .width(actionWidth)
                .height(actionHeight)
        ) { leftAction() }

        Box(
            modifier = Modifier
                .offset(x = boardLeft + step * (COLUMN_COUNT - 2), y = actionTop)
                .width(actionWidth)
                .height(actionHeight)
        ) { rightAction() }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ColumnView(vm: GameViewModel, col: Int, cell: Dp, lineScale: Float) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val top = columnHeight(col)
    val claimedBy = vm.claimedBy[col]
    val runnerAt = vm.runners[col]

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Gap),
        modifier = Modifier
            .width(cell)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                // indication = null: no ripple. Animation on e-ink means ghosting.
                interactionSource = interaction,
                indication = null,
                onClick = { vm.advance(col) },
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.retreat(col)
                },
                onClickLabel = "Advance column ${columnNumber(col)}",
                onLongClickLabel = "Step back column ${columnNumber(col)}",
            ),
    ) {
        for (space in top downTo 1) {
            SpaceView(
                vm = vm,
                col = col,
                space = space,
                cell = cell,
                isTopSpace = space == top,
                hasRunner = runnerAt == space,
                claimedBy = claimedBy,
                lineScale = lineScale,
            )
        }
        ColumnFooter(col = col, cell = cell, claimedBy = claimedBy, lineScale = lineScale)
    }
}

@Composable
private fun SpaceView(
    vm: GameViewModel,
    col: Int,
    space: Int,
    cell: Dp,
    isTopSpace: Boolean,
    hasRunner: Boolean,
    claimedBy: Int,
    lineScale: Float,
) {
    val palette = LocalBoardPalette.current
    val playersHere = (0 until vm.playerCount).filter { vm.progress[it][col] == space }

    // Flat fill plus a crisp outline in every case — no translucent gradients, which e-ink
    // renders as dither noise.
    val fill: Color
    val borderColor: Color
    val borderWidth: Dp
    when {
        claimedBy != UNCLAIMED -> {
            fill = palette.playerColors[claimedBy].copy(alpha = palette.claimedFillAlpha)
            borderColor = palette.playerColors[claimedBy]
            // Coloured lines get an extra bump: on a Kaleido panel colour renders at half the
            // resolution of black, so a coloured edge is softer than an equivalent ink one.
            borderWidth = palette.claimedBorderWidth * lineScale * 1.25f
        }
        isTopSpace -> {
            fill = palette.topSpaceFill
            borderColor = palette.topSpaceBorder
            borderWidth = palette.topSpaceBorderWidth * lineScale
        }
        else -> {
            fill = palette.spaceFill
            borderColor = palette.spaceBorder
            borderWidth = palette.spaceBorderWidth * lineScale
        }
    }
    val shape = RoundedCornerShape(percent = 28)

    Box(
        modifier = Modifier
            .size(cell)
            .clip(shape)
            .background(fill)
            .border(width = borderWidth, color = borderColor, shape = shape),
        contentAlignment = Alignment.Center,
    ) {
        // Markers first, runner painted over the top. A runner shares a space with markers
        // rather than replacing them, and there isn't room for both at full size — so the
        // runner overlaps, which is also what a token physically sitting on the board looks
        // like. Shrinking the markers to clear it would make them smaller than plain dots.
        if (playersHere.size == 1 && !hasRunner) {
            MarkerSquare(
                player = playersHere[0],
                side = cell * MARKER_SIDE,
                lineScale = lineScale,
            )
        } else if (playersHere.isNotEmpty()) {
            MarkerQuadrants(players = playersHere, cell = cell, lineScale = lineScale)
        }
        if (hasRunner) {
            // Neutral runner: the marker that only lives for the length of a turn.
            Box(
                modifier = Modifier
                    .size(cell * 0.74f)
                    .clip(CircleShape)
                    .background(palette.runnerFill)
                    .border(
                        palette.runnerBorderWidth * lineScale,
                        palette.runnerBorder,
                        CircleShape,
                    )
            )
        }
    }
}

@Composable
private fun MarkerSquare(
    player: Int,
    side: Dp,
    lineScale: Float,
    modifier: Modifier = Modifier,
) {
    val palette = LocalBoardPalette.current
    val shape = RoundedCornerShape(percent = 22)
    Box(
        modifier = modifier
            .size(side)
            .clip(shape)
            .background(palette.playerColors[player])
            // Outline scaled to the marker so two touching squares always show a dividing
            // line, without the ring eating the colour on a small cell.
            .border(
                width = (side * 0.06f).coerceIn(0.6.dp * lineScale, 1.6.dp * lineScale),
                color = palette.markerBorder,
                shape = shape,
            )
    )
}

/** Two or more markers on one space, each in its own player's fixed quadrant. */
@Composable
private fun MarkerQuadrants(players: List<Int>, cell: Dp, lineScale: Float) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(cell * MARKER_INSET)
    ) {
        players.forEach { p ->
            MarkerSquare(
                player = p,
                side = cell * MARKER_SIDE,
                lineScale = lineScale,
                modifier = Modifier.align(QUADRANT_ORDER[p % QUADRANT_ORDER.size]),
            )
        }
    }
}

@Composable
private fun ColumnFooter(col: Int, cell: Dp, claimedBy: Int, lineScale: Float) {
    val palette = LocalBoardPalette.current
    val claimed = claimedBy != UNCLAIMED
    val shape = RoundedCornerShape(6.dp * lineScale)
    // The footer grows with the cell, so the column numbers stay legible on a big screen.
    val footerHeight = cell * FOOTER_RATIO
    val numberSize = with(LocalDensity.current) { (cell * 0.42f).toSp() }
    Box(
        modifier = Modifier
            .width(cell)
            .height(footerHeight)
            .clip(shape)
            .background(if (claimed) palette.playerColors[claimedBy] else palette.footerFill)
            .border(
                width = palette.spaceBorderWidth * lineScale,
                color = if (claimed) palette.playerColors[claimedBy] else palette.footerBorder,
                shape = shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = columnNumber(col).toString(),
            color = if (claimed) onPlayerColor(palette.playerColors[claimedBy])
            else MaterialTheme.colorScheme.onSurface,
            fontSize = numberSize,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
