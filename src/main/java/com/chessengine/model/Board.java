package com.chessengine.model;

import java.util.*;

/**
 * Represents the chess board state with efficient move generation and game logic.
 */
public class Board {
    private static final String STARTING_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    
    // Board representation - 8x8 array
    private final Piece[] squares = new Piece[64];
    
    // Game state
    private PieceColor sideToMove = PieceColor.WHITE;
    private int castlingRights = 0; // KQkq bits
    private Square enPassantSquare = new Square(Square.INVALID);
    private int halfMoveClock = 0;
    private int fullMoveNumber = 1;
    
    // Castling rights constants
    public static final int WHITE_KING_SIDE = 1;
    public static final int WHITE_QUEEN_SIDE = 2;
    public static final int BLACK_KING_SIDE = 4;
    public static final int BLACK_QUEEN_SIDE = 8;
    
    // King positions for efficient check detection
    private Square whiteKingSquare = new Square("e1");
    private Square blackKingSquare = new Square("e8");
    
    // Move history for undo functionality
    private final Stack<BoardState> history = new Stack<>();

    public Board() {
        initializeFromFEN(STARTING_FEN);
    }

    public Board(String fen) {
        initializeFromFEN(fen);
    }

    public void initializeFromFEN(String fen) {
        // Clear board
        Arrays.fill(squares, Piece.NONE);
        
        String[] parts = fen.split(" ");
        if (parts.length != 6) {
            throw new IllegalArgumentException("Invalid FEN string");
        }
        
        // Parse piece placement
        String[] ranks = parts[0].split("/");
        if (ranks.length != 8) {
            throw new IllegalArgumentException("Invalid FEN piece placement");
        }
        
        for (int rank = 7; rank >= 0; rank--) {
            int file = 0;
            for (char c : ranks[7 - rank].toCharArray()) {
                if (Character.isDigit(c)) {
                    file += Character.getNumericValue(c);
                } else {
                    Piece piece = pieceFromChar(c);
                    squares[rank * 8 + file] = piece;
                    
                    // Track king positions
                    if (piece == Piece.WHITE_KING) {
                        whiteKingSquare = new Square(file, rank);
                    } else if (piece == Piece.BLACK_KING) {
                        blackKingSquare = new Square(file, rank);
                    }
                    
                    file++;
                }
            }
        }
        
        // Parse side to move
        sideToMove = parts[1].equals("w") ? PieceColor.WHITE : PieceColor.BLACK;
        
        // Parse castling rights
        castlingRights = 0;
        for (char c : parts[2].toCharArray()) {
            switch (c) {
                case 'K' -> castlingRights |= WHITE_KING_SIDE;
                case 'Q' -> castlingRights |= WHITE_QUEEN_SIDE;
                case 'k' -> castlingRights |= BLACK_KING_SIDE;
                case 'q' -> castlingRights |= BLACK_QUEEN_SIDE;
            }
        }
        
        // Parse en passant square
        if (!parts[3].equals("-")) {
            enPassantSquare = new Square(parts[3]);
        } else {
            enPassantSquare = new Square(Square.INVALID);
        }
        
        // Parse halfmove clock and fullmove number
        halfMoveClock = Integer.parseInt(parts[4]);
        fullMoveNumber = Integer.parseInt(parts[5]);
    }

    private Piece pieceFromChar(char c) {
        return switch (c) {
            case 'P' -> Piece.WHITE_PAWN;
            case 'N' -> Piece.WHITE_KNIGHT;
            case 'B' -> Piece.WHITE_BISHOP;
            case 'R' -> Piece.WHITE_ROOK;
            case 'Q' -> Piece.WHITE_QUEEN;
            case 'K' -> Piece.WHITE_KING;
            case 'p' -> Piece.BLACK_PAWN;
            case 'n' -> Piece.BLACK_KNIGHT;
            case 'b' -> Piece.BLACK_BISHOP;
            case 'r' -> Piece.BLACK_ROOK;
            case 'q' -> Piece.BLACK_QUEEN;
            case 'k' -> Piece.BLACK_KING;
            default -> Piece.NONE;
        };
    }

    public Piece getPiece(Square square) {
        if (!square.isValid()) return Piece.NONE;
        return squares[square.getIndex()];
    }

    public void setPiece(Square square, Piece piece) {
        if (!square.isValid()) return;
        squares[square.getIndex()] = piece;
        
        // Update king positions
        if (piece == Piece.WHITE_KING) {
            whiteKingSquare = square;
        } else if (piece == Piece.BLACK_KING) {
            blackKingSquare = square;
        }
    }

