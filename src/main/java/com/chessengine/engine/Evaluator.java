package com.chessengine.engine;

import com.chessengine.model.*;

/**
 * Position evaluation function for the chess engine.
 */
public class Evaluator {
    
    // Piece-square tables for positional evaluation
    private static final int[][] PAWN_TABLE = {
        { 0,  0,  0,  0,  0,  0,  0,  0},
        {50, 50, 50, 50, 50, 50, 50, 50},
        {10, 10, 20, 30, 30, 20, 10, 10},
        { 5,  5, 10, 25, 25, 10,  5,  5},
        { 0,  0,  0, 20, 20,  0,  0,  0},
        { 5, -5,-10,  0,  0,-10, -5,  5},
        { 5, 10, 10,-20,-20, 10, 10,  5},
        { 0,  0,  0,  0,  0,  0,  0,  0}
    };
    
    private static final int[][] KNIGHT_TABLE = {
        {-50,-40,-30,-30,-30,-30,-40,-50},
        {-40,-20,  0,  0,  0,  0,-20,-40},
        {-30,  0, 10, 15, 15, 10,  0,-30},
        {-30,  5, 15, 20, 20, 15,  5,-30},
        {-30,  0, 15, 20, 20, 15,  0,-30},
        {-30,  5, 10, 15, 15, 10,  5,-30},
        {-40,-20,  0,  5,  5,  0,-20,-40},
        {-50,-40,-30,-30,-30,-30,-40,-50}
    };
    
    private static final int[][] BISHOP_TABLE = {
        {-20,-10,-10,-10,-10,-10,-10,-20},
        {-10,  0,  0,  0,  0,  0,  0,-10},
        {-10,  0,  5, 10, 10,  5,  0,-10},
        {-10,  5,  5, 10, 10,  5,  5,-10},
        {-10,  0, 10, 10, 10, 10,  0,-10},
        {-10, 10, 10, 10, 10, 10, 10,-10},
        {-10,  5,  0,  0,  0,  0,  5,-10},
        {-20,-10,-10,-10,-10,-10,-10,-20}
    };
    
    private static final int[][] ROOK_TABLE = {
        { 0,  0,  0,  0,  0,  0,  0,  0},
        { 5, 10, 10, 10, 10, 10, 10,  5},
        {-5,  0,  0,  0,  0,  0,  0, -5},
        {-5,  0,  0,  0,  0,  0,  0, -5},
        {-5,  0,  0,  0,  0,  0,  0, -5},
        {-5,  0,  0,  0,  0,  0,  0, -5},
        {-5,  0,  0,  0,  0,  0,  0, -5},
        { 0,  0,  0,  5,  5,  0,  0,  0}
    };
    
    private static final int[][] QUEEN_TABLE = {
        {-20,-10,-10, -5, -5,-10,-10,-20},
        {-10,  0,  0,  0,  0,  0,  0,-10},
        {-10,  0,  5,  5,  5,  5,  0,-10},
        { -5,  0,  5,  5,  5,  5,  0, -5},
        {  0,  0,  5,  5,  5,  5,  0, -5},
        {-10,  5,  5,  5,  5,  5,  0,-10},
        {-10,  0,  5,  0,  0,  0,  0,-10},
        {-20,-10,-10, -5, -5,-10,-10,-20}
    };
    
    private static final int[][] KING_MIDDLE_GAME_TABLE = {
        {-30,-40,-40,-50,-50,-40,-40,-30},
        {-30,-40,-40,-50,-50,-40,-40,-30},
        {-30,-40,-40,-50,-50,-40,-40,-30},
        {-30,-40,-40,-50,-50,-40,-40,-30},
        {-20,-30,-30,-40,-40,-30,-30,-20},
        {-10,-20,-20,-20,-20,-20,-20,-10},
        { 20, 20,  0,  0,  0,  0, 20, 20},
        { 20, 30, 10,  0,  0, 10, 30, 20}
    };
    
