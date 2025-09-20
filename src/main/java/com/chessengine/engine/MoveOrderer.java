package com.chessengine.engine;

import com.chessengine.model.*;
import java.util.*;

/**
 * Move ordering for improved alpha-beta pruning efficiency.
 */
public class MoveOrderer {
    
    // Move scoring constants
    private static final int WINNING_CAPTURE_SCORE = 8000;
    private static final int EQUAL_CAPTURE_SCORE = 7000;
    private static final int PROMOTION_SCORE = 6000;
    private static final int KILLER_MOVE_SCORE = 5000;
    private static final int CASTLE_SCORE = 4000;
    private static final int LOSING_CAPTURE_SCORE = 1000;
    
    // Killer moves (moves that caused cutoffs at the same depth)
    private final Move[][] killerMoves = new Move[64][2]; // [depth][slot]
    
    // History heuristic (moves that historically performed well)
    private final int[][][][] historyTable = new int[2][64][64][64]; // [color][from][to][piece]

    public void orderMoves(List<Move> moves, Board board) {
        // Create move-score pairs
        List<MoveScore> moveScores = new ArrayList<>();
        
        for (Move move : moves) {
            int score = scoreMove(move, board);
            moveScores.add(new MoveScore(move, score));
        }
        
        // Sort by score (highest first)
        moveScores.sort((a, b) -> Integer.compare(b.score, a.score));
        
        // Update the original list
        moves.clear();
        for (MoveScore ms : moveScores) {
            moves.add(ms.move);
        }
    }

    private int scoreMove(Move move, Board board) {
        int score = 0;
        
        // Hash move (from transposition table) gets highest priority
        // This would be set by the search function
        
        // Captures
        if (move.isCapture()) {
            score += scoreCaptureMove(move, board);
        }
        
        // Promotions
        if (move.isPromotion()) {
            score += PROMOTION_SCORE;
            // Prefer queen promotions
            if (move.getPromotionPiece() == PieceType.QUEEN) {
                score += 1000;
            }
        }
        
        // Castling
        if (move.isCastle()) {
            score += CASTLE_SCORE;
        }
        
        // Killer moves
        score += scoreKillerMove(move, 0); // depth would be passed from search
        
        // History heuristic
        score += getHistoryScore(move, board);
        
        // Positional bonuses
        score += scorePositionalMove(move, board);
        
        return score;
    }

    private int scoreCaptureMove(Move move, Board board) {
        Piece capturedPiece = move.getCapturedPiece();
        Piece movingPiece = move.getMovingPiece();
        
        if (capturedPiece.isEmpty()) {
            // En passant
            if (move.isEnPassant()) {
                return EQUAL_CAPTURE_SCORE;
            }
            return 0;
        }
        
        // MVV-LVA (Most Valuable Victim - Least Valuable Attacker)
        int captureValue = capturedPiece.getType().getMaterialValue();
        int attackerValue = movingPiece.getType().getMaterialValue();
        
        // Basic capture scoring
        if (captureValue > attackerValue) {
            return WINNING_CAPTURE_SCORE + captureValue - attackerValue;
        } else if (captureValue == attackerValue) {
            return EQUAL_CAPTURE_SCORE;
        } else {
            // Check if the capture is safe using SEE (Static Exchange Evaluation)
            if (isCaptureSafe(move, board)) {
                return EQUAL_CAPTURE_SCORE + captureValue - attackerValue;
            } else {
                return LOSING_CAPTURE_SCORE + captureValue - attackerValue;
            }
        }
    }

    private boolean isCaptureSafe(Move move, Board board) {
        // Simplified SEE - just check if the target square is defended
        Square targetSquare = move.getTo();
        PieceColor opponent = board.getSideToMove().opposite();
        
        // Make the capture
        board.makeMove(move);
        boolean isDefended = board.isSquareAttacked(targetSquare, opponent);
        board.undoMove();
        
        return !isDefended;
    }

    private int scoreKillerMove(Move move, int depth) {
        if (depth >= killerMoves.length) return 0;
        
        for (Move killer : killerMoves[depth]) {
            if (killer != null && killer.equals(move)) {
                return KILLER_MOVE_SCORE;
            }
        }
        return 0;
    }

