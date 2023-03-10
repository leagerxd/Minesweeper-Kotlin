package com.minesweeper.game.minesweeper
import utils.DoublyIndexedItem
import utils.choose
import utils.next
import java.util.*
import kotlin.properties.Delegates

typealias IndexedTile = DoublyIndexedItem<Tile>

open class Game(val height: Int, val width: Int, val amountOfMines: Int,
              private val winnableGame: Boolean = false,
              private var currentInputMode: InputMode = InputMode.FLAGGING) {
  open val board: List<List<IndexedTile>> = List(height) { i -> List(width) { j -> IndexedTile(i, j, Tile()) } }

  enum class EndState { WON, LOST, UNDECIDED }

  var endCallback: ((EndState) -> Unit)? = null
  var winState by Delegates.observable(EndState.UNDECIDED) { _, _, newValue ->
    endCallback?.invoke(newValue)
  }
  private var isFirstMove = true

  enum class InputMode { REVEALING, FLAGGING }

  protected fun initGame(startingTile: IndexedTile) {
    do {
            cleanMines()
            plantMines(startingTile)
            updateNumbers()
      } 
      while (winnableGame)
  }

  private fun updateNumbers() {
    board.flatten().forEach { tile -> tile.value.numberOfMinedNeighbors = tile.neighbors(board).count { it.value.isMine } }
  }

  fun swapMode() {
    currentInputMode = currentInputMode.next()
  }

  fun holdTile(i: Int, j: Int) {
        swapMode()
        clickTile(i, j)
        swapMode()
  }

  fun clickTile(i: Int, j: Int) {
    val currentTile = board[i][j]
    if (isFirstMove) {
      isFirstMove = false
      initGame(currentTile)
      revealTile(currentTile)
    }
    else if (!currentTile.value.isRevealed) {
    when (currentInputMode) {
      InputMode.REVEALING -> revealTile(currentTile)
      InputMode.FLAGGING -> flagTile(currentTile)
      }
    }
    else if (currentTile.isSafe()) {
      revealTileNeighbors(currentTile)
    }
  }

  private fun IndexedTile.isSafe(): Boolean {
    val expectedFlags = value.numberOfMinedNeighbors
    return neighbors(board).count { it.value.isFlagged } >= expectedFlags
  }

  private var minesLeft = amountOfMines
  open fun flagTile(hiddenTile: IndexedTile) {
    if (!hiddenTile.value.isRevealed) hiddenTile.toggleFlag()
    if (hiddenTile.value.isFlagged) minesLeft-- else minesLeft++ //flag added

  }

  open fun IndexedTile.reveal() {
    value.reveal()
    checkForWin()
  }

  protected open fun IndexedTile.toggleFlag() {
    value.toggleFlag()
    checkForWin()
  }

  private fun revealTile(dangerousTile: IndexedTile) {
    if (!dangerousTile.value.isRevealed && !dangerousTile.value.isFlagged) {
      if (dangerousTile.value.isMine) lose()
      dangerousTile.reveal()
    }
    if (dangerousTile.value.isEmpty()) {
      revealTileNeighbors(dangerousTile)
    }
    checkForWin()
  }

  protected fun checkForWin() {
    if (winConditionDone())
      win()
  }

  private fun win() {
    winState = EndState.WON
  }

  private fun winConditionDone(): Boolean {
    return ((board.flatten().count { !it.value.isRevealed } == amountOfMines) or board.flatten().filter { it.value.isMine }.all { it.value.isFlagged })
      && (winState != EndState.LOST)
  }

  private fun revealTileNeighbors(startingTile: IndexedTile) {
    val revealable = { it: IndexedTile -> !it.value.isRevealed && !it.value.isFlagged }
    val tileQueue: Queue<IndexedTile> = LinkedList()
    tileQueue.addAll(startingTile.neighbors(board).filter(revealable))
    while (tileQueue.isNotEmpty()) {
      val currentTile = tileQueue.poll()
      if (!currentTile.value.isFlagged) {
        currentTile.reveal()
        if (currentTile.value.isMine) lose()
      }
      if (currentTile.value.isEmpty()) {
        tileQueue.addAll(currentTile.neighbors(board).toSet().filter(revealable))
      }
    }
    checkForWin()
  }

  private fun lose() {
    winState = EndState.LOST
  }

  open fun plantMine(tile: IndexedTile) {
    tile.value.plantMine()
  }

  open fun plantMines(startingTile: IndexedTile) {
    val possibleMines = board.flatten().toSet() - (startingTile.neighbors(board) + startingTile).toSet()
    possibleMines.choose(amountOfMines).forEach(::plantMine)
    countMines()
  }

  private fun countMines() {
    for (tile in board.flatten()) {
      tile.value.numberOfMinedNeighbors = tile.neighbors(board).count { it.value.isMine }
    }
  }

  private fun cleanMines() {
    board.flatten().filter { it.value.isMine }.forEach { tile -> tile.value.removeMine() }
  }
}

class UnMutatingGame(height: Int, width: Int, amountOfMines: Int,
  winnableGame: Boolean = false,
  currentInputMode: InputMode = InputMode.FLAGGING) : Game(height, width, amountOfMines, winnableGame, currentInputMode) {
    override val board: MutableList<MutableList<IndexedTile>> = MutableList(height) { i -> MutableList(width) { j -> IndexedTile(i, j, Tile()) } }
    override fun flagTile(hiddenTile: IndexedTile) {
      hiddenTile.replaceValue { toggleFlag() }
      checkForWin()
  }

  override fun plantMine(tile: IndexedTile) {
    tile.replaceValue { plantMine() }
  }

  override fun IndexedTile.reveal() {
    replaceValue { reveal() }
    checkForWin()
  }

  private fun IndexedTile.replaceValue(transformation: Tile.() -> Unit) {
    board[i][j] = copy(value = value.apply(transformation))
  }
}


