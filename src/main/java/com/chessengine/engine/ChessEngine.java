package com.chessengine.engine;

import com.chessengine.model.*;
import java.util.*;

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
    
    // Transposition table (fixed-size, Zobrist)
    private final TranspositionTable tt = new TranspositionTable();
    
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
            SearchResult result = minimax(board, maxDepth, Integer.MIN_VALUE + 1, Integer.MAX_VALUE, true, true);
            bestMove = result.bestMove;
        }
        
        printSearchStats();
        return bestMove;
    }

    private Move iterativeDeepening(Board board) {
        Move bestMove = Move.NULL_MOVE;
        int bestScore = 0;
        int aspirationWindow = 50;
        
        for (int depth = 1; depth <= maxDepth; depth++) {
            if (isTimeUp()) break;
            int alpha = Math.max(Integer.MIN_VALUE + 1, bestScore - aspirationWindow);
            int beta = Math.min(Integer.MAX_VALUE, bestScore + aspirationWindow);
            SearchResult result = minimax(board, depth, alpha, beta, true, true);
            if (result.score <= alpha || result.score >= beta) {
                // Aspiration failed, re-search full window
                result = minimax(board, depth, Integer.MIN_VALUE + 1, Integer.MAX_VALUE, true, true);
            }
            
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

    private SearchResult minimax(Board board, int depth, int alpha, int beta, boolean maximizingPlayer, boolean allowNullMove) {
        nodesSearched++;
        
        // Check time limit
        if (isTimeUp()) {
            searchCancelled = true;
            return new SearchResult(Move.NULL_MOVE, 0);
        }
        
        long key = Zobrist.hash(board);
        // Probe TT
        if (useTranspositionTable) {
            TranspositionTable.Entry e = tt.probe(key);
            if (e != null && e.depth >= depth) {
                transpositionHits++;
                if (e.flag == TranspositionTable.EXACT) return new SearchResult(e.move != null ? e.move : Move.NULL_MOVE, e.score);
                if (e.flag == TranspositionTable.LOWER && e.score >= beta) return new SearchResult(e.move != null ? e.move : Move.NULL_MOVE, e.score);
                if (e.flag == TranspositionTable.UPPER && e.score <= alpha) return new SearchResult(e.move != null ? e.move : Move.NULL_MOVE, e.score);
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

        // Null-move pruning
        if (allowNullMove && depth >= 3 && !board.isInCheck(board.getSideToMove())) {
            board.makeNullMove();
            int R = 2; // reduction
            int score = -minimax(board, depth - 1 - R, -beta, -beta + 1, !maximizingPlayer, false).score;
            board.undoNullMove();
            if (score >= beta) {
                cutoffs++;
                return new SearchResult(Move.NULL_MOVE, beta);
            }
        }

        // Order moves for better alpha-beta pruning
        // TT move ordering: place TT best move first if available
        if (useTranspositionTable) {
            Move ttMove = tt.getBestMove(key);
            if (ttMove != null) {
                for (int i = 0; i < legalMoves.size(); i++) {
                    if (legalMoves.get(i).equals(ttMove)) {
                        Collections.swap(legalMoves, 0, i);
                        break;
                    }
                }
            }
        }
        moveOrderer.orderMoves(legalMoves, board);
        
        Move bestMove = Move.NULL_MOVE;
        int bestScore = maximizingPlayer ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        int originalAlpha = alpha;
        
        int moveIndex = 0;
        for (Move move : legalMoves) {
            if (searchCancelled) break;
            
            board.makeMove(move);
            // PVS + LMR
            int childDepth = depth - 1;
            boolean isCapture = move.isCapture();
            boolean doLMR = childDepth >= 3 && !isCapture && moveIndex > 3; // light LMR
            int score;
            if (moveIndex == 0) {
                // Full window for PV move
                score = -minimax(board, childDepth, -beta, -alpha, !maximizingPlayer, true).score;
            } else {
                int reducedDepth = doLMR ? childDepth - 1 : childDepth;
                score = -minimax(board, reducedDepth, -alpha - 1, -alpha, !maximizingPlayer, true).score;
                if (score > alpha) {
                    // Re-search at full window
                    score = -minimax(board, childDepth, -beta, -alpha, !maximizingPlayer, true).score;
                }
            }
            board.undoMove();
            
            if (maximizingPlayer) {
                if (score > bestScore) {
                    bestScore = score;
                    bestMove = move;
                }
                alpha = Math.max(alpha, bestScore);
            } else {
                if (score < bestScore) {
                    bestScore = score;
                    bestMove = move;
                }
                beta = Math.min(beta, bestScore);
            }
            
            // Alpha-beta pruning
            if (beta <= alpha) {
                cutoffs++;
                break;
            }
            moveIndex++;
        }
        
        // Store in transposition table
        if (useTranspositionTable && !searchCancelled) {
            int flag = TranspositionTable.EXACT;
            if (bestScore <= originalAlpha) flag = TranspositionTable.UPPER;
            else if (bestScore >= beta) flag = TranspositionTable.LOWER;
            tt.store(key, depth, bestScore, flag, bestMove);
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

    // Zobrist hashing now used directly via Zobrist.hash(board)

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

    public void clearTranspositionTable() { tt.clear(); }

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
}
