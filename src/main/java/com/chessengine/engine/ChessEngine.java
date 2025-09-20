package com.chessengine.engine;

import com.chessengine.model.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * High-performance chess engine with minimax algorithm, alpha-beta pruning, and advanced optimizations.
 */
public class ChessEngine {
    
    // Engine configuration
    private int maxDepth = 6;
    private long maxTimeMs = 5000; // 5 seconds default
    private boolean useTranspositionTable = true;
    private boolean useIterativeDeepening = true;
    private boolean useQuiescenceSearch = true;
    
    // Search statistics
    private long nodesSearched = 0;
    private long transpositionHits = 0;
    private long cutoffs = 0;
    
    // Transposition table
    private final Map<Long, TranspositionEntry> transpositionTable = new ConcurrentHashMap<>();
    private static final int MAX_TT_SIZE = 1000000;
    
    // Move ordering
    private final MoveOrderer moveOrderer = new MoveOrderer();
    
    // Search control
    private volatile boolean searchCancelled = false;
    private long searchStartTime = 0;

    public ChessEngine() {
        this(6, 5000);
    }

    public ChessEngine(int maxDepth, long maxTimeMs) {
        this.maxDepth = maxDepth;
        this.maxTimeMs = maxTimeMs;
    }

    public Move findBestMove(Board board) {
        resetSearchStats();
        searchCancelled = false;
        searchStartTime = System.currentTimeMillis();
        
        Move bestMove = Move.NULL_MOVE;
        
        if (useIterativeDeepening) {
            bestMove = iterativeDeepening(board);
        } else {
            SearchResult result = minimax(board, maxDepth, Integer.MIN_VALUE, Integer.MAX_VALUE, true);
            bestMove = result.bestMove;
        }
        
        printSearchStats();
        return bestMove;
    }

    private Move iterativeDeepening(Board board) {
        Move bestMove = Move.NULL_MOVE;
        int bestScore = Integer.MIN_VALUE;
        
        for (int depth = 1; depth <= maxDepth; depth++) {
            if (isTimeUp()) break;
            
            SearchResult result = minimax(board, depth, Integer.MIN_VALUE, Integer.MAX_VALUE, true);
            
            if (!searchCancelled && !result.bestMove.isNull()) {
                bestMove = result.bestMove;
                bestScore = result.score;
                
                System.out.printf("Depth %d: Score %d, Move %s, Nodes %d%n", 
                    depth, bestScore, bestMove.toAlgebraic(), nodesSearched);
            }
            
            // If we found a mate, no need to search deeper
            if (Math.abs(bestScore) > 19000) {
                break;
            }
        }
        
        return bestMove;
    }

    private SearchResult minimax(Board board, int depth, int alpha, int beta, boolean maximizingPlayer) {
        nodesSearched++;
        
        // Check time limit
        if (isTimeUp()) {
            searchCancelled = true;
            return new SearchResult(Move.NULL_MOVE, 0);
        }
        
        // Check transposition table
        long boardHash = getBoardHash(board);
        if (useTranspositionTable && transpositionTable.containsKey(boardHash)) {
            TranspositionEntry entry = transpositionTable.get(boardHash);
            if (entry.depth >= depth) {
                transpositionHits++;
                if (entry.nodeType == TranspositionEntry.EXACT ||
                    (entry.nodeType == TranspositionEntry.LOWER_BOUND && entry.score >= beta) ||
                    (entry.nodeType == TranspositionEntry.UPPER_BOUND && entry.score <= alpha)) {
                    return new SearchResult(entry.bestMove, entry.score);
                }
            }
        }
        
        // Terminal node evaluation
        if (depth == 0) {
            int score = useQuiescenceSearch ? quiescenceSearch(board, alpha, beta, 4) : Evaluator.evaluate(board);
            return new SearchResult(Move.NULL_MOVE, score);
        }
        
        // Check for game over
        List<Move> legalMoves = MoveGenerator.generateLegalMoves(board);
        if (legalMoves.isEmpty()) {
            if (board.isInCheck(board.getSideToMove())) {
                // Checkmate
                return new SearchResult(Move.NULL_MOVE, maximizingPlayer ? -20000 + (maxDepth - depth) : 20000 - (maxDepth - depth));
            } else {
                // Stalemate
                return new SearchResult(Move.NULL_MOVE, 0);
            }
        }
        
        // Order moves for better alpha-beta pruning
        moveOrderer.orderMoves(legalMoves, board);
        
        Move bestMove = Move.NULL_MOVE;
        int bestScore = maximizingPlayer ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        int originalAlpha = alpha;
        
        for (Move move : legalMoves) {
            if (searchCancelled) break;
            
            board.makeMove(move);
            SearchResult result = minimax(board, depth - 1, alpha, beta, !maximizingPlayer);
            board.undoMove();
            
            if (maximizingPlayer) {
                if (result.score > bestScore) {
                    bestScore = result.score;
                    bestMove = move;
                }
                alpha = Math.max(alpha, bestScore);
            } else {
                if (result.score < bestScore) {
                    bestScore = result.score;
                    bestMove = move;
                }
                beta = Math.min(beta, bestScore);
            }
            
            // Alpha-beta pruning
            if (beta <= alpha) {
                cutoffs++;
                break;
            }
        }
        
        // Store in transposition table
        if (useTranspositionTable && !searchCancelled) {
            int nodeType;
            if (bestScore <= originalAlpha) {
                nodeType = TranspositionEntry.UPPER_BOUND;
            } else if (bestScore >= beta) {
                nodeType = TranspositionEntry.LOWER_BOUND;
            } else {
                nodeType = TranspositionEntry.EXACT;
            }
            
            storeTransposition(boardHash, depth, bestScore, bestMove, nodeType);
        }
        
        return new SearchResult(bestMove, bestScore);
    }

