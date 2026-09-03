package com.moira.cantstop

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
// Undo is directional, so it lives in the auto-mirrored set and flips in RTL locales.
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Score-pill dot, sized to match the runner token on the board.
 * The runner is cell * 0.74f, and the cell lands around 29-34 dp on a phone.
 * Multiplied by LocalUiScale at the use site, like every other fixed size here.
 */
private val PillDotSize = 24.dp

/** Same rounded square the board uses for player markers, so the pills read as a legend. */
private val MarkerShape = RoundedCornerShape(percent = 22)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Transparent scrims both ends: enableEdgeToEdge otherwise picks bar styles once from
        // the SYSTEM night mode, which then contradicts the in-app theme toggle.
        val clear = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = clear, navigationBarStyle = clear)
        setContent {
            val vm: GameViewModel = viewModel()
            CantStopTheme(darkTheme = vm.darkMode) {
                BoardScreen(vm)
            }
        }
    }
}

@Composable
fun BoardScreen(vm: GameViewModel = viewModel()) {
    var showNewGame by remember { mutableStateOf(false) }
    var showNameEdit by remember { mutableStateOf(false) }
    var pendingPlayers by remember { mutableStateOf(vm.playerCount) }
    var pendingName by remember { mutableStateOf("") }
    val s = LocalUiScale.current

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .padding(horizontal = 10.dp * s, vertical = 6.dp * s)
        ) {
            HeaderBar(
                vm = vm,
                onUndo = { vm.undo() },
                onNewGame = {
                    pendingPlayers = vm.playerCount
                    pendingName = vm.firstPlayerName
                    showNewGame = true
                },
                onEditName = {
                    pendingName = vm.firstPlayerName
                    showNameEdit = true
                },
                onToggleTheme = { vm.toggleTheme() },
            )
            BoardView(
                vm = vm,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 8.dp * s),
                // The pyramid's empty shoulders are the only large free space on screen.
                leftAction = {
                    ActionButton("Bust", false, vm.currentPlayer) { vm.bust() }
                },
                rightAction = {
                    ActionButton("Stop", true, vm.currentPlayer) { vm.stopAndBank() }
                },
            )
            ControlBar(vm)
        }
    }

    val winner = vm.winner
    if (winner != null) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("${PLAYER_NAMES[winner]} wins") },
            text = { Text("Three columns closed. Set the board up again?") },
            confirmButton = {
                Button(onClick = { vm.newGame() }) { Text("New game") }
            },
            dismissButton = {
                TextButton(onClick = { vm.undo() }, enabled = vm.canUndo) { Text("Undo last move") }
            },
        )
    }

    if (showNewGame) {
        AlertDialog(
            onDismissRequest = { showNewGame = false },
            title = { Text("Clear the board?") },
            text = {
                Column {
                    Text("The game in progress will be discarded. This can't be undone.")
                    Text(
                        text = "Players",
                        fontSize = 13.sp * s,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 18.dp * s),
                    )
                    Row(
                        modifier = Modifier.padding(top = 6.dp * s),
                        horizontalArrangement = Arrangement.spacedBy(8.dp * s),
                    ) {
                        for (n in 2..MAX_PLAYERS) {
                            if (pendingPlayers == n) {
                                Button(onClick = { pendingPlayers = n }) { Text("$n") }
                            } else {
                                OutlinedButton(onClick = { pendingPlayers = n }) { Text("$n") }
                            }
                        }
                    }
                    FirstPlayerField(
                        value = pendingName,
                        onValueChange = { pendingName = filterNameInput(it) },
                        modifier = Modifier.padding(top = 18.dp * s),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.newGame(pendingPlayers, pendingName)
                        showNewGame = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text("Clear board") }
            },
            dismissButton = {
                TextButton(onClick = { showNewGame = false }) { Text("Cancel") }
            },
        )
    }

    if (showNameEdit) {
        AlertDialog(
            onDismissRequest = { showNameEdit = false },
            title = { Text("Who's playing Red?") },
            text = {
                Column {
                    Text(
                        text = "Turn order is $TURN_ORDER_TEXT — so naming Red is " +
                            "enough to work out who has which colour.",
                        fontSize = 13.sp * s,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FirstPlayerField(
                        value = pendingName,
                        onValueChange = { pendingName = filterNameInput(it) },
                        modifier = Modifier.padding(top = 16.dp * s),
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    vm.nameFirstPlayer(pendingName)
                    showNameEdit = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showNameEdit = false }) { Text("Cancel") }
            },
        )
    }
}

