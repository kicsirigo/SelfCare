package com.selfcare.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Centralized 24dp monochrome vector icons using uniform 2dp rounded strokes.
 * Material 3 Icon applies LocalContentColor dynamically (onSurface, onSurfaceVariant, primary).
 */
object AppIcons {

    private fun buildIcon(
        name: String,
        block: PathBuilder.() -> Unit
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = block
        )
    }.build()

    // Routine
    val Sun: ImageVector by lazy {
        buildIcon("Sun") {
            // Center circle r=4 at (12, 12)
            moveTo(16f, 12f)
            arcTo(4f, 4f, 0f, isMoreThanHalf = true, isPositiveArc = true, 8f, 12f)
            arcTo(4f, 4f, 0f, isMoreThanHalf = true, isPositiveArc = true, 16f, 12f)
            // Rays
            moveTo(12f, 2f); lineTo(12f, 4f)
            moveTo(12f, 20f); lineTo(12f, 22f)
            moveTo(4.93f, 4.93f); lineTo(6.34f, 6.34f)
            moveTo(17.66f, 17.66f); lineTo(19.07f, 19.07f)
            moveTo(2f, 12f); lineTo(4f, 12f)
            moveTo(20f, 12f); lineTo(22f, 12f)
            moveTo(4.93f, 19.07f); lineTo(6.34f, 17.66f)
            moveTo(17.66f, 6.34f); lineTo(19.07f, 4.93f)
        }
    }

    val Moon: ImageVector by lazy {
        buildIcon("Moon") {
            moveTo(20.5f, 14.5f)
            arcTo(8.5f, 8.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 9.5f, 3.5f)
            arcTo(6.5f, 6.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, 20.5f, 14.5f)
            close()
        }
    }

    val Check: ImageVector by lazy {
        buildIcon("Check") {
            moveTo(5f, 12.5f)
            lineTo(9.5f, 17f)
            lineTo(19f, 7.5f)
        }
    }

    val CheckCircle: ImageVector by lazy {
        buildIcon("CheckCircle") {
            moveTo(21f, 12f)
            arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = true, 3f, 12f)
            arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = true, 21f, 12f)
            moveTo(8.5f, 12.5f)
            lineTo(11f, 15f)
            lineTo(15.5f, 9.5f)
        }
    }

    // Hygiene
    val WaterDrop: ImageVector by lazy {
        buildIcon("WaterDrop") {
            moveTo(12f, 3f)
            curveTo(12f, 3f, 6f, 10.2f, 6f, 14.5f)
            arcTo(6f, 6f, 0f, isMoreThanHalf = false, isPositiveArc = false, 18f, 14.5f)
            curveTo(18f, 10.2f, 12f, 3f, 12f, 3f)
            close()
        }
    }

    val Sparkle: ImageVector by lazy {
        buildIcon("Sparkle") {
            moveTo(12f, 3f)
            lineTo(13.8f, 8.8f)
            lineTo(19.5f, 10.5f)
            lineTo(13.8f, 12.2f)
            lineTo(12f, 18f)
            lineTo(10.2f, 12.2f)
            lineTo(4.5f, 10.5f)
            lineTo(10.2f, 8.8f)
            close()
            moveTo(19f, 16f); lineTo(19f, 20f)
            moveTo(17f, 18f); lineTo(21f, 18f)
        }
    }

    val Habit: ImageVector by lazy {
        buildIcon("Habit") {
            moveTo(9f, 5f); lineTo(20f, 5f)
            moveTo(9f, 12f); lineTo(20f, 12f)
            moveTo(9f, 19f); lineTo(20f, 19f)
            moveTo(4f, 5f); lineTo(5.2f, 6.2f); lineTo(7f, 4f)
            moveTo(4f, 12f); lineTo(5.2f, 13.2f); lineTo(7f, 11f)
            moveTo(4f, 19f); lineTo(5.2f, 20.2f); lineTo(7f, 18f)
        }
    }

    // Shelf
    val Bottle: ImageVector by lazy {
        buildIcon("Bottle") {
            moveTo(9f, 3f); lineTo(15f, 3f)
            moveTo(10f, 3f); lineTo(10f, 6f)
            moveTo(14f, 3f); lineTo(14f, 6f)
            moveTo(8f, 8f); lineTo(16f, 8f)
            curveTo(17.1f, 8f, 18f, 8.9f, 18f, 10f)
            lineTo(18f, 19f)
            curveTo(18f, 20.1f, 17.1f, 21f, 16f, 21f)
            lineTo(8f, 21f)
            curveTo(6.9f, 21f, 6f, 20.1f, 6f, 19f)
            lineTo(6f, 10f)
            curveTo(6f, 8.9f, 6.9f, 8f, 8f, 8f)
            close()
            moveTo(10f, 6f); lineTo(8f, 8f)
            moveTo(14f, 6f); lineTo(16f, 8f)
        }
    }

    val Barcode: ImageVector by lazy {
        buildIcon("Barcode") {
            moveTo(3f, 7f); lineTo(3f, 5f); lineTo(6f, 5f)
            moveTo(18f, 5f); lineTo(21f, 5f); lineTo(21f, 7f)
            moveTo(3f, 17f); lineTo(3f, 19f); lineTo(6f, 19f)
            moveTo(18f, 19f); lineTo(21f, 19f); lineTo(21f, 17f)
            moveTo(7f, 8f); lineTo(7f, 16f)
            moveTo(10f, 8f); lineTo(10f, 16f)
            moveTo(13f, 8f); lineTo(13f, 16f)
            moveTo(17f, 8f); lineTo(17f, 16f)
        }
    }

    val Scan: ImageVector by lazy {
        buildIcon("Scan") {
            moveTo(4f, 8f); lineTo(4f, 5f); lineTo(8f, 5f)
            moveTo(16f, 5f); lineTo(20f, 5f); lineTo(20f, 8f)
            moveTo(4f, 16f); lineTo(4f, 19f); lineTo(8f, 19f)
            moveTo(16f, 19f); lineTo(20f, 19f); lineTo(20f, 16f)
            moveTo(4f, 12f); lineTo(20f, 12f)
        }
    }

    val Warning: ImageVector by lazy {
        buildIcon("Warning") {
            moveTo(12f, 3.5f)
            lineTo(21f, 19.5f)
            lineTo(3f, 19.5f)
            close()
            moveTo(12f, 9.5f); lineTo(12f, 14f)
            moveTo(12f, 16.8f); lineTo(12f, 17f)
        }
    }

    val Search: ImageVector by lazy {
        buildIcon("Search") {
            moveTo(18f, 11f)
            arcTo(7f, 7f, 0f, isMoreThanHalf = true, isPositiveArc = true, 4f, 11f)
            arcTo(7f, 7f, 0f, isMoreThanHalf = true, isPositiveArc = true, 18f, 11f)
            moveTo(16f, 16f); lineTo(20.5f, 20.5f)
        }
    }

    // Check-In
    val Face: ImageVector by lazy {
        buildIcon("Face") {
            moveTo(21f, 12f)
            arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = true, 3f, 12f)
            arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = true, 21f, 12f)
            moveTo(9f, 10f); lineTo(9f, 10.5f)
            moveTo(15f, 10f); lineTo(15f, 10.5f)
            moveTo(8.5f, 14.5f)
            curveTo(9.5f, 16.2f, 14.5f, 16.2f, 15.5f, 14.5f)
        }
    }

    val Haircut: ImageVector by lazy {
        buildIcon("Haircut") {
            // Scissors blades & rings
            moveTo(9f, 6f)
            arcTo(3f, 3f, 0f, isMoreThanHalf = true, isPositiveArc = true, 3f, 6f)
            arcTo(3f, 3f, 0f, isMoreThanHalf = true, isPositiveArc = true, 9f, 6f)
            moveTo(9f, 18f)
            arcTo(3f, 3f, 0f, isMoreThanHalf = true, isPositiveArc = true, 3f, 18f)
            arcTo(3f, 3f, 0f, isMoreThanHalf = true, isPositiveArc = true, 9f, 18f)
            moveTo(8.2f, 8.2f); lineTo(20f, 18f)
            moveTo(8.2f, 15.8f); lineTo(20f, 6f)
        }
    }

    val Camera: ImageVector by lazy {
        buildIcon("Camera") {
            moveTo(4f, 7f)
            lineTo(7.5f, 7f)
            lineTo(9.2f, 4.5f)
            lineTo(14.8f, 4.5f)
            lineTo(16.5f, 7f)
            lineTo(20f, 7f)
            curveTo(21.1f, 7f, 22f, 7.9f, 22f, 9f)
            lineTo(22f, 18f)
            curveTo(22f, 19.1f, 21.1f, 20f, 20f, 20f)
            lineTo(4f, 20f)
            curveTo(2.9f, 20f, 2f, 19.1f, 2f, 18f)
            lineTo(2f, 9f)
            curveTo(2f, 7.9f, 2.9f, 7f, 4f, 7f)
            close()
            moveTo(15.5f, 13.5f)
            arcTo(3.5f, 3.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 8.5f, 13.5f)
            arcTo(3.5f, 3.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 15.5f, 13.5f)
        }
    }

    // Trends
    val Chart: ImageVector by lazy {
        buildIcon("Chart") {
            moveTo(4f, 4f); lineTo(4f, 20f); lineTo(20f, 20f)
            moveTo(7f, 15f); lineTo(11f, 11f); lineTo(14.5f, 13.5f); lineTo(19f, 8f)
        }
    }

    val History: ImageVector by lazy {
        buildIcon("History") {
            moveTo(3.5f, 12f)
            arcTo(8.5f, 8.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 6f, 18f)
            moveTo(3.5f, 7f); lineTo(3.5f, 12f); lineTo(8f, 12f)
            moveTo(12f, 7.5f); lineTo(12f, 12f); lineTo(15.5f, 14f)
        }
    }

    val ArrowUp: ImageVector by lazy {
        buildIcon("ArrowUp") {
            moveTo(12f, 19f); lineTo(12f, 5f)
            moveTo(6f, 11f); lineTo(12f, 5f); lineTo(18f, 11f)
        }
    }

    val ArrowDown: ImageVector by lazy {
        buildIcon("ArrowDown") {
            moveTo(12f, 5f); lineTo(12f, 19f)
            moveTo(6f, 13f); lineTo(12f, 19f); lineTo(18f, 13f)
        }
    }

    // Settings & Actions
    val Settings: ImageVector by lazy {
        buildIcon("Settings") {
            moveTo(15f, 12f)
            arcTo(3f, 3f, 0f, isMoreThanHalf = true, isPositiveArc = true, 9f, 12f)
            arcTo(3f, 3f, 0f, isMoreThanHalf = true, isPositiveArc = true, 15f, 12f)
            moveTo(19.5f, 12f)
            arcTo(7.5f, 7.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 4.5f, 12f)
            arcTo(7.5f, 7.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 19.5f, 12f)
            moveTo(12f, 2f); lineTo(12f, 4.5f)
            moveTo(12f, 19.5f); lineTo(12f, 22f)
            moveTo(2f, 12f); lineTo(4.5f, 12f)
            moveTo(19.5f, 12f); lineTo(22f, 12f)
        }
    }

    val Globe: ImageVector by lazy {
        buildIcon("Globe") {
            moveTo(21f, 12f)
            arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = true, 3f, 12f)
            arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = true, 21f, 12f)
            moveTo(3.5f, 9.5f); lineTo(20.5f, 9.5f)
            moveTo(3.5f, 14.5f); lineTo(20.5f, 14.5f)
            moveTo(12f, 3f)
            curveTo(14.8f, 6f, 14.8f, 18f, 12f, 21f)
            moveTo(12f, 3f)
            curveTo(9.2f, 6f, 9.2f, 18f, 12f, 21f)
        }
    }

    val Palette: ImageVector by lazy {
        buildIcon("Palette") {
            moveTo(12f, 3f)
            arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = false, 20.5f, 15f)
            curveTo(20.5f, 13.5f, 19.2f, 12.5f, 17.5f, 12.5f)
            lineTo(15.5f, 12.5f)
            curveTo(14.1f, 12.5f, 13f, 11.4f, 13f, 10f)
            curveTo(13f, 9.2f, 13.4f, 8.5f, 14f, 8f)
            curveTo(15f, 7f, 14.2f, 3f, 12f, 3f)
            close()
            moveTo(7.5f, 11f); lineTo(7.6f, 11f)
            moveTo(9.5f, 7.5f); lineTo(9.6f, 7.5f)
            moveTo(8f, 15f); lineTo(8.1f, 15f)
        }
    }

    val Export: ImageVector by lazy {
        buildIcon("Export") {
            moveTo(12f, 15f); lineTo(12f, 4f)
            moveTo(7.5f, 8.5f); lineTo(12f, 4f); lineTo(16.5f, 8.5f)
            moveTo(4f, 15f); lineTo(4f, 19f); lineTo(20f, 19f); lineTo(20f, 15f)
        }
    }

    val Import: ImageVector by lazy {
        buildIcon("Import") {
            moveTo(12f, 4f); lineTo(12f, 15f)
            moveTo(7.5f, 10.5f); lineTo(12f, 15f); lineTo(16.5f, 10.5f)
            moveTo(4f, 15f); lineTo(4f, 19f); lineTo(20f, 19f); lineTo(20f, 15f)
        }
    }

    val Delete: ImageVector by lazy {
        buildIcon("Delete") {
            moveTo(4f, 7f); lineTo(20f, 7f)
            moveTo(9f, 7f); lineTo(9f, 4f); lineTo(15f, 4f); lineTo(15f, 7f)
            moveTo(6f, 7f); lineTo(7f, 20f); lineTo(17f, 20f); lineTo(18f, 7f)
            moveTo(10f, 11f); lineTo(10f, 16f)
            moveTo(14f, 11f); lineTo(14f, 16f)
        }
    }

    val Lock: ImageVector by lazy {
        buildIcon("Lock") {
            moveTo(5f, 11f); lineTo(19f, 11f); lineTo(19f, 20f); lineTo(5f, 20f); close()
            moveTo(8f, 11f); lineTo(8f, 7.5f)
            arcTo(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 16f, 7.5f)
            lineTo(16f, 11f)
        }
    }

    val Add: ImageVector by lazy {
        buildIcon("Add") {
            moveTo(12f, 5f); lineTo(12f, 19f)
            moveTo(5f, 12f); lineTo(19f, 12f)
        }
    }

    val Edit: ImageVector by lazy {
        buildIcon("Edit") {
            moveTo(4f, 20f); lineTo(8.5f, 19f); lineTo(19f, 8.5f)
            lineTo(15.5f, 5f); lineTo(5f, 15.5f); close()
        }
    }

    val Close: ImageVector by lazy {
        buildIcon("Close") {
            moveTo(6f, 6f); lineTo(18f, 18f)
            moveTo(18f, 6f); lineTo(6f, 18f)
        }
    }

    val Back: ImageVector by lazy {
        buildIcon("Back") {
            moveTo(19f, 12f); lineTo(5f, 12f)
            moveTo(11f, 6f); lineTo(5f, 12f); lineTo(11f, 18f)
        }
    }

    val Refresh: ImageVector by lazy {
        buildIcon("Refresh") {
            moveTo(20f, 12f)
            arcTo(8f, 8f, 0f, isMoreThanHalf = true, isPositiveArc = true, 17.6f, 6.3f)
            moveTo(20f, 4f); lineTo(20f, 8.5f); lineTo(15.5f, 8.5f)
        }
    }
}