    private int getHistoryScore(Move move, Board board) {
        int colorIndex = board.getSideToMove() == PieceColor.WHITE ? 0 : 1;
        int fromIndex = move.getFrom().getIndex();
        int toIndex = move.getTo().getIndex();
        int pieceIndex = move.getMovingPiece().getType().getValue();
        
        if (fromIndex < 64 && toIndex < 64 && pieceIndex < 64) {
            return historyTable[colorIndex][fromIndex][toIndex][pieceIndex] / 10;
        }
        return 0;
    }

    private int scorePositionalMove(Move move, Board board) {
        int score = 0;
        
        // Prefer moves towards the center
        Square to = move.getTo();
        double centerDistance = Math.abs(to.getFile() - 3.5) + Math.abs(to.getRank() - 3.5);
        score += (int) ((7 - centerDistance) * 2);
        
        // Prefer moves that develop pieces
        if (isDevelopmentMove(move, board)) {
            score += 50;
        }
        
        // Prefer moves that attack opponent pieces
        if (attacksOpponentPiece(move, board)) {
            score += 20;
        }
        
        return score;
    }

    private boolean isDevelopmentMove(Move move, Board board) {
        Piece piece = move.getMovingPiece();
        Square from = move.getFrom();
        
        // Knights and bishops moving from back rank
        if (piece.getType() == PieceType.KNIGHT || piece.getType() == PieceType.BISHOP) {
            int backRank = piece.isWhite() ? 0 : 7;
            return from.getRank() == backRank;
        }
        
        return false;
    }

    private boolean attacksOpponentPiece(Move move, Board board) {
        Square to = move.getTo();
        PieceColor opponent = board.getSideToMove().opposite();
        
        // Check adjacent squares for opponent pieces
        for (int fileOffset = -1; fileOffset <= 1; fileOffset++) {
            for (int rankOffset = -1; rankOffset <= 1; rankOffset++) {
                if (fileOffset == 0 && rankOffset == 0) continue;
                
                Square adjacent = to.offset(fileOffset, rankOffset);
                if (adjacent.isValid()) {
                    Piece piece = board.getPiece(adjacent);
                    if (!piece.isEmpty() && piece.getColor() == opponent) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }

    public void addKillerMove(Move move, int depth) {
        if (depth >= killerMoves.length || move.isCapture()) return;
        
        // Shift killer moves
        if (!move.equals(killerMoves[depth][0])) {
            killerMoves[depth][1] = killerMoves[depth][0];
            killerMoves[depth][0] = move;
        }
    }

    public void updateHistory(Move move, Board board, int depth) {
        if (move.isCapture()) return; // Don't update history for captures
        
        int colorIndex = board.getSideToMove() == PieceColor.WHITE ? 0 : 1;
        int fromIndex = move.getFrom().getIndex();
        int toIndex = move.getTo().getIndex();
        int pieceIndex = move.getMovingPiece().getType().getValue();
        
        if (fromIndex < 64 && toIndex < 64 && pieceIndex < 64) {
            historyTable[colorIndex][fromIndex][toIndex][pieceIndex] += depth * depth;
            
            // Prevent overflow
            if (historyTable[colorIndex][fromIndex][toIndex][pieceIndex] > 10000) {
                // Age all history scores
                for (int c = 0; c < 2; c++) {
                    for (int f = 0; f < 64; f++) {
                        for (int t = 0; t < 64; t++) {
                            for (int p = 0; p < 64; p++) {
                                historyTable[c][f][t][p] /= 2;
                            }
                        }
                    }
                }
            }
        }
    }

    public void clearKillerMoves() {
        for (int depth = 0; depth < killerMoves.length; depth++) {
            killerMoves[depth][0] = null;
            killerMoves[depth][1] = null;
        }
    }

    public void clearHistory() {
        for (int c = 0; c < 2; c++) {
            for (int f = 0; f < 64; f++) {
                for (int t = 0; t < 64; t++) {
                    for (int p = 0; p < 64; p++) {
                        historyTable[c][f][t][p] = 0;
                    }
                }
            }
        }
    }

    // Helper class for move scoring
    private static class MoveScore {
        final Move move;
        final int score;

        MoveScore(Move move, int score) {
            this.move = move;
            this.score = score;
        }
    }
}
