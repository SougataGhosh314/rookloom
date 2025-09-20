const apiBase = window.location.pathname.replace(/\/[^/]*$/, '');

let gameId = null;
let state = null;
let selectedSquare = null; // e.g., 'e2'
let legalMoves = new Set(); // set of UCI strings
let engineMoving = false; // prevent re-entrancy
let prevFen = null; // previous board state for animation
let lastFromSq = null;
let lastToSq = null;

const boardEl = document.getElementById('board');
const gameIdEl = document.getElementById('gameId');
const sideToMoveEl = document.getElementById('sideToMove');
const inCheckEl = document.getElementById('inCheck');
const statusEl = document.getElementById('status');
const fenEl = document.getElementById('fen');
const movesEl = document.getElementById('moves');

const newGameBtn = document.getElementById('newGameBtn');
const undoBtn = document.getElementById('undoBtn');
const engineMoveBtn = document.getElementById('engineMoveBtn');
const depthInput = document.getElementById('depthInput');
const timeInput = document.getElementById('timeInput');
const autoEngineChk = document.getElementById('autoEngineChk');
const humanColorSel = document.getElementById('humanColorSel');

function isEngineTurn() {
  if (!state) return false;
  const humanColor = humanColorSel.value || 'WHITE';
  return state.sideToMove !== humanColor;
}

