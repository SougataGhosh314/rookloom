package com.chessengine.engine;

import com.chessengine.model.*;
import java.util.Random;

/**
 * Zobrist hashing for chess positions.
 * Keys are generated deterministically using a fixed seed for reproducibility.
 */
public final class Zobrist {
    private static final long[][] PIECE_SQUARE = new long[15][64]; // Piece enum has values up to 14
    private static final long[] CASTLING = new long[16]; // 4 bits -> 16 states
    private static final long[] EN_PASSANT_FILE = new long[8];
    private static final long SIDE_TO_MOVE;

    static {
        Random rng = new Random(0xC001D00D); // fixed seed
        for (int p = 0; p < PIECE_SQUARE.length; p++) {
            for (int s = 0; s < 64; s++) {
                PIECE_SQUARE[p][s] = rng.nextLong();
            }
        }
        for (int i = 0; i < CASTLING.length; i++) CASTLING[i] = rng.nextLong();
        for (int f = 0; f < 8; f++) EN_PASSANT_FILE[f] = rng.nextLong();
        SIDE_TO_MOVE = rng.nextLong();
    }

    private Zobrist() {}

    public static long hash(Board board) {
        long h = 0L;
        // pieces
        for (int idx = 0; idx < 64; idx++) {
            Piece piece = board.getPiece(Square.fromIndex(idx));
            if (!piece.isEmpty()) {
                int p = piece.getValue();
                h ^= PIECE_SQUARE[p][idx];
            }
        }
        // side to move
        if (board.getSideToMove() == PieceColor.BLACK) {
            h ^= SIDE_TO_MOVE;
        }
        // castling rights
        h ^= CASTLING[board.getCastlingRights() & 0xF];
        // en passant file (if valid)
        Square ep = board.getEnPassantSquare();
        if (ep != null && ep.isValid()) {
            h ^= EN_PASSANT_FILE[ep.getFile()];
        }
        return h;
    }
}
