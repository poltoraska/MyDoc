package com.poltorashka.documents

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.cos
import kotlin.math.sin

class ScallopShape(
    private val petals: Int = 9,
    private val depth: Float = 0.1f
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        val width = size.width
        val height = size.height
        val radius = minOf(width, height) / 2f
        val centerX = width / 2f
        val centerY = height / 2f

        for (i in 0..360) {
            val angle = Math.toRadians(i.toDouble())
            val r = radius * (1f - depth + depth * cos(angle * petals)).toFloat()
            val x = centerX + r * cos(angle).toFloat()
            val y = centerY + r * sin(angle).toFloat()

            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}