/** Shared by the new-game dialog and the standalone rename dialog. */
@Composable
private fun FirstPlayerField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("First player (Red)") },
        placeholder = { Text("Name") },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeaderBar(
    vm: GameViewModel,
    onUndo: () -> Unit,
    onNewGame: () -> Unit,
    onEditName: () -> Unit,
    onToggleTheme: () -> Unit,
) {
    val s = LocalUiScale.current
    Column(modifier = Modifier.fillMaxWidth()) {
        // FlowRow, not Row: sp sizes still grow with the system font scale even when s is
        // clamped to 1, and a plain Row would squeeze or clip the last item rather than wrap.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp * s, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(6.dp * s),
        ) {
            HeaderButton(
                label = "Undo",
                icon = Icons.AutoMirrored.Filled.Undo,
                onClick = onUndo,
                enabled = vm.canUndo,
            )
            HeaderButton(
                label = "New game",
                icon = Icons.Filled.Refresh,
                onClick = onNewGame,
            )
            // Labelled with the mode it switches TO, so it reads as an action.
            HeaderButton(
                label = if (vm.darkMode) "Light" else "Dark",
                icon = if (vm.darkMode) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                onClick = onToggleTheme,
            )
        }
        // One pill per player, closed columns out of three, centred.
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp * s),
            horizontalArrangement = Arrangement.spacedBy(8.dp * s, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(6.dp * s),
        ) {
            for (p in 0 until vm.playerCount) {
                ScorePill(
                    player = p,
                    claimed = vm.claimedCount(p),
                    isTurn = p == vm.currentPlayer,
                )
            }
        }
        // Who Red is. Pills read left to right in turn order, so this fixes everyone else too.
        val caption = if (vm.firstPlayerName.isBlank()) {
            "Tap to name the first player"
        } else {
            val rest = (1 until vm.playerCount).joinToString(", ") { PLAYER_NAMES[it] }
            "Red is ${vm.firstPlayerName} · then $rest"
        }
        Text(
            text = caption,
            fontSize = 11.sp * s,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onEditName)
                .padding(top = 12.dp * s, bottom = 10.dp * s),
        )
    }
}

/**
 * Bust and Stop, sized by the slot BoardView gives them in the pyramid's shoulders.
 * Opaque, because they sit over the board rather than below it.
 */
@Composable
private fun ActionButton(label: String, filled: Boolean, player: Int, onClick: () -> Unit) {
    val palette = LocalBoardPalette.current
    val s = LocalUiScale.current
    val fill = palette.playerColors[player]
    // Pinned to a physical size rather than sp: the slot is a fixed fraction of the board, so
    // a large accessibility font scale would otherwise clip "Bust" to "Bus".
    val labelSize = with(LocalDensity.current) { (14.dp * s).toSp() }
    val text = @Composable {
        Text(
            text = label,
            fontWeight = FontWeight.Bold,
            fontSize = labelSize,
            maxLines = 1,
            softWrap = false,
        )
    }
    if (filled) {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 2.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = fill,
                contentColor = onPlayerColor(fill),
            ),
        ) { text() }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 2.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
        ) { text() }
    }
}

/** The three header controls, so they stay identical in size and shape. */
@Composable
private fun HeaderButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val s = LocalUiScale.current
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        // 40 dp, not 34: below that the touch target gets uncomfortably small on a phone.
        modifier = Modifier.height(40.dp * s),
        contentPadding = PaddingValues(horizontal = 12.dp * s, vertical = 0.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(15.dp * s))
        Text(label, fontSize = 12.sp * s, modifier = Modifier.padding(start = 5.dp * s))
    }
}

@Composable
private fun ScorePill(player: Int, claimed: Int, isTurn: Boolean) {
    val palette = LocalBoardPalette.current
    val s = LocalUiScale.current
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(
                if (isTurn) palette.playerColors[player].copy(alpha = palette.claimedFillAlpha)
                else MaterialTheme.colorScheme.surface
            )
            .border(
                width = (if (isTurn) 1.5.dp else 1.dp) * s,
                color = if (isTurn) palette.playerColors[player] else palette.footerBorder,
                shape = shape,
            )
            .padding(horizontal = 8.dp * s, vertical = 5.dp * s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp * s),
    ) {
        // Rounded square, matching the board markers — the runner is the only circle now.
        Box(
            modifier = Modifier
                .size(PillDotSize * s)
                .clip(MarkerShape)
                .background(palette.playerColors[player])
                .border(1.dp * s, palette.markerBorder, MarkerShape)
        )
        Text(
            text = "$claimed/$COLUMNS_TO_WIN",
            fontSize = 13.sp * s,
            fontWeight = if (isTurn) FontWeight.Bold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun ControlBar(vm: GameViewModel) {
    val player = vm.currentPlayer
    val palette = LocalBoardPalette.current
    val s = LocalUiScale.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp * s),
            horizontalArrangement = Arrangement.spacedBy(12.dp * s, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (player == 0 && vm.firstPlayerName.isNotBlank()) {
                    "${vm.firstPlayerName} (Red) to move"
                } else {
                    "${PLAYER_NAMES[player]} to move"
                },
                color = palette.playerColors[player],
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp * s,
            )
            Text(
                text = "Runners ${vm.runners.size}/$RUNNER_COUNT",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp * s,
            )
        }
        Text(
            text = "Tap a column to move a runner up · long-press to step back",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp * s,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp * s),
        )
    }
}
