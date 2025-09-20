package com.chessengine.model;

/**
 * Enumeration of piece colors.
 */
public enum PieceColor {
    NONE(0),
    WHITE(1),
    BLACK(2);

    private final int value;

    PieceColor(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public PieceColor opposite() {
        return switch (this) {
            case WHITE -> BLACK;
            case BLACK -> WHITE;
            default -> NONE;
        };
    }

    public static PieceColor fromValue(int value) {
        for (PieceColor color : values()) {
            if (color.value == value) {
                return color;
            }
        }
        return NONE;
    }
}
