package com.chessengine.model;

/**
 * Represents a square on the chess board using 0x88 representation for efficient bounds checking.
 */
public class Square {
    public static final int INVALID = -1;
    
    // Board boundaries
    public static final int MIN_FILE = 0;
    public static final int MAX_FILE = 7;
    public static final int MIN_RANK = 0;
    public static final int MAX_RANK = 7;

    private final int index;

    public Square(int file, int rank) {
        if (!isValidFileRank(file, rank)) {
            this.index = INVALID;
        } else {
            this.index = rank * 8 + file;
        }
    }

    public Square(int index) {
        this.index = isValidIndex(index) ? index : INVALID;
    }

    public Square(String algebraic) {
        if (algebraic == null || algebraic.length() != 2) {
            this.index = INVALID;
            return;
        }
        
        char fileChar = algebraic.charAt(0);
        char rankChar = algebraic.charAt(1);
        
        if (fileChar < 'a' || fileChar > 'h' || rankChar < '1' || rankChar > '8') {
            this.index = INVALID;
            return;
        }
        
        int file = fileChar - 'a';
        int rank = rankChar - '1';
        this.index = rank * 8 + file;
    }

    public int getIndex() {
        return index;
    }

    public int getFile() {
        return isValid() ? index % 8 : -1;
    }

    public int getRank() {
        return isValid() ? index / 8 : -1;
    }

    public boolean isValid() {
        return index != INVALID && index >= 0 && index < 64;
    }

    public String toAlgebraic() {
        if (!isValid()) return "??";
        return String.valueOf((char)('a' + getFile())) + (getRank() + 1);
    }

    public Square offset(int fileOffset, int rankOffset) {
        if (!isValid()) return new Square(INVALID);
        return new Square(getFile() + fileOffset, getRank() + rankOffset);
    }

    public static boolean isValidFileRank(int file, int rank) {
        return file >= MIN_FILE && file <= MAX_FILE && rank >= MIN_RANK && rank <= MAX_RANK;
    }

    public static boolean isValidIndex(int index) {
        return index >= 0 && index < 64;
    }

    public static Square fromAlgebraic(String algebraic) {
        return new Square(algebraic);
    }

    public static Square fromIndex(int index) {
        return new Square(index);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Square square = (Square) obj;
        return index == square.index;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(index);
    }

    @Override
    public String toString() {
        return toAlgebraic();
    }
}