    private int quiescenceSearch(Board board, int alpha, int beta, int depth) {
        nodesSearched++;
        
        int standPat = Evaluator.evaluate(board);
        
        if (depth == 0 || isTimeUp()) {
            return standPat;
        }
        
        if (standPat >= beta) {
            return beta;
        }
        
        if (standPat > alpha) {
            alpha = standPat;
        }
        
        // Only consider captures in quiescence search
        List<Move> captures = MoveGenerator.generateCaptures(board);
        moveOrderer.orderMoves(captures, board);
        
        for (Move capture : captures) {
            if (searchCancelled) break;
            
            board.makeMove(capture);
            int score = -quiescenceSearch(board, -beta, -alpha, depth - 1);
            board.undoMove();
            
            if (score >= beta) {
                return beta;
            }
            
            if (score > alpha) {
                alpha = score;
            }
        }
        
        return alpha;
    }

    private long getBoardHash(Board board) {
        // Simple hash based on FEN string - in a real engine you'd use Zobrist hashing
        return board.toFEN().hashCode();
    }

    private void storeTransposition(long hash, int depth, int score, Move bestMove, int nodeType) {
        if (transpositionTable.size() >= MAX_TT_SIZE) {
            // Simple replacement strategy - clear old entries
            transpositionTable.clear();
        }
        
        transpositionTable.put(hash, new TranspositionEntry(depth, score, bestMove, nodeType));
    }

    private boolean isTimeUp() {
        return System.currentTimeMillis() - searchStartTime >= maxTimeMs;
    }

    private void resetSearchStats() {
        nodesSearched = 0;
        transpositionHits = 0;
        cutoffs = 0;
    }

    private void printSearchStats() {
        long elapsedTime = System.currentTimeMillis() - searchStartTime;
        long nps = elapsedTime > 0 ? (nodesSearched * 1000) / elapsedTime : 0;
        
        System.out.printf("Search completed: %d nodes, %d TT hits, %d cutoffs, %d ms, %d nps%n",
            nodesSearched, transpositionHits, cutoffs, elapsedTime, nps);
    }

    // Getters and setters
    public void setMaxDepth(int maxDepth) {
        this.maxDepth = Math.max(1, Math.min(maxDepth, 20));
    }

    public void setMaxTimeMs(long maxTimeMs) {
        this.maxTimeMs = Math.max(100, maxTimeMs);
    }

    public void setUseTranspositionTable(boolean useTranspositionTable) {
        this.useTranspositionTable = useTranspositionTable;
    }

    public void setUseIterativeDeepening(boolean useIterativeDeepening) {
        this.useIterativeDeepening = useIterativeDeepening;
    }

    public void setUseQuiescenceSearch(boolean useQuiescenceSearch) {
        this.useQuiescenceSearch = useQuiescenceSearch;
    }

    public void cancelSearch() {
        searchCancelled = true;
    }

    public void clearTranspositionTable() {
        transpositionTable.clear();
    }

    public long getNodesSearched() {
        return nodesSearched;
    }

    // Inner classes
    private static class SearchResult {
        final Move bestMove;
        final int score;

        SearchResult(Move bestMove, int score) {
            this.bestMove = bestMove;
            this.score = score;
        }
    }

    private static class TranspositionEntry {
        static final int EXACT = 0;
        static final int LOWER_BOUND = 1;
        static final int UPPER_BOUND = 2;

        final int depth;
        final int score;
        final Move bestMove;
        final int nodeType;

        TranspositionEntry(int depth, int score, Move bestMove, int nodeType) {
            this.depth = depth;
            this.score = score;
            this.bestMove = bestMove;
            this.nodeType = nodeType;
        }
    }
}
