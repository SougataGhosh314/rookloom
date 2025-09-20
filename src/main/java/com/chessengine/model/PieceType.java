package com.chessengine.model;

/**
 * Enumeration of chess piece types.
 */
public enum PieceType {
    NONE(0, 0),
    PAWN(1, 100),
    KNIGHT(2, 320),
    BISHOP(3, 330),
    ROOK(4, 500),
    QUEEN(5, 900),
    KING(6, 20000);

    private final int value;
    private final int materialValue;

    PieceType(int value, int materialValue) {
        this.value = value;
        this.materialValue = materialValue;
    }

    public int getValue() {
        return value;
    }

    public int getMaterialValue() {
        return materialValue;
    }

    public boolean isSliding() {
        return this == BISHOP || this == ROOK || this == QUEEN;
    }

    public static PieceType fromValue(int value) {
        for (PieceType type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        return NONE;
    }
}
