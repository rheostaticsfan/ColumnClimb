package com.moira.cantstop

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

private val Ink = Color(0xFF121820)

private val BoardScheme = darkColorScheme(
    primary = Color(0xFFF2C230),
    onPrimary = Ink,
    secondary = Color(0xFF7E8CA0),
    background = Ink,
    onBackground = Color(0xFFE8ECF1),
    surface = Color(0xFF1B2430),
    onSurface = Color(0xFFE8ECF1),
    surfaceVariant = Color(0xFF243040),
    onSurfaceVariant = Color(0xFFB9C3D0),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = BoardScheme) {
                BoardScreen()
            }
        }
    }
}

@Composable
fun BoardScreen(vm: GameViewModel = viewModel()) {
    var showNewGame by remember { mutableStateOf(false) }
    var pendingPlayers by remember { mutableStateOf(vm.playerCount) }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            HeaderBar(
                vm = vm,
                onUndo = { vm.undo() },
                onNewGame = {
                    pendingPlayers = vm.playerCount
                    showNewGame = true
                },
            )
            BoardView(
                vm = vm,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 8.dp),
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
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 18.dp),
                    )
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for (n in 2..MAX_PLAYERS) {
                            if (pendingPlayers == n) {
                                Button(onClick = { pendingPlayers = n }) { Text("$n") }
                            } else {
                                OutlinedButton(onClick = { pendingPlayers = n }) { Text("$n") }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.newGame(pendingPlayers)
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
}

@Composable
private fun HeaderBar(vm: GameViewModel, onUndo: () -> Unit, onNewGame: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (p in 0 until vm.playerCount) {
                ScorePill(
                    player = p,
                    claimed = vm.claimedCount(p),
                    isTurn = p == vm.currentPlayer,
                )
            }
        }
        IconButton(onClick = onUndo, enabled = vm.canUndo) {
            Icon(Icons.Filled.Undo, contentDescription = "Undo")
        }
        OutlinedButton(
            onClick = onNewGame,
            modifier = Modifier.height(34.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
            )
            Text("New game", fontSize = 12.sp, modifier = Modifier.padding(start = 5.dp))
        }
    }
}

@Composable
private fun ScorePill(player: Int, claimed: Int, isTurn: Boolean) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (isTurn) PLAYER_COLORS[player].copy(alpha = 0.22f)
                else MaterialTheme.colorScheme.surface
            )
            .then(
                if (isTurn) Modifier.border(1.dp, PLAYER_COLORS[player], RoundedCornerShape(50))
                else Modifier
            )
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(11.dp)
                .clip(CircleShape)
                .background(PLAYER_COLORS[player])
        )
        Text(
            text = "$claimed/$COLUMNS_TO_WIN",
            fontSize = 12.sp,
            fontWeight = if (isTurn) FontWeight.Bold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun ControlBar(vm: GameViewModel) {
    val player = vm.currentPlayer
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${PLAYER_NAMES[player]} to move",
                color = PLAYER_COLORS[player],
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Runners ${vm.runners.size}/$RUNNER_COUNT",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { vm.bust() },
                modifier = Modifier.weight(1f),
            ) { Text("Bust") }
            Button(
                onClick = { vm.stopAndBank() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PLAYER_COLORS[player],
                    contentColor = Ink,
                ),
            ) { Text("Stop", fontWeight = FontWeight.Bold) }
        }
        Text(
            text = "Tap a column to move a runner up · long-press to step back",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
        )
    }
}