    public PieceColor getSideToMove() {
        return sideToMove;
    }

    public void setSideToMove(PieceColor color) {
        this.sideToMove = color;
    }

    public Square getKingSquare(PieceColor color) {
        return color == PieceColor.WHITE ? whiteKingSquare : blackKingSquare;
    }

    public Square getEnPassantSquare() {
        return enPassantSquare;
    }

    public int getCastlingRights() {
        return castlingRights;
    }

    public int getHalfMoveClock() {
        return halfMoveClock;
    }

    public int getFullMoveNumber() {
        return fullMoveNumber;
    }

    public boolean isSquareAttacked(Square square, PieceColor attackingColor) {
        if (!square.isValid()) return false;
        
        // Check pawn attacks
        int pawnDirection = attackingColor == PieceColor.WHITE ? 1 : -1;
        Square leftPawnSquare = square.offset(-1, -pawnDirection);
        Square rightPawnSquare = square.offset(1, -pawnDirection);
        
        Piece attackingPawn = attackingColor == PieceColor.WHITE ? Piece.WHITE_PAWN : Piece.BLACK_PAWN;
        
        if (leftPawnSquare.isValid() && getPiece(leftPawnSquare) == attackingPawn) return true;
        if (rightPawnSquare.isValid() && getPiece(rightPawnSquare) == attackingPawn) return true;
        
        // Check knight attacks
        int[][] knightMoves = {{-2, -1}, {-2, 1}, {-1, -2}, {-1, 2}, {1, -2}, {1, 2}, {2, -1}, {2, 1}};
        Piece attackingKnight = attackingColor == PieceColor.WHITE ? Piece.WHITE_KNIGHT : Piece.BLACK_KNIGHT;
        
        for (int[] move : knightMoves) {
            Square knightSquare = square.offset(move[0], move[1]);
            if (knightSquare.isValid() && getPiece(knightSquare) == attackingKnight) {
                return true;
            }
        }
        
        // Check sliding piece attacks (bishop, rook, queen)
        return isAttackedBySliding(square, attackingColor);
    }

    private boolean isAttackedBySliding(Square square, PieceColor attackingColor) {
        // Diagonal attacks (bishop, queen)
        int[][] diagonalDirections = {{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};
        for (int[] dir : diagonalDirections) {
            if (isAttackedInDirection(square, dir[0], dir[1], attackingColor, true, false)) {
                return true;
            }
        }
        
        // Straight attacks (rook, queen)
        int[][] straightDirections = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] dir : straightDirections) {
            if (isAttackedInDirection(square, dir[0], dir[1], attackingColor, false, true)) {
                return true;
            }
        }
        
        // King attacks
        Piece attackingKing = attackingColor == PieceColor.WHITE ? Piece.WHITE_KING : Piece.BLACK_KING;
        for (int fileOffset = -1; fileOffset <= 1; fileOffset++) {
            for (int rankOffset = -1; rankOffset <= 1; rankOffset++) {
                if (fileOffset == 0 && rankOffset == 0) continue;
                Square kingSquare = square.offset(fileOffset, rankOffset);
                if (kingSquare.isValid() && getPiece(kingSquare) == attackingKing) {
                    return true;
                }
            }
        }
        
