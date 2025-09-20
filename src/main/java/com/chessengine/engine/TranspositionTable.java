package com.chessengine.engine;

import com.chessengine.model.Move;

/**
 * Fixed-size transposition table using Zobrist keys.
 */
public class TranspositionTable {
    public static final int EXACT = 0;
    public static final int LOWER = 1;
    public static final int UPPER = 2;

    private static final int ENTRY_COUNT = 1 << 20; // ~1M entries
    private static final int MASK = ENTRY_COUNT - 1;

    private final long[] keys = new long[ENTRY_COUNT];
    private final int[] depths = new int[ENTRY_COUNT];
    private final int[] scores = new int[ENTRY_COUNT];
    private final int[] flags = new int[ENTRY_COUNT];
    private final Move[] moves = new Move[ENTRY_COUNT];

    public void clear() {
        for (int i = 0; i < ENTRY_COUNT; i++) {
            keys[i] = 0L; depths[i] = 0; scores[i] = 0; flags[i] = 0; moves[i] = null;
        }
    }

    public void store(long key, int depth, int score, int flag, Move move) {
        int idx = index(key);
        // Replace-if-deeper policy
        if (keys[idx] == 0L || depths[idx] <= depth) {
            keys[idx] = key;
            depths[idx] = depth;
            scores[idx] = score;
            flags[idx] = flag;
            moves[idx] = move;
        }
    }

    public Entry probe(long key) {
        int idx = index(key);
        if (keys[idx] == key) {
            return new Entry(depths[idx], scores[idx], flags[idx], moves[idx]);
        }
        return null;
    }

    public Move getBestMove(long key) {
        int idx = index(key);
        if (keys[idx] == key) return moves[idx];
        return null;
    }

    private int index(long key) {
        return (int) (key & MASK);
    }

    public static class Entry {
        public final int depth;
        public final int score;
        public final int flag;
        public final Move move;
        public Entry(int depth, int score, int flag, Move move) {
            this.depth = depth; this.score = score; this.flag = flag; this.move = move;
        }
    }
}