    private static final int[][] KING_END_GAME_TABLE = {
        {-50,-40,-30,-20,-20,-30,-40,-50},
        {-30,-20,-10,  0,  0,-10,-20,-30},
        {-30,-10, 20, 30, 30, 20,-10,-30},
        {-30,-10, 30, 40, 40, 30,-10,-30},
        {-30,-10, 30, 40, 40, 30,-10,-30},
        {-30,-10, 20, 30, 30, 20,-10,-30},
        {-30,-30,  0,  0,  0,  0,-30,-30},
        {-50,-30,-30,-30,-30,-30,-30,-50}
    };

    public static int evaluate(Board board) {
        int score = 0;
        
        // Material and positional evaluation
        score += evaluateMaterial(board);
        score += evaluatePosition(board);
        score += evaluateKingSafety(board);
        score += evaluatePawnStructure(board);
        score += evaluateMobility(board);
        
        // Return score from the perspective of the side to move
        return board.getSideToMove() == PieceColor.WHITE ? score : -score;
    }

    private static int evaluateMaterial(Board board) {
        int score = 0;
        
        for (int square = 0; square < 64; square++) {
            Piece piece = board.getPiece(Square.fromIndex(square));
            if (!piece.isEmpty()) {
                int value = piece.getType().getMaterialValue();
                score += piece.isWhite() ? value : -value;
            }
        }
        
        return score;
    }

    private static int evaluatePosition(Board board) {
        int score = 0;
        boolean isEndGame = isEndGame(board);
        
        for (int square = 0; square < 64; square++) {
            Square sq = Square.fromIndex(square);
            Piece piece = board.getPiece(sq);
            
            if (!piece.isEmpty()) {
                int positionalValue = getPositionalValue(piece, sq, isEndGame);
                score += piece.isWhite() ? positionalValue : -positionalValue;
            }
        }
        
        return score;
    }

    private static int getPositionalValue(Piece piece, Square square, boolean isEndGame) {
        int file = square.getFile();
        int rank = square.getRank();
        
        // Flip rank for black pieces
        if (piece.isBlack()) {
            rank = 7 - rank;
        }
        
        return switch (piece.getType()) {
            case PAWN -> PAWN_TABLE[rank][file];
            case KNIGHT -> KNIGHT_TABLE[rank][file];
            case BISHOP -> BISHOP_TABLE[rank][file];
            case ROOK -> ROOK_TABLE[rank][file];
            case QUEEN -> QUEEN_TABLE[rank][file];
            case KING -> isEndGame ? KING_END_GAME_TABLE[rank][file] : KING_MIDDLE_GAME_TABLE[rank][file];
            default -> 0;
        };
    }

    private static int evaluateKingSafety(Board board) {
        int score = 0;
        
        // Evaluate white king safety
        Square whiteKing = board.getKingSquare(PieceColor.WHITE);
        score += evaluateKingSafetyForColor(board, whiteKing, PieceColor.WHITE);
        
        // Evaluate black king safety
        Square blackKing = board.getKingSquare(PieceColor.BLACK);
        score -= evaluateKingSafetyForColor(board, blackKing, PieceColor.BLACK);
        
        return score;
    }

    private static int evaluateKingSafetyForColor(Board board, Square kingSquare, PieceColor color) {
        int safety = 0;
        
        // Penalty for exposed king
        if (board.isSquareAttacked(kingSquare, color.opposite())) {
            safety -= 50;
        }
        
        // Bonus for pawn shield
        int direction = color == PieceColor.WHITE ? 1 : -1;
        for (int fileOffset = -1; fileOffset <= 1; fileOffset++) {
            Square pawnSquare = kingSquare.offset(fileOffset, direction);
            if (pawnSquare.isValid()) {
                Piece piece = board.getPiece(pawnSquare);
                if (piece.getType() == PieceType.PAWN && piece.getColor() == color) {
                    safety += 10;
                }
            }
        }
        
        return safety;
    }

