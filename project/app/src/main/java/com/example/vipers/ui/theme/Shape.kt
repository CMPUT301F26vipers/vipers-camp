/*
 * Shape.kt
 * Corner sizes and heights for buttons, inputs and cards, taken from the UI mockups.
 * Known issues: none.
 */
package com.example.vipers.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Material shapes: small is used by text fields, medium by cards. */
val Shapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(14.dp),
)

/** Material buttons are fully rounded by default, so pass this shape to them. */
val ButtonShape = RoundedCornerShape(14.dp)
val ButtonHeight = 50.dp

val InputShape = RoundedCornerShape(12.dp)
val InputHeight = 46.dp
