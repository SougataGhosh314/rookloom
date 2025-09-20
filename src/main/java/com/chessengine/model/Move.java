package com.chessengine.model;

/**
 * Represents a chess move with all necessary information for efficient move generation and undo.
 */
public class Move {
    public static final Move NULL_MOVE = new Move();

    // Move flags
    public static final int QUIET = 0;
    public static final int DOUBLE_PAWN_PUSH = 1;
    public static final int KING_CASTLE = 2;
    public static final int QUEEN_CASTLE = 3;
    public static final int CAPTURE = 4;
    public static final int EN_PASSANT = 5;
    public static final int KNIGHT_PROMOTION = 8;
    public static final int BISHOP_PROMOTION = 9;
    public static final int ROOK_PROMOTION = 10;
    public static final int QUEEN_PROMOTION = 11;
    public static final int KNIGHT_PROMOTION_CAPTURE = 12;
    public static final int BISHOP_PROMOTION_CAPTURE = 13;
    public static final int ROOK_PROMOTION_CAPTURE = 14;
    public static final int QUEEN_PROMOTION_CAPTURE = 15;

    private final Square from;
    private final Square to;
    private final int flags;
    private final Piece movingPiece;
    private final Piece capturedPiece;

    // Null move constructor
    private Move() {
        this.from = new Square(Square.INVALID);
        this.to = new Square(Square.INVALID);
        this.flags = QUIET;
        this.movingPiece = Piece.NONE;
        this.capturedPiece = Piece.NONE;
    }

    public Move(Square from, Square to, int flags, Piece movingPiece, Piece capturedPiece) {
        this.from = from;
        this.to = to;
        this.flags = flags;
        this.movingPiece = movingPiece;
        this.capturedPiece = capturedPiece;
    }

    public Move(Square from, Square to, Piece movingPiece) {
        this(from, to, QUIET, movingPiece, Piece.NONE);
    }

    public Square getFrom() {
        return from;
    }

    public Square getTo() {
        return to;
    }

    public int getFlags() {
        return flags;
    }

    public Piece getMovingPiece() {
        return movingPiece;
    }

    public Piece getCapturedPiece() {
        return capturedPiece;
    }

    public boolean isCapture() {
        return (flags & CAPTURE) != 0 || flags == EN_PASSANT;
    }

    public boolean isPromotion() {
        return flags >= KNIGHT_PROMOTION;
    }

    public boolean isCastle() {
        return flags == KING_CASTLE || flags == QUEEN_CASTLE;
    }

    public boolean isEnPassant() {
        return flags == EN_PASSANT;
    }

    public boolean isDoublePawnPush() {
        return flags == DOUBLE_PAWN_PUSH;
    }

    public PieceType getPromotionPiece() {
        return switch (flags) {
            case KNIGHT_PROMOTION, KNIGHT_PROMOTION_CAPTURE -> PieceType.KNIGHT;
            case BISHOP_PROMOTION, BISHOP_PROMOTION_CAPTURE -> PieceType.BISHOP;
            case ROOK_PROMOTION, ROOK_PROMOTION_CAPTURE -> PieceType.ROOK;
            case QUEEN_PROMOTION, QUEEN_PROMOTION_CAPTURE -> PieceType.QUEEN;
            default -> PieceType.NONE;
        };
    }

    public boolean isNull() {
        return this == NULL_MOVE || !from.isValid() || !to.isValid();
    }

    public String toAlgebraic() {
        if (isNull()) return "0000";
        
        String move = from.toAlgebraic() + to.toAlgebraic();
        
        if (isPromotion()) {
            PieceType promotionPiece = getPromotionPiece();
            move += switch (promotionPiece) {
                case KNIGHT -> "n";
                case BISHOP -> "b";
                case ROOK -> "r";
                case QUEEN -> "q";
                default -> "";
            };
        }
        
        return move;
    }

    public static Move fromAlgebraic(String algebraic, Piece movingPiece) {
        if (algebraic == null || algebraic.length() < 4) {
            return NULL_MOVE;
        }
        
        Square from = new Square(algebraic.substring(0, 2));
        Square to = new Square(algebraic.substring(2, 4));
        
        if (!from.isValid() || !to.isValid()) {
            return NULL_MOVE;
        }
        
        int flags = QUIET;
        if (algebraic.length() == 5) {
            char promotion = algebraic.charAt(4);
            flags = switch (promotion) {
                case 'n' -> KNIGHT_PROMOTION;
                case 'b' -> BISHOP_PROMOTION;
                case 'r' -> ROOK_PROMOTION;
                case 'q' -> QUEEN_PROMOTION;
                default -> QUIET;
            };
        }
        
        return new Move(from, to, flags, movingPiece, Piece.NONE);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Move move = (Move) obj;
        return flags == move.flags &&
               from.equals(move.from) &&
               to.equals(move.to) &&
               movingPiece == move.movingPiece;
    }

    @Override
    public int hashCode() {
        return from.hashCode() ^ (to.hashCode() << 6) ^ (flags << 12);
    }

    @Override
    public String toString() {
        return toAlgebraic();
    }
}