    private static int evaluatePawnStructure(Board board) {
        int score = 0;
        
        // Count pawns per file for both colors
        int[] whitePawnsPerFile = new int[8];
        int[] blackPawnsPerFile = new int[8];
        
        for (int square = 0; square < 64; square++) {
            Square sq = Square.fromIndex(square);
            Piece piece = board.getPiece(sq);
            
            if (piece.getType() == PieceType.PAWN) {
                if (piece.isWhite()) {
                    whitePawnsPerFile[sq.getFile()]++;
                } else {
                    blackPawnsPerFile[sq.getFile()]++;
                }
            }
        }
        
        // Evaluate pawn structure
        for (int file = 0; file < 8; file++) {
            // Doubled pawns penalty
            if (whitePawnsPerFile[file] > 1) {
                score -= 10 * (whitePawnsPerFile[file] - 1);
            }
            if (blackPawnsPerFile[file] > 1) {
                score += 10 * (blackPawnsPerFile[file] - 1);
            }
            
            // Isolated pawns penalty
            boolean whiteIsolated = whitePawnsPerFile[file] > 0 &&
                (file == 0 || whitePawnsPerFile[file - 1] == 0) &&
                (file == 7 || whitePawnsPerFile[file + 1] == 0);
            boolean blackIsolated = blackPawnsPerFile[file] > 0 &&
                (file == 0 || blackPawnsPerFile[file - 1] == 0) &&
                (file == 7 || blackPawnsPerFile[file + 1] == 0);
                
            if (whiteIsolated) score -= 15;
            if (blackIsolated) score += 15;
        }
        
        return score;
    }

    private static int evaluateMobility(Board board) {
        int whiteMobility = MoveGenerator.generateAllMoves(board).size();
        
        // Switch sides to count black mobility
        board.setSideToMove(board.getSideToMove().opposite());
        int blackMobility = MoveGenerator.generateAllMoves(board).size();
        board.setSideToMove(board.getSideToMove().opposite()); // Switch back
        
        return (whiteMobility - blackMobility) * 2;
    }

    private static boolean isEndGame(Board board) {
        int pieceCount = 0;
        int queenCount = 0;
        
        for (int square = 0; square < 64; square++) {
            Piece piece = board.getPiece(Square.fromIndex(square));
            if (!piece.isEmpty() && piece.getType() != PieceType.KING) {
                pieceCount++;
                if (piece.getType() == PieceType.QUEEN) {
                    queenCount++;
                }
            }
        }
        
        // Endgame if few pieces left or no queens
        return pieceCount <= 12 || queenCount == 0;
    }

    public static boolean isDrawByMaterial(Board board) {
        int whitePieces = 0, blackPieces = 0;
        boolean whiteBishop = false, blackBishop = false;
        
        for (int square = 0; square < 64; square++) {
            Piece piece = board.getPiece(Square.fromIndex(square));
            
            if (!piece.isEmpty() && piece.getType() != PieceType.KING) {
                if (piece.isWhite()) {
                    whitePieces++;
                    if (piece.getType() == PieceType.BISHOP) whiteBishop = true;
                } else {
                    blackPieces++;
                    if (piece.getType() == PieceType.BISHOP) blackBishop = true;
                }
                
                // If there are major pieces, it's not a draw
                if (piece.getType() == PieceType.QUEEN || 
                    piece.getType() == PieceType.ROOK || 
                    piece.getType() == PieceType.PAWN) {
                    return false;
                }
            }
        }
        
        // King vs King
        if (whitePieces == 0 && blackPieces == 0) return true;
        
        // King + minor piece vs King
        if ((whitePieces == 1 && blackPieces == 0) || (whitePieces == 0 && blackPieces == 1)) {
            return true;
        }
        
        // King + Bishop vs King + Bishop (same color squares would need more complex check)
        if (whitePieces == 1 && blackPieces == 1 && whiteBishop && blackBishop) {
            return true;
        }
        
        return false;
    }
}
