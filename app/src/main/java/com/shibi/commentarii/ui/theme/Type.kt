package com.shibi.commentarii.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.shibi.commentarii.R

/** Script face, used only for titles and display text. Body text keeps the default sans. */
val ScriptFont = FontFamily(Font(R.font.dancing_script))

private val Base = Typography()

val AppTypography = Typography(
    displayLarge = Base.displayLarge.copy(fontFamily = ScriptFont),
    displayMedium = Base.displayMedium.copy(fontFamily = ScriptFont),
    displaySmall = Base.displaySmall.copy(fontFamily = ScriptFont),
    headlineLarge = Base.headlineLarge.copy(fontFamily = ScriptFont),
    headlineMedium = Base.headlineMedium.copy(fontFamily = ScriptFont),
    headlineSmall = Base.headlineSmall.copy(fontFamily = ScriptFont),
    titleLarge = Base.titleLarge.copy(fontFamily = ScriptFont),
)