        return false;
    }

    private boolean isAttackedInDirection(Square square, int fileDir, int rankDir, PieceColor attackingColor, boolean diagonal, boolean straight) {
        Square current = square.offset(fileDir, rankDir);
        
        while (current.isValid()) {
            Piece piece = getPiece(current);
            if (!piece.isEmpty()) {
                if (piece.getColor() == attackingColor) {
                    PieceType type = piece.getType();
                    if (type == PieceType.QUEEN) return true;
                    if (diagonal && type == PieceType.BISHOP) return true;
                    if (straight && type == PieceType.ROOK) return true;
                }
                break; // Piece blocks further attacks
            }
            current = current.offset(fileDir, rankDir);
        }
        
        return false;
    }

    public boolean isInCheck(PieceColor color) {
        Square kingSquare = getKingSquare(color);
        return isSquareAttacked(kingSquare, color.opposite());
    }

    public boolean makeMove(Move move) {
        if (move.isNull() || !isLegalMove(move)) {
            return false;
        }
        
        // Save current state for undo
        history.push(new BoardState(this));
        
        // Execute the move
        executeMoveUnchecked(move);
        
        return true;
    }

    private void executeMoveUnchecked(Move move) {
        Square from = move.getFrom();
        Square to = move.getTo();
        Piece movingPiece = getPiece(from);
        
        // Handle special moves
        if (move.isCastle()) {
            executeCastle(move);
        } else if (move.isEnPassant()) {
            executeEnPassant(move);
        } else {
            // Regular move
            setPiece(from, Piece.NONE);
            setPiece(to, move.isPromotion() ? 
                Piece.createPiece(move.getPromotionPiece(), movingPiece.getColor()) : movingPiece);
        }
        
        // Update game state
        updateGameState(move);
    }

    private void executeCastle(Move move) {
        Square from = move.getFrom();
        Square to = move.getTo();
        PieceColor color = move.getMovingPiece().getColor();
        
        // Move king
        setPiece(from, Piece.NONE);
        setPiece(to, move.getMovingPiece());
        
        // Move rook
        if (move.getFlags() == Move.KING_CASTLE) {
            Square rookFrom = new Square(7, from.getRank());
            Square rookTo = new Square(5, from.getRank());
            Piece rook = getPiece(rookFrom);
            setPiece(rookFrom, Piece.NONE);
            setPiece(rookTo, rook);
        } else { // Queen castle
            Square rookFrom = new Square(0, from.getRank());
            Square rookTo = new Square(3, from.getRank());
            Piece rook = getPiece(rookFrom);
            setPiece(rookFrom, Piece.NONE);
            setPiece(rookTo, rook);
        }
    }

    private void executeEnPassant(Move move) {
        Square from = move.getFrom();
        Square to = move.getTo();
        
        // Move pawn
        setPiece(from, Piece.NONE);
        setPiece(to, move.getMovingPiece());
        
        // Remove captured pawn
        Square capturedPawnSquare = new Square(to.getFile(), from.getRank());
        setPiece(capturedPawnSquare, Piece.NONE);
    }

    private void updateGameState(Move move) {
        // Update castling rights
        updateCastlingRights(move);
        
        // Update en passant square
        if (move.isDoublePawnPush()) {
            int targetRank = move.getFrom().getRank() + (sideToMove == PieceColor.WHITE ? 1 : -1);
            enPassantSquare = new Square(move.getFrom().getFile(), targetRank);
        } else {
            enPassantSquare = new Square(Square.INVALID);
        }
        
        // Update halfmove clock
        if (move.isCapture() || move.getMovingPiece().getType() == PieceType.PAWN) {
            halfMoveClock = 0;
        } else {
            halfMoveClock++;
        }
        
        // Update fullmove number
        if (sideToMove == PieceColor.BLACK) {
            fullMoveNumber++;
        }
        
        // Switch sides
        sideToMove = sideToMove.opposite();
    }

    private void updateCastlingRights(Move move) {
        Square from = move.getFrom();
        Square to = move.getTo();
        
        // King moves remove all castling rights for that color
        if (move.getMovingPiece().getType() == PieceType.KING) {
            if (move.getMovingPiece().isWhite()) {
                castlingRights &= ~(WHITE_KING_SIDE | WHITE_QUEEN_SIDE);
            } else {
                castlingRights &= ~(BLACK_KING_SIDE | BLACK_QUEEN_SIDE);
            }
        }
        
        // Rook moves or captures remove specific castling rights
        if (from.equals(new Square("a1")) || to.equals(new Square("a1"))) {
            castlingRights &= ~WHITE_QUEEN_SIDE;
        }
        if (from.equals(new Square("h1")) || to.equals(new Square("h1"))) {
            castlingRights &= ~WHITE_KING_SIDE;
        }
        if (from.equals(new Square("a8")) || to.equals(new Square("a8"))) {
            castlingRights &= ~BLACK_QUEEN_SIDE;
        }
        if (from.equals(new Square("h8")) || to.equals(new Square("h8"))) {
            castlingRights &= ~BLACK_KING_SIDE;
        }
    }

    public void undoMove() {
        if (history.isEmpty()) return;
        
        BoardState previousState = history.pop();
        restoreState(previousState);
    }

    // Null-move support for search (toggle side to move and clear en passant)
    public void makeNullMove() {
        history.push(new BoardState(this));
        // Clear en passant as per rules when a non-pawn move happens
        enPassantSquare = new Square(Square.INVALID);
        // Increase halfmove clock (no pawn move or capture occurred)
        halfMoveClock++;
        // Do not change fullMoveNumber for null moves
        sideToMove = sideToMove.opposite();
    }

    public void undoNullMove() {
        if (history.isEmpty()) return;
        BoardState previousState = history.pop();
        restoreState(previousState);
    }

    private void restoreState(BoardState state) {
        System.arraycopy(state.squares, 0, squares, 0, 64);
        sideToMove = state.sideToMove;
        castlingRights = state.castlingRights;
        enPassantSquare = state.enPassantSquare;
        halfMoveClock = state.halfMoveClock;
        fullMoveNumber = state.fullMoveNumber;
        whiteKingSquare = state.whiteKingSquare;
        blackKingSquare = state.blackKingSquare;
    }

    public boolean isLegalMove(Move move) {
        // Basic validation
        if (move.isNull() || !move.getFrom().isValid() || !move.getTo().isValid()) {
            return false;
        }
        
        Piece movingPiece = getPiece(move.getFrom());
        if (movingPiece.isEmpty() || movingPiece.getColor() != sideToMove) {
            return false;
        }
        
        // Check if move leaves own king in check using a state snapshot
        BoardState snapshot = new BoardState(this);
        executeMoveUnchecked(move);
        boolean inCheck = isInCheck(sideToMove.opposite());
        restoreState(snapshot);
        return !inCheck;
    }

    public String toFEN() {
        StringBuilder fen = new StringBuilder();
        
        // Piece placement
        for (int rank = 7; rank >= 0; rank--) {
            int emptyCount = 0;
            for (int file = 0; file < 8; file++) {
                Piece piece = squares[rank * 8 + file];
                if (piece.isEmpty()) {
                    emptyCount++;
                } else {
                    if (emptyCount > 0) {
                        fen.append(emptyCount);
                        emptyCount = 0;
                    }
                    fen.append(pieceToChar(piece));
                }
            }
            if (emptyCount > 0) {
                fen.append(emptyCount);
            }
            if (rank > 0) {
                fen.append('/');
            }
        }
        
        fen.append(' ');
        
        // Side to move
        fen.append(sideToMove == PieceColor.WHITE ? 'w' : 'b');
        fen.append(' ');
        
        // Castling rights
        if (castlingRights == 0) {
            fen.append('-');
        } else {
            if ((castlingRights & WHITE_KING_SIDE) != 0) fen.append('K');
            if ((castlingRights & WHITE_QUEEN_SIDE) != 0) fen.append('Q');
            if ((castlingRights & BLACK_KING_SIDE) != 0) fen.append('k');
            if ((castlingRights & BLACK_QUEEN_SIDE) != 0) fen.append('q');
        }
        fen.append(' ');
        
        // En passant square
        fen.append(enPassantSquare.isValid() ? enPassantSquare.toAlgebraic() : "-");
        fen.append(' ');
        
        // Halfmove clock and fullmove number
        fen.append(halfMoveClock).append(' ').append(fullMoveNumber);
        
        return fen.toString();
    }

    private char pieceToChar(Piece piece) {
        char c = switch (piece.getType()) {
            case PAWN -> 'p';
            case KNIGHT -> 'n';
            case BISHOP -> 'b';
            case ROOK -> 'r';
            case QUEEN -> 'q';
            case KING -> 'k';
            default -> ' ';
        };
        return piece.isWhite() ? Character.toUpperCase(c) : c;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("  a b c d e f g h\n");
        
        for (int rank = 7; rank >= 0; rank--) {
            sb.append(rank + 1).append(" ");
            for (int file = 0; file < 8; file++) {
                Piece piece = squares[rank * 8 + file];
                sb.append(piece.toString()).append(" ");
            }
            sb.append(rank + 1).append("\n");
        }
        
        sb.append("  a b c d e f g h\n");
        sb.append("FEN: ").append(toFEN()).append("\n");
        
        return sb.toString();
    }

    // Inner class to store board state for undo functionality
    private static class BoardState {
        final Piece[] squares = new Piece[64];
        final PieceColor sideToMove;
        final int castlingRights;
        final Square enPassantSquare;
        final int halfMoveClock;
        final int fullMoveNumber;
        final Square whiteKingSquare;
        final Square blackKingSquare;

        BoardState(Board board) {
            System.arraycopy(board.squares, 0, squares, 0, 64);
            sideToMove = board.sideToMove;
            castlingRights = board.castlingRights;
            enPassantSquare = board.enPassantSquare;
            halfMoveClock = board.halfMoveClock;
            fullMoveNumber = board.fullMoveNumber;
            whiteKingSquare = board.whiteKingSquare;
            blackKingSquare = board.blackKingSquare;
        }
    }
}
