# Rookloom (Java Chess Engine)

Rookloom is a high-performance chess engine written in Java 17 with a Spring Boot backend and a lightweight HTML/CSS/JavaScript frontend. You can play in your browser, let the engine think by depth or time, or enable automatic replies so the engine moves as soon as it’s its turn.

## Highlights
- Strong baseline engine with minimax + alpha-beta, quiescence, iterative deepening, and basic transposition table.
- Fast move generator and complete game rules (castling, en passant, promotions, checks, checkmate, stalemate).
- Simple, modern web UI served by Spring Boot.
- API-first design so you can integrate it with other UIs or services.

---

## Quick Start (for anyone)

1) Requirements
- Java 17+ (already installed on this machine)

2) Start the server
- If Maven is installed globally:
  ```bash
  mvn spring-boot:run
  ```
- If Maven isn’t installed, use the packaged jar (after a build) or the locally downloaded Maven we used for development:
  ```powershell
  # Build the project
  E:\tools\apache-maven-3.9.9\bin\mvn.cmd -q -DskipTests package

  # Run the app
  java -jar target\rookloom-1.0.0.jar
  ```

3) Open the app
- Visit: http://localhost:8080/api/

4) Basic usage
- Click "New Game".
- Click a piece, then a destination square to move (the UI shows legal UCI moves on the right panel for reference).
- Click "Engine Move" to let the engine respond.
- Toggle "Auto engine move" so the engine replies automatically when it’s its turn.
- Choose who the human plays as (White/Black). If you choose Black and auto is on, the engine will open for White.
- Use "Undo" to revert last move.

---

## For Developers

### Tech Stack
- Backend: Java 17, Spring Boot 3.1.x
- Build: Maven
- Frontend: Static HTML/CSS/JS (served from `src/main/resources/static/`)

### Project Structure
```
E:\code\chess-engine
├─ pom.xml
├─ src
│  ├─ main
│  │  ├─ java
│  │  │  └─ com\chessengine
│  │  │     ├─ ChessEngineApplication.java
│  │  │     ├─ controller\ChessController.java
│  │  │     ├─ service\GameService.java
│  │  │     ├─ engine
│  │  │     │  ├─ ChessEngine.java
│  │  │     │  ├─ Evaluator.java
│  │  │     │  ├─ MoveGenerator.java
│  │  │     │  └─ MoveOrderer.java
│  │  │     └─ model
│  │  │        ├─ Board.java
│  │  │        ├─ Move.java
│  │  │        ├─ Piece.java
│  │  │        ├─ PieceColor.java
│  │  │        ├─ PieceType.java
│  │  │        └─ Square.java
│  │  └─ resources
│  │     ├─ application.yml
│  │     └─ static
│  │        ├─ index.html
│  │        ├─ main.js
│  │        └─ styles.css
└─ README.md
```

### Key Concepts
- `model/` contains core chess types:
  - `Board` stores piece placement and game state, supports FEN import/export, legality checks, and move execution/undo.
  - `Move`, `Square`, `Piece`, `PieceType`, `PieceColor` are lightweight, performance-oriented representations.
- `engine/` contains the engine logic:
  - `MoveGenerator` creates pseudo-legal moves; legality checked against king safety.
  - `Evaluator` combines material, piece-square tables, mobility, and king safety for scoring.
  - `ChessEngine` implements iterative deepening + alpha-beta, quiescence search, and a basic transposition table.
  - `MoveOrderer` uses MVV-LVA, promotions, killers, and history heuristic to order moves.
- `service/` + `controller/` wrap the engine into a REST API and keep per-game state.

### Running and Building
- Build:
  ```bash
  mvn -q -DskipTests package
  ```
- Run (dev):
  ```bash
  mvn spring-boot:run
  ```
- Run (jar):
  ```bash
  java -jar target/rookloom-1.0.0.jar
  ```

### API Endpoints (base path `/api/game`)
- `POST /new` → `{ gameId }` — create a new game (optional body `{ "fen": "<FEN>" }`).
- `GET /{id}` → game state including `fen`, `sideToMove`, `inCheck`, `checkmate`, `stalemate`, `legalMoves` (UCI).
- `POST /{id}/move` — body `{ "uci": "e2e4" }` to make a player move.
- `POST /{id}/engine-move` — body `{ "depth": 6, "timeMs": 3000 }` to let engine move.
- `POST /{id}/undo` — undo last move.
- `POST /{id}/reset` — reset game to start (or to provided FEN).

### Development Tips
- Static resources live in `src/main/resources/static/`. Spring Boot will serve `index.html` at `/api/` (due to `server.servlet.context-path: /api`).
- For frontend changes, rebuild and restart the app or run from your IDE.
- For engine debugging:
  - Log statements in `ChessEngine` show depth, scores, nodes, and NPS.
  - You can add perft tests to validate `MoveGenerator` before changing search logic.

### Roadmap (to approach top-tier strength)
- Zobrist hashing and a proper fixed-size transposition table with replacement policies.
- Enhanced search: PVS, null-move pruning, LMR, aspiration windows, improved quiescence (checks, recaptures).
- Tapered evaluation and richer features (pawn structure hashing, passed pawns, king safety, mobility by piece type).
- Opening book (Polyglot) and Syzygy endgame tablebases.
- Parallel search (Lazy SMP) and stronger move ordering integrations (TT move, PV move, killer/history by depth).

---

## For Non-Developers (Layman’s Guide)

This app lets you play chess against a computer engine in your web browser.

- Open the app at: http://localhost:8080/api/
- Click "New Game" to start.
- Click a piece, then click the square you want to move to.
- Click "Engine Move" to let the computer move, or turn on "Auto engine move" so it moves automatically.
- You can choose to play as White or Black.
- "Undo" takes back the last move.

What makes it smart?
- The computer looks ahead several moves (search depth) and evaluates positions.
- It prunes bad branches using alpha-beta pruning, saving time.
- It evaluates piece values, activity, king safety, and more.

How to make it stronger (simple knobs)
- Increase the depth or the time (ms) before clicking "Engine Move" or enable auto.
- Higher values make it think longer and usually play stronger.

---

## License
This project is provided as-is for educational and development purposes.
