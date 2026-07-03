package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "movies")
data class Movie(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val badDescription: String,
    val characterHint: String,
    val plotHint: String,
    val sceneHint: String,
    val isAIGenerated: Boolean = false,
    val imageRes: String? = null
)

@Entity(tableName = "user_state")
data class UserState(
    @PrimaryKey val id: Int = 1,
    val coins: Int = 50,
    val currentLevelIndex: Int = 0,
    val hasPurchasedAdFree: Boolean = false
)
