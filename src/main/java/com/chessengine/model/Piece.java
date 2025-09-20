package com.chessengine.model;

/**
 * Represents a chess piece with its type and color.
 * Uses efficient integer representation for performance.
 */
public enum Piece {
    // Empty square
    NONE(0, PieceType.NONE, PieceColor.NONE),
    
    // White pieces
    WHITE_PAWN(1, PieceType.PAWN, PieceColor.WHITE),
    WHITE_KNIGHT(2, PieceType.KNIGHT, PieceColor.WHITE),
    WHITE_BISHOP(3, PieceType.BISHOP, PieceColor.WHITE),
    WHITE_ROOK(4, PieceType.ROOK, PieceColor.WHITE),
    WHITE_QUEEN(5, PieceType.QUEEN, PieceColor.WHITE),
    WHITE_KING(6, PieceType.KING, PieceColor.WHITE),
    
    // Black pieces
    BLACK_PAWN(9, PieceType.PAWN, PieceColor.BLACK),
    BLACK_KNIGHT(10, PieceType.KNIGHT, PieceColor.BLACK),
    BLACK_BISHOP(11, PieceType.BISHOP, PieceColor.BLACK),
    BLACK_ROOK(12, PieceType.ROOK, PieceColor.BLACK),
    BLACK_QUEEN(13, PieceType.QUEEN, PieceColor.BLACK),
    BLACK_KING(14, PieceType.KING, PieceColor.BLACK);

    private final int value;
    private final PieceType type;
    private final PieceColor color;

    Piece(int value, PieceType type, PieceColor color) {
        this.value = value;
        this.type = type;
        this.color = color;
    }

    public int getValue() {
        return value;
    }

    public PieceType getType() {
        return type;
    }

    public PieceColor getColor() {
        return color;
    }

    public boolean isWhite() {
        return color == PieceColor.WHITE;
    }

    public boolean isBlack() {
        return color == PieceColor.BLACK;
    }

    public boolean isEmpty() {
        return this == NONE;
    }

    public static Piece fromValue(int value) {
        for (Piece piece : values()) {
            if (piece.value == value) {
                return piece;
            }
        }
        return NONE;
    }

    public static Piece createPiece(PieceType type, PieceColor color) {
        if (type == PieceType.NONE || color == PieceColor.NONE) {
            return NONE;
        }
        
        for (Piece piece : values()) {
            if (piece.type == type && piece.color == color) {
                return piece;
            }
        }
        return NONE;
    }

    @Override
    public String toString() {
        if (this == NONE) return " ";
        
        String symbol = switch (type) {
            case PAWN -> "P";
            case KNIGHT -> "N";
            case BISHOP -> "B";
            case ROOK -> "R";
            case QUEEN -> "Q";
            case KING -> "K";
            default -> " ";
        };
        
        return isWhite() ? symbol : symbol.toLowerCase();
    }
}
