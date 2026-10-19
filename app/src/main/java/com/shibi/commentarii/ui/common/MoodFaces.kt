package com.shibi.commentarii.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SentimentDissatisfied
import androidx.compose.material.icons.filled.SentimentNeutral
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.SentimentVeryDissatisfied
import androidx.compose.material.icons.filled.SentimentVerySatisfied
import androidx.compose.ui.graphics.vector.ImageVector
import com.shibi.commentarii.R

/** Icon and label for a mood 1 (very sad) .. 5 (very happy), the same faces as the daily MoodCard. */
fun moodFace(mood: Int): Pair<ImageVector, Int>? = when (mood) {
    5 -> Icons.Filled.SentimentVerySatisfied to R.string.mood_5
    4 -> Icons.Filled.SentimentSatisfied to R.string.mood_4
    3 -> Icons.Filled.SentimentNeutral to R.string.mood_3
    2 -> Icons.Filled.SentimentDissatisfied to R.string.mood_2
    1 -> Icons.Filled.SentimentVeryDissatisfied to R.string.mood_1
    else -> null
}
