package com.moira.cantstop

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Gap = 2.dp
private val FooterHeight = 26.dp
private const val TALLEST_COLUMN = 13

/** Top space of each column, the one that closes it. */
private val TopSpaceGold = Color(0xFFF2C230)

@Composable
fun BoardView(vm: GameViewModel, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        // Fit the whole pyramid: 11 columns across, 13 spaces plus a number footer down.
        val byWidth = (maxWidth - Gap * (COLUMN_COUNT - 1)) / COLUMN_COUNT
        val byHeight = (maxHeight - FooterHeight - Gap * TALLEST_COLUMN) / TALLEST_COLUMN
        val cell = minOf(byWidth, byHeight).coerceAtLeast(8.dp)

        Row(
            modifier = Modifier.align(Alignment.BottomCenter),
            horizontalArrangement = Arrangement.spacedBy(Gap),
            verticalAlignment = Alignment.Bottom,
        ) {
            for (col in 0 until COLUMN_COUNT) {
                ColumnView(vm = vm, col = col, cell = cell)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ColumnView(vm: GameViewModel, col: Int, cell: Dp) {
    val haptics = LocalHapticFeedback.current
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
            )
        }
        ColumnFooter(col = col, cell = cell, claimedBy = claimedBy)
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
) {
    val height = columnHeight(col)
    val playersHere = (0 until vm.playerCount).filter { vm.progress[it][col] == space }

    val fill = when {
        claimedBy != UNCLAIMED -> PLAYER_COLORS[claimedBy].copy(alpha = 0.22f)
        isTopSpace -> TopSpaceGold.copy(alpha = 0.16f)
        else -> Color.White.copy(alpha = 0.05f + 0.05f * (space.toFloat() / height))
    }

    Box(
        modifier = Modifier
            .size(cell)
            .clip(RoundedCornerShape(percent = 28))
            .background(fill)
            .then(
                if (isTopSpace) Modifier.border(
                    width = 1.dp,
                    color = TopSpaceGold.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(percent = 28),
                ) else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (hasRunner) {
            // Neutral runner: the white marker that only lives for the length of a turn.
            Box(
                modifier = Modifier
                    .size(cell * 0.74f)
                    .clip(CircleShape)
                    .background(Color(0xFFF7F9FC))
                    .border(1.5.dp, Color(0xFF121820), CircleShape)
            )
            if (playersHere.isNotEmpty()) {
                MarkerDots(
                    players = playersHere,
                    dot = cell * 0.20f,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        } else if (playersHere.isNotEmpty()) {
            MarkerDots(players = playersHere, dot = cell * 0.34f)
        }
    }
}

@Composable
private fun MarkerDots(players: List<Int>, dot: Dp, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        players.forEach { p ->
            Box(
                modifier = Modifier
                    .size(dot)
                    .clip(CircleShape)
                    .background(PLAYER_COLORS[p])
                    .border(0.5.dp, Color(0x66000000), CircleShape)
            )
        }
    }
}

@Composable
private fun ColumnFooter(col: Int, cell: Dp, claimedBy: Int) {
    val claimed = claimedBy != UNCLAIMED
    Box(
        modifier = Modifier
            .width(cell)
            .height(FooterHeight - Gap)
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (claimed) PLAYER_COLORS[claimedBy] else Color.White.copy(alpha = 0.10f)
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = columnNumber(col).toString(),
            color = if (claimed) Color(0xFF121820) else MaterialTheme.colorScheme.onSurface,
            fontSize = if (cell > 26.dp) 13.sp else 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
