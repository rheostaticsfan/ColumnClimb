# Can't Stop — board & markers

A digital stand-in for the physical board. It does **no** dice logic and enforces no rules
beyond what the wooden pieces themselves enforce: you roll and decide at the table, then tap
the board to move.

## Build and install

1. **Android Studio → Open** and pick this folder (`CantStopBoard`).
2. Let the Gradle sync finish. Studio downloads the Android SDK, Gradle 8.2 and the
   dependencies itself. There is no `gradlew` script in the repo — the IDE reads the version
   from `gradle/wrapper/gradle-wrapper.properties` and handles it. If you want a command-line
   wrapper, run `gradle wrapper` once from Studio's terminal.
   - Toolchain is pinned for **Android Studio Hedgehog (2023.1.1)**: AGP 8.1.4, Kotlin 1.9.22,
     Gradle 8.2, compileSdk 34, Compose BOM 2024.02.00. If you later update Android Studio,
     its **AGP Upgrade Assistant** can move these forward; nothing in the source cares.
3. On the phone: **Settings → About phone → tap "Build number" seven times** to unlock
   Developer options, then **Developer options → USB debugging → on**.
4. Plug the phone in, accept the "Allow USB debugging" prompt, pick the device in Studio's
   toolbar, press **Run** (▶). No root needed — this is a normal debug install.

To install on a second device without a cable: **Build → Build Bundle(s)/APK(s) → Build APK(s)**,
then copy `app/build/outputs/apk/debug/app-debug.apk` to the phone and open it. Android will ask
you to allow installs from whichever app you used to transfer it.

A debug-signed APK installs and runs indefinitely; the debug certificate expires after
30 years, and only matters if you later want to *update* the app rather than reinstall it.

## How to use it

| Gesture | Effect |
| --- | --- |
| Tap a column | Drop a white runner on it, or step the runner there up one space |
| Long-press a column | Step that runner back one space (lifts it off at the start) |
| **Stop** | Bank the runners into the current player's colour, closing any column topped out |
| **Bust** | Runners come off, nothing banked, next player |
| ↺ (top right) | Undo — steps back through every move, including whole turns |
| **New game** (top right) | Asks to confirm, then clears the board. The only thing that does |

The pills across the top show each player's closed columns out of three; the outlined one is
whose turn it is.

## What it refuses to do

Only the three things the physical board also refuses: a fourth runner, moving on a closed
column, and climbing past a column's top space. Everything else is allowed, including moves
your dice didn't justify — the app trusts you.

## Layout notes

- Columns are 2–12, heights 3/5/7/9/11/13/11/9/7/5/3 (83 spaces).
- Portrait-locked. The board auto-sizes; cells land around 29–34 dp on a typical phone.

## Persistence

The board is written to `SharedPreferences` after every move, as one short string
(~200 bytes) — see the comment at the top of `GameStore.kt` for the format. So a game survives
backgrounding, rotation, the app being killed, and a reboot. Nothing expires it; only
**New game → Clear board** does.

Two deliberate limits:

- **Undo history is not saved.** It lives in memory, so after the app is killed you can still
  see the board exactly as it was but can't undo back into the previous session.
- **A malformed or foreign save is discarded**, not repaired — you get a fresh board rather
  than a crash. `GameStore.decode()` checks every value and the relationships between them.

## Files

```
app/src/main/java/com/moira/cantstop/
  GameViewModel.kt   board state, moves, undo, win check
  GameStore.kt       GameState value type + save/load to SharedPreferences
  Board.kt           the pyramid: columns, spaces, runners, markers
  MainActivity.kt    theme, header, control bar, dialogs
```
