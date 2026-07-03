package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Query("SELECT * FROM movies ORDER BY id ASC")
    fun getAllMovies(): Flow<List<Movie>>
    
    @Query("SELECT * FROM movies WHERE id = :id LIMIT 1")
    suspend fun getMovieById(id: Int): Movie?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovies(movies: List<Movie>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovie(movie: Movie): Long

    @Query("SELECT * FROM user_state WHERE id = 1 LIMIT 1")
    fun getUserStateFlow(): Flow<UserState?>

    @Query("SELECT * FROM user_state WHERE id = 1 LIMIT 1")
    suspend fun getUserState(): UserState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserState(state: UserState)
    
    @Update
    suspend fun updateUserState(state: UserState)
}