function api(path, options = {}) {
  return fetch(`${apiBase}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  }).then(r => {
    if (!r.ok) throw new Error(`HTTP ${r.status}`);
    return r.json();
  });
}

async function newGame() {
  const res = await api('/game/new', { method: 'POST', body: JSON.stringify({}) });
  gameId = res.gameId;
  await refresh();
}

async function refresh() {
  if (!gameId) return;
  state = await api(`/game/${gameId}`);
  updateUI();
  if (prevFen && prevFen.split(' ')[0] !== state.fen.split(' ')[0]) {
    // Try animating from prevFen to state.fen
    animateBetween(prevFen, state.fen, () => {
      renderBoardFromFEN(state.fen);
      prevFen = state.fen;
      maybeAutoEngineMove();
    });
    return;
  }
  renderBoardFromFEN(state.fen);
  prevFen = state.fen;
  maybeAutoEngineMove();
}

async function undo() {
  if (!gameId) return;
  await api(`/game/${gameId}/undo`, { method: 'POST', body: JSON.stringify({}) });
  await refresh();
}

async function engineMove() {
  if (!gameId) return;
  if (engineMoving) return;
  engineMoving = true;
  const depth = parseInt(depthInput.value, 10);
  const timeMs = parseInt(timeInput.value, 10);
  try {
    await api(`/game/${gameId}/engine-move`, { method: 'POST', body: JSON.stringify({ depth, timeMs }) });
    await refresh();
  } finally {
    engineMoving = false;
  }
}

function updateUI() {
  gameIdEl.textContent = gameId || '-';
  sideToMoveEl.textContent = state.sideToMove;
  inCheckEl.textContent = state.inCheck ? 'Yes' : 'No';
  if (state.checkmate) statusEl.textContent = 'Checkmate';
  else if (state.stalemate) statusEl.textContent = 'Stalemate';
  else statusEl.textContent = 'Ongoing';
  fenEl.textContent = state.fen;
  movesEl.textContent = state.legalMoves.join(' ');
  legalMoves = new Set(state.legalMoves);
}

function maybeAutoEngineMove() {
  if (!autoEngineChk.checked) return;
  if (state && !state.checkmate && !state.stalemate && isEngineTurn()) {
    // Defer slightly to allow UI to update
    setTimeout(() => engineMove(), 50);
  }
}

function renderBoardFromFEN(fen) {
  boardEl.innerHTML = '';
  selectedSquare = null;

  const board = fen.split(' ')[0];
  const ranks = board.split('/');
  for (let r = 7; r >= 0; r--) {
    let file = 0;
    const row = ranks[7 - r];
    for (const ch of row) {
      if (/[1-8]/.test(ch)) {
        const empty = parseInt(ch, 10);
        for (let i = 0; i < empty; i++) {
          const sq = idxToAlg(file, r);
          addSquare(sq, null);
          file++;
        }
      } else {
        const sq = idxToAlg(file, r);
        addSquare(sq, ch);
        file++;
      }
    }
  }
}

function idxToAlg(f, r) {
  return String.fromCharCode('a'.charCodeAt(0) + f) + (r + 1);
}

function addSquare(algebraic, pieceChar) {
  const file = algebraic.charCodeAt(0) - 'a'.charCodeAt(0);
  const rank = parseInt(algebraic[1], 10) - 1;
  const light = (file + rank) % 2 === 0;

  const cell = document.createElement('div');
  cell.className = `square ${light ? 'light' : 'dark'}`;
  cell.dataset.square = algebraic;
  if (algebraic === lastFromSq) cell.classList.add('last-from');
  if (algebraic === lastToSq) cell.classList.add('last-to');
  if (pieceChar) {
    const piece = document.createElement('div');
    const isWhite = pieceChar === pieceChar.toUpperCase();
    piece.className = `piece ${isWhite ? 'white-piece' : 'black-piece'}`;
    piece.textContent = pieceSymbol(pieceChar);
    cell.appendChild(piece);
  }

  cell.addEventListener('click', () => onSquareClick(algebraic));
  boardEl.appendChild(cell);
}

function pieceSymbol(ch) {
  // Use simple text symbols; you can replace with images later
  const map = {
    'P': '♙', 'N': '♘', 'B': '♗', 'R': '♖', 'Q': '♕', 'K': '♔',
    'p': '♟', 'n': '♞', 'b': '♝', 'r': '♜', 'q': '♛', 'k': '♚'
  };
  return map[ch] || '';
}

function highlightSquares(sqs, on) {
  const set = new Set(sqs);
  document.querySelectorAll('.square').forEach(el => {
    const sq = el.dataset.square;
    if (set.has(sq)) el.classList.toggle('highlight', on);
  });
}

function onSquareClick(sq) {
  // Don't allow human to move if it's engine's turn
  if (isEngineTurn()) return;
  if (!selectedSquare) {
    // Select if there is at least one legal move from here
    const movesFrom = state.legalMoves.filter(m => m.startsWith(sq));
    if (movesFrom.length > 0) {
      selectedSquare = sq;
      highlightSquares(movesFrom.map(m => m.substring(2, 4)), true);
    }
  } else {
    if (sq === selectedSquare) {
      // Deselect
      highlightSquares(state.legalMoves.filter(m => m.startsWith(sq)).map(m => m.substring(2, 4)), false);
      selectedSquare = null;
      return;
    }
    // Try to move selectedSquare->sq; find if promotion is needed
    let uci = selectedSquare + sq;
    const promos = ['q','r','b','n'];
    let okMove = legalMoves.has(uci);
    if (!okMove) {
      // maybe promotion
      for (const p of promos) {
        if (legalMoves.has(uci + p)) { uci = uci + p; okMove = true; break; }
      }
    }
    // Clear highlight
    highlightSquares(state.legalMoves.filter(m => m.startsWith(selectedSquare)).map(m => m.substring(2, 4)), false);
    selectedSquare = null;
    if (okMove) {
      makePlayerMove(uci);
    }
  }
}

async function makePlayerMove(uci) {
  if (isEngineTurn()) return;
  lastFromSq = uci.substring(0,2);
  lastToSq = uci.substring(2,4);
  await api(`/game/${gameId}/move`, { method: 'POST', body: JSON.stringify({ uci }) });
  await refresh();
}

// Init
newGameBtn.addEventListener('click', newGame);
undoBtn.addEventListener('click', undo);
engineMoveBtn.addEventListener('click', engineMove);
autoEngineChk.addEventListener('change', () => maybeAutoEngineMove());
humanColorSel.addEventListener('change', () => maybeAutoEngineMove());

// Start a game on load
newGame().catch(err => console.error(err));

// --- Animation helpers ---
function parseFenBoard(fen) {
  const board = fen.split(' ')[0];
  const ranks = board.split('/');
  const grid = Array.from({ length: 8 }, () => Array(8).fill(null));
  for (let r = 7; r >= 0; r--) {
    let file = 0;
    const row = ranks[7 - r];
    for (const ch of row) {
      if (/[1-8]/.test(ch)) {
        file += parseInt(ch, 10);
      } else {
        grid[r][file] = ch;
        file++;
      }
    }
  }
  return grid;
}

function findDiff(prevGrid, nextGrid) {
  // Try to detect a single move (from,to,piece). Covers normal moves, captures, simple castles, promotions.
  const removed = [];
  const added = [];
  for (let r = 0; r < 8; r++) {
    for (let f = 0; f < 8; f++) {
      const a = prevGrid[r][f];
      const b = nextGrid[r][f];
      if (a !== b) {
        if (a && !b) removed.push({ f, r, ch: a });
        if (!a && b) added.push({ f, r, ch: b });
        if (a && b && a !== b) { // capture or promotion destination
          added.push({ f, r, ch: b });
        }
      }
    }
  }
  // Prefer king move in castling by checking for king displacement of 2 files
  // Otherwise, match by color preference
  const match = (rem, add) => {
    // Try exact piece char match first
    let cand = add.find(x => x.ch === rem.ch);
    if (cand) return cand;
    // Promotion: pawn to new piece; allow different char but same color
    const isWhite = rem.ch === rem.ch.toUpperCase();
    cand = add.find(x => (x.ch === x.ch.toUpperCase()) === isWhite);
    return cand || null;
  };
  // Handle simple one-move case
  if (removed.length) {
    const rem = removed[0];
    const add = match(rem, added) || added[0];
    if (add) return {
      from: idxToAlg(rem.f, rem.r),
      to: idxToAlg(add.f, add.r),
      ch: add.ch || rem.ch
    };
  }
  return null;
}

function animateBetween(prevFEN, nextFEN, onDone) {
  const prevGrid = parseFenBoard(prevFEN);
  const nextGrid = parseFenBoard(nextFEN);
  const diff = findDiff(prevGrid, nextGrid);
  if (!diff) { onDone(); return; }

  lastFromSq = diff.from;
  lastToSq = diff.to;

  // Render previous state first
  renderBoardFromFEN(prevFEN);

  // Create floating piece
  const isWhite = diff.ch === diff.ch.toUpperCase();
  const floating = document.createElement('div');
  floating.className = `piece ${isWhite ? 'white-piece' : 'black-piece'} floating`;
  floating.textContent = pieceSymbol(diff.ch);
  boardEl.appendChild(floating);

  const size = 64; // must match CSS square size
  const fromF = diff.from.charCodeAt(0) - 'a'.charCodeAt(0);
  const fromR = parseInt(diff.from[1], 10) - 1;
  const toF = diff.to.charCodeAt(0) - 'a'.charCodeAt(0);
  const toR = parseInt(diff.to[1], 10) - 1;

  const fromX = fromF * size;
  const fromY = (7 - fromR) * size; // invert because render loops 7..0
  const toX = toF * size;
  const toY = (7 - toR) * size;

  // Position and animate
  floating.style.transform = `translate(${fromX}px, ${fromY}px)`;
  // Force layout before transitioning
  requestAnimationFrame(() => {
    floating.style.transition = 'transform 160ms ease-out';
    floating.style.transform = `translate(${toX}px, ${toY}px)`;
    setTimeout(() => {
      floating.remove();
      onDone();
    }, 170);
  });
}
