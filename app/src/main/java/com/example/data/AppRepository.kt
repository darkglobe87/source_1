package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

class AppRepository(
    private val appDao: AppDao,
    private val context: Context
) {
    // Guards all UserState read-modify-write sequences below. Without this, two
    // near-simultaneous calls (e.g. a fast double-tap on a hint button) can both
    // read the same coin balance before either writes back, letting one purchase
    // succeed twice for the price of one.
    private val userStateMutex = Mutex()

    val allMovies: Flow<List<Movie>> = appDao.getAllMovies()
    val userState: Flow<UserState?> = appDao.getUserStateFlow()

    suspend fun initializePrepopulatedData() {
        val existingMovies = appDao.getAllMovies().firstOrNull()
        if (existingMovies.isNullOrEmpty()) {
            val initialMovies = try {
                val jsonString = context.assets.open("movies.json").bufferedReader().use { it.readText() }
                Json.decodeFromString<List<Movie>>(jsonString)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
            if (initialMovies.isNotEmpty()) {
                appDao.insertMovies(initialMovies)
            }
        }
        
        val state = appDao.getUserState()
        if (state == null) {
            appDao.insertUserState(UserState(coins = 50, currentLevelIndex = 0))
        }
    }

    suspend fun insertMovie(movie: Movie): Long {
        return appDao.insertMovie(movie)
    }
    
    suspend fun spendCoins(amount: Int): Boolean = userStateMutex.withLock {
        val state = appDao.getUserState() ?: return@withLock false
        if (state.coins >= amount) {
            appDao.updateUserState(state.copy(coins = state.coins - amount))
            true
        } else {
            false
        }
    }

    suspend fun addCoins(amount: Int) = userStateMutex.withLock {
        val state = appDao.getUserState()
        if (state != null) {
            appDao.updateUserState(state.copy(coins = state.coins + amount))
        }
    }

    suspend fun advanceLevel() = userStateMutex.withLock {
        val state = appDao.getUserState()
        if (state != null) {
            appDao.updateUserState(state.copy(currentLevelIndex = state.currentLevelIndex + 1))
        }
    }
    
    suspend fun purchaseAdFree() = userStateMutex.withLock {
        val state = appDao.getUserState()
        if (state != null) {
            appDao.updateUserState(state.copy(hasPurchasedAdFree = true))
        }
    }

    /** Call when a level is won: bumps the streak, tracks the best streak, and the lifetime total. */
    suspend fun recordWin() = userStateMutex.withLock {
        val state = appDao.getUserState()
        if (state != null) {
            val newStreak = state.currentStreak + 1
            appDao.updateUserState(
                state.copy(
                    currentStreak = newStreak,
                    bestStreak = maxOf(state.bestStreak, newStreak),
                    totalMoviesGuessed = state.totalMoviesGuessed + 1
                )
            )
        }
    }

    /** Call when a level is lost: resets the current streak, but leaves the best streak alone. */
    suspend fun recordLoss() = userStateMutex.withLock {
        val state = appDao.getUserState()
        if (state != null) {
            appDao.updateUserState(state.copy(currentStreak = 0))
        }
    }
}
