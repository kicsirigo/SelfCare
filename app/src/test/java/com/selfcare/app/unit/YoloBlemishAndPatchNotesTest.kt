package com.selfcare.app.unit

import com.selfcare.app.domain.cv.YoloBlemishPostProcessor
import com.selfcare.app.ui.settings.PatchNotesCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YoloBlemishAndPatchNotesTest {

    @Test
    fun `yolo decoder ignores low confidence detections`() {
        val output = Array(5) { FloatArray(3) }
        output[0] = floatArrayOf(100f, 200f, 300f)
        output[1] = floatArrayOf(100f, 200f, 300f)
        output[2] = floatArrayOf(20f, 20f, 20f)
        output[3] = floatArrayOf(20f, 20f, 20f)
        output[4] = floatArrayOf(0.05f, 0.12f, 0.20f)

        val metrics = YoloBlemishPostProcessor.decode(output)
        assertEquals(0, metrics.estimatedSpotCount)
        assertEquals(0f, metrics.blemishScore, 0.001f)
    }

    @Test
    fun `yolo decoder merges overlapping boxes via NMS and scores distinct lesions`() {
        val output = Array(5) { FloatArray(4) }
        // Box 0 & Box 1 overlap heavily (same spot), Box 2 is separate, Box 3 is below threshold
        output[0] = floatArrayOf(120f, 122f, 400f, 500f) // cx
        output[1] = floatArrayOf(150f, 151f, 300f, 500f) // cy
        output[2] = floatArrayOf(24f, 24f, 20f, 20f)     // w
        output[3] = floatArrayOf(24f, 24f, 20f, 20f)     // h
        output[4] = floatArrayOf(0.82f, 0.65f, 0.55f, 0.10f)

        val metrics = YoloBlemishPostProcessor.decode(output)
        assertEquals(2, metrics.estimatedSpotCount)
        assertTrue(metrics.blemishScore in 0.15f..0.40f)
    }

    @Test
    fun `patch notes catalog lists v3_3_0 first with non-empty highlights`() {
        val latest = PatchNotesCatalog.releases.first()
        assertEquals("v3.3.0", latest.version)
        assertTrue(latest.highlights.isNotEmpty())
    }
}
