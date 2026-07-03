package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppRepository
import com.example.data.Movie
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class GameStatus {
    object Playing : GameStatus()
    object Won : GameStatus()
    object Lost : GameStatus()
}

data class GameUiState(
    val currentMovie: Movie? = null,
    val guessedLetters: Set<Char> = emptySet(),
    val lives: Int = 8,
    val status: GameStatus = GameStatus.Playing,
    val isLoadingNewLevel: Boolean = false,
    val revealedCharacterHint: Boolean = false,
    val revealedSceneHint: Boolean = false,
    val error: String? = null,
    val snackbarMessage: String? = null
)

class GameViewModel(
    private val repository: AppRepository
) : ViewModel() {

    val userState = repository.userState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    private val _uiState = MutableStateFlow(GameUiState(isLoadingNewLevel = true))
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.initializePrepopulatedData()
            loadCurrentLevel()
        }
    }

    private suspend fun loadCurrentLevel() {
        _uiState.update { it.copy(isLoadingNewLevel = true, error = null) }
        val state = repository.userState.firstOrNull()
        val index = state?.currentLevelIndex ?: 0
        
        val allMovies = repository.allMovies.firstOrNull() ?: emptyList()
        if (allMovies.isEmpty()) {
            _uiState.update { it.copy(isLoadingNewLevel = false, error = "No movies available.") }
            return
        }

        // Loop back to the beginning if we run out of movies
        val safeIndex = index % allMovies.size
        val movie = allMovies[safeIndex]

        _uiState.update { 
            GameUiState(
                currentMovie = movie,
                guessedLetters = generateInitialPunctuation(movie.title),
                lives = 8,
                status = GameStatus.Playing,
                isLoadingNewLevel = false
            ) 
        }
    }

    private fun generateInitialPunctuation(title: String): Set<Char> {
        return title.uppercase().filter { it !in 'A'..'Z' }.toSet()
    }

    fun guessLetter(letter: Char) {
        val currentState = _uiState.value
        if (currentState.status != GameStatus.Playing || currentState.currentMovie == null) return
        
        val upperLetter = letter.uppercaseChar()
        if (currentState.guessedLetters.contains(upperLetter)) return

        val newGuessed = currentState.guessedLetters + upperLetter
        val titleUpper = currentState.currentMovie.title.uppercase()
        val isCorrect = titleUpper.contains(upperLetter)
        
        val newLives = if (isCorrect) currentState.lives else currentState.lives - 1
        
        // Check win condition
        val hasWon = titleUpper.all { it in newGuessed || it.isWhitespace() }
        
        val newStatus = when {
            hasWon -> GameStatus.Won
            newLives <= 0 -> GameStatus.Lost
            else -> GameStatus.Playing
        }

        _uiState.update { 
            it.copy(
                guessedLetters = newGuessed,
                lives = newLives,
                status = newStatus
            )
        }

        if (newStatus == GameStatus.Won) {
            viewModelScope.launch {
                repository.addCoins(20) // Reward for winning
            }
        }
    }

    fun useLetterHint() {
        val currentState = _uiState.value
        if (currentState.status != GameStatus.Playing || currentState.currentMovie == null) return

        viewModelScope.launch {
            val isFree = repository.userState.firstOrNull()?.hasPurchasedAdFree == true
            if (isFree || repository.spendCoins(10)) {
                // Re-read state fresh here - a manual guess could have landed while
                // spendCoins was suspended above, so the pre-suspend snapshot may be stale.
                val freshState = _uiState.value
                val movie = freshState.currentMovie
                if (movie == null || freshState.status != GameStatus.Playing) return@launch
                val title = movie.title.uppercase()
                val unrevealed = title.filter { it !in freshState.guessedLetters && !it.isWhitespace() }
                if (unrevealed.isNotEmpty()) {
                    val randomLetter = unrevealed.random()
                    guessLetter(randomLetter)
                }
            } else {
                _uiState.update { it.copy(snackbarMessage = "Not enough coins!") }
            }
        }
    }

    fun revealClue(type: String) {
        val currentState = _uiState.value
        if (currentState.status != GameStatus.Playing || currentState.currentMovie == null) return

        viewModelScope.launch {
            val isFree = repository.userState.firstOrNull()?.hasPurchasedAdFree == true
            if (isFree || repository.spendCoins(15)) {
                _uiState.update { 
                    when (type) {
                        "character" -> it.copy(revealedCharacterHint = true)
                        "scene" -> it.copy(revealedSceneHint = true)
                        else -> it
                    }
                }
            } else {
                _uiState.update { it.copy(snackbarMessage = "Not enough coins!") }
            }
        }
    }

    fun consumeSnackbarMessage() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    private var isTransitioningLevel = false

    fun nextLevel() {
        val status = _uiState.value.status
        if (isTransitioningLevel || (status != GameStatus.Won && status != GameStatus.Lost)) return
        isTransitioningLevel = true
        viewModelScope.launch {
            try {
                repository.advanceLevel()
                loadCurrentLevel()
            } finally {
                isTransitioningLevel = false
            }
        }
    }
    
    fun retryLevel() {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState.currentMovie != null) {
                _uiState.update { 
                    GameUiState(
                        currentMovie = currentState.currentMovie,
                        guessedLetters = generateInitialPunctuation(currentState.currentMovie.title),
                        lives = 8,
                        status = GameStatus.Playing
                    )
                }
            } else {
                loadCurrentLevel()
            }
        }
    }
}
