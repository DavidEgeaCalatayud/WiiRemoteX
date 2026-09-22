package io.github.davidegeacalatayud.wiiremotex.core.session

import io.github.davidegeacalatayud.wiiremotex.core.model.Orientation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VirtualIrCameraTest {
    @Test
    fun `calibrated neutral orientation produces two visible centered points`() {
        val camera = VirtualIrCamera()
        val neutral = Orientation(
            yawDegrees = 20f,
            pitchDegrees = -5f,
        )

        camera.calibrate(neutral)
        val state = camera.project(neutral, enabled = true)

        assertTrue(state.enabled)
        assertTrue(state.points[0].visible)
        assertTrue(state.points[1].visible)
        assertEquals(384, state.points[0].y)
        assertEquals(384, state.points[1].y)
    }

    @Test
    fun `disabled camera exposes no visible objects`() {
        val camera = VirtualIrCamera()
        val neutral = Orientation()

        camera.calibrate(neutral)
        val state = camera.project(neutral, enabled = false)

        assertTrue(state.points.none { it.visible })
    }
}
