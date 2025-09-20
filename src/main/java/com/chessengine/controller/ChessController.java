package com.chessengine.controller;

import com.chessengine.service.GameService;
import com.chessengine.service.GameService.GameState;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/game")
public class ChessController {

    private final GameService gameService;

    public ChessController(GameService gameService) {
        this.gameService = gameService;
    }

    @PostMapping("/new")
    public ResponseEntity<Map<String, String>> newGame(@RequestBody(required = false) NewGameRequest req) {
        String fen = req != null ? req.fen : null;
        String id = gameService.newGame(fen);
        return ResponseEntity.ok(Map.of("gameId", id));
    }

    @GetMapping("/{id}")
    public ResponseEntity<GameState> getState(@PathVariable("id") String id) {
        return ResponseEntity.ok(gameService.getState(id));
    }

    @PostMapping("/{id}/move")
    public ResponseEntity<ActionResult> playerMove(@PathVariable("id") String id, @RequestBody MoveRequest req) {
        boolean ok = gameService.makePlayerMove(id, req.uci);
        return ResponseEntity.ok(new ActionResult(ok));
    }

    @PostMapping("/{id}/engine-move")
    public ResponseEntity<BestMoveResponse> engineMove(@PathVariable("id") String id,
                                                       @RequestBody(required = false) EngineMoveRequest req) {
        Integer depth = req != null ? req.depth : null;
        Long timeMs = req != null ? req.timeMs : null;
        String uci = gameService.findBestMove(id, depth, timeMs);
        boolean ok = gameService.makePlayerMove(id, uci);
        return ResponseEntity.ok(new BestMoveResponse(ok, uci));
    }

    @PostMapping("/{id}/undo")
    public ResponseEntity<ActionResult> undo(@PathVariable("id") String id) {
        boolean ok = gameService.undo(id);
        return ResponseEntity.ok(new ActionResult(ok));
    }

    @PostMapping("/{id}/reset")
    public ResponseEntity<ActionResult> reset(@PathVariable("id") String id, @RequestBody(required = false) NewGameRequest req) {
        boolean ok = gameService.reset(id, req != null ? req.fen : null);
        return ResponseEntity.ok(new ActionResult(ok));
    }

    // DTOs
    public static class NewGameRequest {
        public String fen;
    }

    public static class MoveRequest {
        public String uci;
    }

    public static class EngineMoveRequest {
        @Min(1)
        public Integer depth;
        @Min(100)
        public Long timeMs;
    }

    public static class ActionResult {
        public boolean ok;
        public ActionResult(boolean ok) { this.ok = ok; }
    }

    public static class BestMoveResponse extends ActionResult {
        public String uci;
        public BestMoveResponse(boolean ok, String uci) {
            super(ok);
            this.uci = uci;
        }
    }
}
