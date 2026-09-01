# Column Climb

A board and markers for press-your-luck dice games, built for **colour e-ink tablets**.

It is not a game. There are no dice, no rules, no AI opponent and nothing to beat. It replaces
the physical board and the wooden markers, and nothing else — you roll real dice on the table,
argue about whether to push your luck, then tap the board to move. The app trusts you
completely, and refuses only what the physical pieces refuse: a fourth runner, moving on a
closed column, and climbing past a column's top space.

The board is the classic 11-column pyramid used by **Can't Stop**, the 1980 dice game by Sid
Sackson — columns 2 to 12, heights 3/5/7/9/11/13/11/9/7/5/3, 83 spaces, three neutral runners,
close three columns to win.

> This project is not affiliated with, endorsed by, or connected to Sid Sackson's estate,
> Ravensburger, Asmodee, or any publisher of Can't Stop. The name is referenced only to say
> which game this accessory is designed for. All artwork here is original.

## Why it exists

Board-game apps assume a backlit phone. This one assumes a 10.3" colour e-ink tablet — a BOOX
Note Air4 C in the author's case — where the screen ghosts, animation is a liability, and
**colour renders at half the resolution of black**. Most of the design decisions below follow
from that, and they're documented because the reasoning is more useful than the result if you
want to adapt it.

It works fine on an ordinary phone too. There's a dark theme for that.

## Install

Grab `app-debug.apk` from the [Releases](../../releases) page, copy it to the device, and tap
it. Android will ask you to allow installs from whatever app you used to transfer it. On a BOOX,
**BooxDrop** over Wi-Fi is easier than a cable.

Requires Android 8.0 (API 26) or later. The app requests no permissions and has no network code.

## Build it yourself

**Android Studio → Open**, pick this folder, let the Gradle sync finish, press Run. Studio
downloads the SDK, Gradle and the dependencies itself.

There's no `gradlew` in the repo — the IDE reads the version from
`gradle/wrapper/gradle-wrapper.properties`. Run `gradle wrapper` once if you want a
command-line build.

The toolchain is pinned deliberately low, for **Android Studio Hedgehog (2023.1.1)**: AGP 8.1.4,
Kotlin 1.9.22, Gradle 8.2, compileSdk 34, Compose BOM 2024.02.00. Nothing in the source depends
on those versions — a newer Studio's AGP Upgrade Assistant will move them forward happily.

## Using it

| Gesture | Effect |
| --- | --- |
| Tap a column | Drop a runner on it, or step the runner there up one space |
| Long-press a column | Step that runner back one space (lifts it off at the start) |
| **Stop** | Bank the runners into the current player's colour, closing any column topped out |
| **Bust** | Runners come off, nothing banked, next player |
| **Undo** | Steps back through every move, including whole turns |
| **New game** | Asks to confirm, then clears the board. The only thing that does |
| Tap the caption under the pills | Name whoever is playing Red |

Turn order is fixed — Red, Green, Blue, Cyan — matching the score pills left to right *and* the
clockwise order of the marker quadrants in a cell. One sequence, so there's no mapping to
learn. Naming the first player is enough to reconstruct who has which colour when someone gets
distracted mid-game; the name is saved and carried into the next game, since it's usually the
same people.

Bust and Stop sit in the pyramid's empty shoulders rather than at the bottom of the screen. The
space above the short columns is otherwise wasted, and putting them there let the board grow.

## Designing for colour e-ink

Kaleido panels put a colour filter over a monochrome screen: black text renders at full
resolution, colour at half, and every hue arrives desaturated and dimmed. Three consequences
drove most of this.

**Colours must differ in lightness, not only in hue.** An early palette had all four players at
relative luminance 0.13–0.16 — in greyscale, the same colour. On the panel the amber
"yellow" was indistinguishable from red, and green from blue. The palette is now a ladder:

| | luminance | |
| --- | --- | --- |
| Blue `#1449A0` | 0.075 | darkest |
| Red `#AE2621` | 0.105 | |
| Green `#2A9E2E` | 0.251 | |
| Cyan `#00B5CD` | 0.375 | lightest |

**The spacing is tuned to the layout, not spread evenly.** Each player owns a fixed quadrant of
a cell, clockwise from top-left, which fixes exactly which colours can share an *edge* and
which only ever touch at a corner. The edge-adjacent pairs get the separation; the diagonal
pairs are allowed to run close, because position and hue already distinguish those.

| edge-adjacent | | diagonal only | |
| --- | --- | --- | --- |
| red / green | 1.95:1 | red / blue | 1.24:1 |
| green / blue | 2.42:1 | green / cyan | 1.41:1 |
| blue / cyan | 3.41:1 | | |
| red / cyan | 2.74:1 | | |

Those are pure-greyscale contrasts, so they hold with the colour stripped out entirely.

**Use shape as a second channel.** Player markers are rounded squares filling a quadrant, ~19%
of the cell's area each — about 2.7× the coloured area of round dots, which matters when the
colour is half-resolution. The neutral runner stays a circle, so it's distinguishable from a
marker without relying on colour at all.

Everything else follows from ghosting and refresh behaviour:

- **No ripples.** Tap animations ghost and provoke full-panel refreshes. Suppressed app-wide via
  `LocalRippleTheme`, and independently on the board columns via `indication = null`.
- **No translucent greys.** Every space is a flat fill plus a crisp outline; e-ink dithers soft
  gradients into noise.
- **The launch window is white.** It's what paints before Compose draws its first frame, so a
  dark one would flash grey on every cold start.
- **Text on a player colour is chosen per colour** by `onPlayerColor()`, since one shared value
  can't serve both the dark hues and the pale cyan.

## Scaling

Two independent scale factors, which is one more than seems necessary until you try it on both
a phone and a 10" tablet.

`LocalUiScale` scales fixed chrome — text, pill dots, button heights — from the screen size,
taking whichever of width or height is *tighter*. Scaling off width alone over-scales a 4:3
tablet: the chrome grows, eats the board's vertical space, and the board ends up **smaller**
than if you hadn't scaled at all.

`lineScale` scales border weights and the column numbers from the *cell* size, because a 1 dp
outline that reads fine on a 32 dp phone cell is a hairline on a 60 dp tablet cell.

Cells land around 32 dp on a phone and ~60 dp on a 10.3" 300 DPI tablet. Portrait-locked: the
board's own aspect ratio is close to 4:3, so portrait suits it on both.

## Persistence

The board is written to `SharedPreferences` after every move as one ~200-byte string — see the
comment at the top of `GameStore.kt` for the format. A game survives backgrounding, rotation,
the app being killed and a reboot. Nothing expires it; only **New game → Clear board** does.

Two deliberate limits: **undo history isn't saved** (it's in memory, so after a kill you see the
board exactly as it was but can't undo into the previous session), and **a malformed or foreign
save is discarded rather than repaired** — you get a fresh board instead of a crash.
`GameStore.decode()` validates every value and the relationships between them.

## Source layout

```
app/src/main/java/com/moira/cantstop/
  GameViewModel.kt   board state, moves, undo, win check, theme preference
  GameStore.kt       GameState value type + save/load to SharedPreferences
  Theme.kt           light (e-ink) and dark palettes, colour schemes, ripple suppression
  Board.kt           the pyramid: columns, spaces, runners, markers, action slots
  MainActivity.kt    header, control bar, dialogs
```

No dependencies beyond Compose. No analytics, no network, no permissions.

## Licence

MIT — see [LICENSE](LICENSE). Do what you like with it.
