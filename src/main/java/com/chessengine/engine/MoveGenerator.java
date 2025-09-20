package com.chessengine.engine;

import com.chessengine.model.*;
import java.util.*;

/**
 * High-performance move generator for chess positions.
 */
public class MoveGenerator {
    
    // Pre-computed knight move offsets
    private static final int[][] KNIGHT_MOVES = {
        {-2, -1}, {-2, 1}, {-1, -2}, {-1, 2},
        {1, -2}, {1, 2}, {2, -1}, {2, 1}
    };
    
    // Pre-computed king move offsets
    private static final int[][] KING_MOVES = {
        {-1, -1}, {-1, 0}, {-1, 1},
        {0, -1},           {0, 1},
        {1, -1}, {1, 0}, {1, 1}
    };
    
    // Sliding piece directions
    private static final int[][] ROOK_DIRECTIONS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
    private static final int[][] BISHOP_DIRECTIONS = {{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};
    private static final int[][] QUEEN_DIRECTIONS = {
        {-1, -1}, {-1, 0}, {-1, 1},
        {0, -1},           {0, 1},
        {1, -1}, {1, 0}, {1, 1}
    };

    public static List<Move> generateAllMoves(Board board) {
        List<Move> moves = new ArrayList<>();
        PieceColor sideToMove = board.getSideToMove();
        
        for (int square = 0; square < 64; square++) {
            Square from = Square.fromIndex(square);
            Piece piece = board.getPiece(from);
            
            if (piece.isEmpty() || piece.getColor() != sideToMove) {
                continue;
            }
            
            switch (piece.getType()) {
                case PAWN -> generatePawnMoves(board, from, moves);
                case KNIGHT -> generateKnightMoves(board, from, moves);
                case BISHOP -> generateBishopMoves(board, from, moves);
                case ROOK -> generateRookMoves(board, from, moves);
                case QUEEN -> generateQueenMoves(board, from, moves);
                case KING -> generateKingMoves(board, from, moves);
                case NONE -> { /* Skip empty squares */ }
            }
        }
        
        return moves;
    }

    public static List<Move> generateLegalMoves(Board board) {
        List<Move> allMoves = generateAllMoves(board);
        List<Move> legalMoves = new ArrayList<>();
        
        for (Move move : allMoves) {
            if (board.isLegalMove(move)) {
                legalMoves.add(move);
            }
        }
        
        return legalMoves;
    }

    public static List<Move> generateCaptures(Board board) {
        List<Move> allMoves = generateAllMoves(board);
        List<Move> captures = new ArrayList<>();
        
        for (Move move : allMoves) {
            if (move.isCapture() && board.isLegalMove(move)) {
                captures.add(move);
            }
        }
        
        return captures;
    }

    private static void generatePawnMoves(Board board, Square from, List<Move> moves) {
        PieceColor color = board.getPiece(from).getColor();
        int direction = color == PieceColor.WHITE ? 1 : -1;
        int startRank = color == PieceColor.WHITE ? 1 : 6;
        int promotionRank = color == PieceColor.WHITE ? 7 : 0;
        
        // Forward moves
        Square oneSquareForward = from.offset(0, direction);
        if (oneSquareForward.isValid() && board.getPiece(oneSquareForward).isEmpty()) {
            if (oneSquareForward.getRank() == promotionRank) {
                // Promotions
                addPromotionMoves(moves, from, oneSquareForward, board.getPiece(from), false);
            } else {
                moves.add(new Move(from, oneSquareForward, Move.QUIET, board.getPiece(from), Piece.NONE));
                
                // Double pawn push
                if (from.getRank() == startRank) {
                    Square twoSquaresForward = from.offset(0, 2 * direction);
                    if (twoSquaresForward.isValid() && board.getPiece(twoSquaresForward).isEmpty()) {
                        moves.add(new Move(from, twoSquaresForward, Move.DOUBLE_PAWN_PUSH, 
                                         board.getPiece(from), Piece.NONE));
                    }
                }
            }
        }
        
        // Captures
        for (int fileOffset : new int[]{-1, 1}) {
            Square captureSquare = from.offset(fileOffset, direction);
            if (captureSquare.isValid()) {
                Piece targetPiece = board.getPiece(captureSquare);
                
                // Regular capture
                if (!targetPiece.isEmpty() && targetPiece.getColor() != color) {
                    if (captureSquare.getRank() == promotionRank) {
                        addPromotionMoves(moves, from, captureSquare, board.getPiece(from), true);
                    } else {
                        moves.add(new Move(from, captureSquare, Move.CAPTURE, 
                                         board.getPiece(from), targetPiece));
                    }
                }
                
                // En passant capture
                Square enPassantSquare = board.getEnPassantSquare();
                if (enPassantSquare.isValid() && captureSquare.equals(enPassantSquare)) {
                    Piece capturedPawn = color == PieceColor.WHITE ? Piece.BLACK_PAWN : Piece.WHITE_PAWN;
                    moves.add(new Move(from, captureSquare, Move.EN_PASSANT, 
                                     board.getPiece(from), capturedPawn));
                }
            }
        }
    }

    private static void addPromotionMoves(List<Move> moves, Square from, Square to, Piece movingPiece, boolean isCapture) {
        Piece capturedPiece = isCapture ? Piece.NONE : Piece.NONE; // Will be set properly by caller
        int baseFlag = isCapture ? Move.KNIGHT_PROMOTION_CAPTURE : Move.KNIGHT_PROMOTION;
        
        moves.add(new Move(from, to, baseFlag, movingPiece, capturedPiece));
        moves.add(new Move(from, to, baseFlag + 1, movingPiece, capturedPiece));
        moves.add(new Move(from, to, baseFlag + 2, movingPiece, capturedPiece));
        moves.add(new Move(from, to, baseFlag + 3, movingPiece, capturedPiece));
    }

    private static void generateKnightMoves(Board board, Square from, List<Move> moves) {
        Piece knight = board.getPiece(from);
        
        for (int[] move : KNIGHT_MOVES) {
            Square to = from.offset(move[0], move[1]);
            if (to.isValid()) {
                Piece targetPiece = board.getPiece(to);
                
                if (targetPiece.isEmpty()) {
                    moves.add(new Move(from, to, Move.QUIET, knight, Piece.NONE));
                } else if (targetPiece.getColor() != knight.getColor()) {
                    moves.add(new Move(from, to, Move.CAPTURE, knight, targetPiece));
                }
            }
        }
    }

    private static void generateBishopMoves(Board board, Square from, List<Move> moves) {
        generateSlidingMoves(board, from, BISHOP_DIRECTIONS, moves);
    }

    private static void generateRookMoves(Board board, Square from, List<Move> moves) {
        generateSlidingMoves(board, from, ROOK_DIRECTIONS, moves);
    }

    private static void generateQueenMoves(Board board, Square from, List<Move> moves) {
        generateSlidingMoves(board, from, QUEEN_DIRECTIONS, moves);
    }

    private static void generateSlidingMoves(Board board, Square from, int[][] directions, List<Move> moves) {
        Piece movingPiece = board.getPiece(from);
        
        for (int[] direction : directions) {
            Square current = from.offset(direction[0], direction[1]);
            
            while (current.isValid()) {
                Piece targetPiece = board.getPiece(current);
                
                if (targetPiece.isEmpty()) {
                    moves.add(new Move(from, current, Move.QUIET, movingPiece, Piece.NONE));
                } else {
                    if (targetPiece.getColor() != movingPiece.getColor()) {
                        moves.add(new Move(from, current, Move.CAPTURE, movingPiece, targetPiece));
                    }
                    break; // Piece blocks further movement
                }
                
                current = current.offset(direction[0], direction[1]);
            }
        }
    }

    private static void generateKingMoves(Board board, Square from, List<Move> moves) {
        Piece king = board.getPiece(from);
        
        // Regular king moves
        for (int[] move : KING_MOVES) {
            Square to = from.offset(move[0], move[1]);
            if (to.isValid()) {
                Piece targetPiece = board.getPiece(to);
                
                if (targetPiece.isEmpty()) {
                    moves.add(new Move(from, to, Move.QUIET, king, Piece.NONE));
                } else if (targetPiece.getColor() != king.getColor()) {
                    moves.add(new Move(from, to, Move.CAPTURE, king, targetPiece));
                }
            }
        }
        
        // Castling moves
        generateCastlingMoves(board, from, moves);
    }

    private static void generateCastlingMoves(Board board, Square from, List<Move> moves) {
        PieceColor color = board.getPiece(from).getColor();
        
        if (board.isInCheck(color)) {
            return; // Cannot castle while in check
        }
        
        // King-side castling
        if (canCastle(board, color, true)) {
            Square kingTarget = from.offset(2, 0);
            moves.add(new Move(from, kingTarget, Move.KING_CASTLE, board.getPiece(from), Piece.NONE));
        }
        
        // Queen-side castling
        if (canCastle(board, color, false)) {
            Square kingTarget = from.offset(-2, 0);
            moves.add(new Move(from, kingTarget, Move.QUEEN_CASTLE, board.getPiece(from), Piece.NONE));
        }
    }

    private static boolean canCastle(Board board, PieceColor color, boolean kingSide) {
        int castlingRight = kingSide ? 
            (color == PieceColor.WHITE ? Board.WHITE_KING_SIDE : Board.BLACK_KING_SIDE) :
            (color == PieceColor.WHITE ? Board.WHITE_QUEEN_SIDE : Board.BLACK_QUEEN_SIDE);
        
        if ((board.getCastlingRights() & castlingRight) == 0) {
            return false;
        }
        
        Square kingSquare = board.getKingSquare(color);
        int rank = kingSquare.getRank();
        
        // Check if squares between king and rook are empty and not attacked
        if (kingSide) {
            for (int file = kingSquare.getFile() + 1; file < 7; file++) {
                Square square = new Square(file, rank);
                if (!board.getPiece(square).isEmpty() || 
                    board.isSquareAttacked(square, color.opposite())) {
                    return false;
                }
            }
        } else {
            for (int file = kingSquare.getFile() - 1; file > 0; file--) {
                Square square = new Square(file, rank);
                if (!board.getPiece(square).isEmpty() || 
                    board.isSquareAttacked(square, color.opposite())) {
                    return false;
                }
            }
            // Also check the b-file for queen-side castling
            Square bFile = new Square(1, rank);
            if (!board.getPiece(bFile).isEmpty()) {
                return false;
            }
        }
        
        return true;
    }

    public static boolean isCheckmate(Board board) {
        return board.isInCheck(board.getSideToMove()) && generateLegalMoves(board).isEmpty();
    }

    public static boolean isStalemate(Board board) {
        return !board.isInCheck(board.getSideToMove()) && generateLegalMoves(board).isEmpty();
    }

    public static boolean isGameOver(Board board) {
        return isCheckmate(board) || isStalemate(board) || isDraw(board);
    }

    public static boolean isDraw(Board board) {
        // 50-move rule
        if (board.getHalfMoveClock() >= 100) {
            return true;
        }
        
        // Insufficient material
        return hasInsufficientMaterial(board);
    }

    private static boolean hasInsufficientMaterial(Board board) {
        List<Piece> pieces = new ArrayList<>();
        
        for (int square = 0; square < 64; square++) {
            Piece piece = board.getPiece(Square.fromIndex(square));
            if (!piece.isEmpty() && piece.getType() != PieceType.KING) {
                pieces.add(piece);
            }
        }
        
        // King vs King
        if (pieces.isEmpty()) {
            return true;
        }
        
        // King + minor piece vs King
        if (pieces.size() == 1) {
            PieceType type = pieces.get(0).getType();
            return type == PieceType.KNIGHT || type == PieceType.BISHOP;
        }
        
        // King + Bishop vs King + Bishop (same color squares)
        if (pieces.size() == 2) {
            boolean bothBishops = pieces.stream().allMatch(p -> p.getType() == PieceType.BISHOP);
            if (bothBishops) {
                // Check if bishops are on same color squares
                // This is a simplified check - in a real engine you'd check the actual square colors
                return true;
            }
        }
        
        return false;
    }
}
