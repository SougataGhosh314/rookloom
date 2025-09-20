package com.chessengine.service;

import com.chessengine.engine.ChessEngine;
import com.chessengine.engine.MoveGenerator;
import com.chessengine.model.*;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class GameService {
    private final Map<String, Board> games = new HashMap<>();
    private final Map<String, ChessEngine> engines = new HashMap<>();

    public static class GameState {
        public String gameId;
        public String fen;
        public String sideToMove;
        public boolean inCheck;
        public boolean checkmate;
        public boolean stalemate;
        public List<String> legalMoves;
    }

    public String newGame(String fen) {
        String gameId = UUID.randomUUID().toString();
        Board board = (fen == null || fen.isBlank()) ? new Board() : new Board(fen);
        games.put(gameId, board);
        engines.put(gameId, new ChessEngine(6, 5000));
        return gameId;
    }

    public GameState getState(String gameId) {
        Board board = requireBoard(gameId);
        GameState state = new GameState();
        state.gameId = gameId;
        state.fen = board.toFEN();
        state.sideToMove = board.getSideToMove().name();
        state.inCheck = board.isInCheck(board.getSideToMove());
        state.checkmate = MoveGenerator.isCheckmate(board);
        state.stalemate = MoveGenerator.isStalemate(board);
        state.legalMoves = toUciMoves(MoveGenerator.generateLegalMoves(board));
        return state;
    }

    public boolean makePlayerMove(String gameId, String uci) {
        Board board = requireBoard(gameId);
        Move move = findMoveByUci(board, uci);
        if (move.isNull()) return false;
        return board.makeMove(move);
    }

    public String findBestMove(String gameId, Integer depth, Long timeMs) {
        Board board = requireBoard(gameId);
        ChessEngine engine = engines.get(gameId);
        if (depth != null) engine.setMaxDepth(depth);
        if (timeMs != null) engine.setMaxTimeMs(timeMs);
        Move best = engine.findBestMove(board);
        return best.toAlgebraic();
    }

    public boolean makeEngineMove(String gameId, Integer depth, Long timeMs) {
        String uci = findBestMove(gameId, depth, timeMs);
        return makePlayerMove(gameId, uci);
    }

    public boolean undo(String gameId) {
        Board board = requireBoard(gameId);
        board.undoMove();
        return true;
    }

    public boolean reset(String gameId, String fen) {
        Board board = requireBoard(gameId);
        board.initializeFromFEN((fen == null || fen.isBlank()) ?
                "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1" : fen);
        return true;
    }

    private Board requireBoard(String gameId) {
        Board board = games.get(gameId);
        if (board == null) {
            throw new NoSuchElementException("Game not found: " + gameId);
        }
        return board;
    }

    private List<String> toUciMoves(List<Move> moves) {
        List<String> list = new ArrayList<>(moves.size());
        for (Move m : moves) list.add(m.toAlgebraic());
        return list;
    }

    private Move findMoveByUci(Board board, String uci) {
        if (uci == null || uci.length() < 4) return Move.NULL_MOVE;
        Square from = Square.fromAlgebraic(uci.substring(0, 2));
        Square to = Square.fromAlgebraic(uci.substring(2, 4));
        Character promo = uci.length() == 5 ? uci.charAt(4) : null;
        
        List<Move> legal = MoveGenerator.generateLegalMoves(board);
        for (Move m : legal) {
            if (m.getFrom().equals(from) && m.getTo().equals(to)) {
                if (promo == null && !m.isPromotion()) return m;
                if (promo != null && m.isPromotion()) {
                    PieceType pt = switch (Character.toLowerCase(promo)) {
                        case 'q' -> PieceType.QUEEN;
                        case 'r' -> PieceType.ROOK;
                        case 'b' -> PieceType.BISHOP;
                        case 'n' -> PieceType.KNIGHT;
                        default -> PieceType.NONE;
                    };
                    if (m.getPromotionPiece() == pt) return m;
                }
            }
        }
        return Move.NULL_MOVE;
    }
}
