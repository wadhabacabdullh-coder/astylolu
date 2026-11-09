# Grandmaster AI — Android chess analysis app

A native Android (Kotlin + Jetpack Compose) chess coach powered by the real
**Stockfish 19** engine with **NNUE** evaluation, running fully on-device.

| | |
|---|---|
| Package | `com.grandmaster.ai` |
| Min / target SDK | 26 (Android 8.0) / 36 |
| ABIs | arm64-v8a (+ ARMv8.2 dot-product build), armeabi-v7a, x86_64 |
| Engine | Stockfish 19 (`third_party/stockfish`, upstream tag `sf_19`) |
| Network | `nn-1a298aa575a0.nnue` (SHA-256 verified at build time and on device) |

## Features

* **Analysis Board**: drag-and-drop and tap-to-move, legal-move hints,
  last-move and check highlights, engine arrows, an animated evaluation bar,
  undo/redo/first/last, flip board, promotion picker.
* **Best move system**: best move with annotation, evaluation, depth,
  principal variation and a "Why?" explanation, plus up to 5 candidate moves
  (MultiPV). The explanations are built from the Stockfish line plus exact board
  calculations (static exchange, forks, threats, checks, mates, passed pawns,
  open files, development and so on). They are never invented.
* **Live analysis toggle**: re-analyses on every position change in the
  background, stopping the previous search. When live analysis is off, an
  "Analyze" button runs a one-off analysis.
* **Hikaru Mode**: re-reads 5 Stockfish lines to surface forced mates,
  sacrifices, forcing sequences, defensive "only moves", unexpected quiet moves
  and king attacks. It also shows static threats and every engine line. It is
  not affiliated with or a simulation of Hikaru Nakamura.
* **Game Review**: evaluates every position. It classifies each move as
  Brilliant, Great, Best, Excellent, Good, Book, Inaccuracy, Mistake, Missed
  win or Blunder using the win% model, and shows per-side accuracy, an eval
  graph you can tap, the better move as an arrow, and a comment for each move.
* **Opening Explorer**: an ECO-named opening book (165 named lines, all
  unit-tested for legality). Every continuation is evaluated live by Stockfish
  using `searchmoves`.
* **Play vs Stockfish**: choose your colour. Strength is set by Skill Level
  (0–20) or Elo (1320–3190), with a time per move and take-back.
* **FEN/PGN**: import FEN, import PGN (paste or open a file), export PGN
  (share sheet or clipboard), copy FEN.
* **Engine Settings**: depth, time limit, MultiPV, review depth, threads,
  hash (capped by device RAM), play strength, arrows and coordinates, the live
  default, and engine restart.
* **About**: Stockfish credits, the full GPLv3 text shipped in the app, the
  network hash, and links to the source.

## Architecture

```
app/src/main/java/com/grandmaster/ai/
  chess/      Pure-Kotlin rules: Position (move gen, SAN, FEN), Game, Pgn
  engine/     UciEngine (process + UCI protocol, coroutines), EngineRepository
  analysis/   Evaluation, Tactics (SEE), MoveExplainer, MoveClassifier, HikaruInsights
  data/       SettingsRepository (DataStore), OpeningBook
  ui/         ViewModels + Compose screens/components
third_party/stockfish/   Stockfish 19 source (GPLv3) + build-android.sh
```

* Stockfish runs as a child process. The NDK-built executables are packaged as
  `lib*.so` so Android installs them into `nativeLibraryDir`, which is the only
  executable location on Android 10+. The dot-product build is chosen at
  runtime when `/proc/cpuinfo` reports `asimddp`.
* A single engine process is shared app-wide. A mutex serialises searches, and
  cancelling a collector sends `stop` and waits for `bestmove`, so the protocol
  never desynchronises. All engine I/O and explanation work runs off the main
  thread, and UI updates are throttled.
* The 98.5 MB network is an APK asset. On first launch it is copied to internal
  storage and its SHA-256 is verified.

## Building

Requirements: JDK 17+, Android SDK (platform 36, build-tools), NDK
`28.2.13676358`. The Gradle build compiles Stockfish with the NDK and fetches
plus verifies the NNUE network automatically (from tests.stockfishchess.org or
GitHub). You can also put `nn-1a298aa575a0.nnue` into
`third_party/stockfish/net/` yourself.

```bash
echo "sdk.dir=/path/to/Android/sdk" > local.properties
./gradlew assembleRelease      # app/build/outputs/apk/release/app-release.apk
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
```

**Release signing**: create `keystore.properties` in the project root (it is
git-ignored):

```properties
storeFile=release.jks
storePassword=...
keyAlias=grandmaster
keyPassword=...
```

Without it, the release APK is signed with the debug key so that it is still
installable.

## Tests

```bash
./gradlew testDebugUnitTest
```

* `ChessRulesTest`: perft on 5 reference positions (start, Kiwipete, positions
  3–5), castling rules, en passant (including the pinned case), promotion and
  under-promotion, checkmate, stalemate, insufficient material, threefold
  repetition, SAN disambiguation, FEN validation, undo/redo.
* `PgnTest`: comments, NAGs, variations, FEN tag, round-trip export/import,
  error reporting.
* `AnalysisLogicTest`: book legality, UCI parsing, static exchange,
  explanations, move classification.
* `StockfishIntegrationTest` and `AppUiTest` need a host build of the vendored
  engine:

  ```bash
  (cd third_party/stockfish/src && make -j4 all ARCH=x86-64-sse41-popcnt EXTRACXXFLAGS=-DNNUE_EMBEDDING_OFF)
  STOCKFISH_HOST_BIN=$PWD/third_party/stockfish/src/stockfish \
  STOCKFISH_NET=$PWD/third_party/stockfish/net/nn-1a298aa575a0.nnue \
  ./gradlew testDebugUnitTest
  ```

  `AppUiTest` drives the real `MainActivity` under Robolectric (tap and drag
  moves, promotion, analysis, Hikaru Mode, review, explorer, play mode,
  settings, about). It runs at phone, small-phone, phone-landscape and
  tablet-landscape sizes and writes screenshots to `app/build/screenshots/`.

## Licence

Stockfish is free software licensed under the GNU GPL v3; see
`third_party/stockfish/Copying.txt`. Its complete corresponding source and the
build script are in `third_party/stockfish`. This repository is also licensed
under the GPL v3 (`LICENSE`).
