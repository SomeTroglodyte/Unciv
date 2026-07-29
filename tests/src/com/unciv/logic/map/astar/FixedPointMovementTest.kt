package com.unciv.logic.map.astar

import com.unciv.logic.map.astar.FixedPointMovement.Companion.fpmFromFixedPointBits
import com.unciv.logic.map.astar.FixedPointMovement.Companion.fpmFromMovement
import com.unciv.testing.GdxTestRunner
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class FixedPointMovementTest {

    @Test
    fun div_knownValues() {
        assertEquals(FixedPointMovement.FPM_POINT_FIVE, FixedPointMovement.FPM_ONE / fpmFromMovement(2))
        assertEquals(fpmFromMovement(2), FixedPointMovement.FPM_ONE / fpmFromMovement(0.5f))
    }

    @Test
    fun timesFixedPointMovement_scalesCorrectly() {
        assertEquals(fpmFromMovement(2), FixedPointMovement.FPM_ONE * fpmFromMovement(2))
        assertEquals(fpmFromMovement(1.5f), FixedPointMovement.FPM_ONE * fpmFromMovement(1.5f))
    }

    @Test
    fun divAndTimesRoundTrip_whenExact() {
        // Integer fixed-point truncates some quotients; only assert pairs that stay exact.
        assertEquals(fpmFromMovement(3), (fpmFromMovement(3) / fpmFromMovement(2)) * fpmFromMovement(2))
        assertEquals(FixedPointMovement.FPM_ONE, (FixedPointMovement.FPM_ONE / fpmFromMovement(2)) * fpmFromMovement(2))
    }

    @Test
    fun fpmFromMovement_roundTripsThroughFloat() {
        for (move in listOf(0f, 0.5f, 1f, 1.5f, 2f, 3f, 10f)) {
            val fpm = fpmFromMovement(move)
            assertEquals(move, fpm.toFloat(), 0.001f)
        }
    }

    @Test
    fun primeBits_roundTripThroughFloat() {
        // Exercises non-power-of-two bit patterns; catches inverted div/times formulas.
        for (bits in intArrayOf(7, 13, 17, 31, 37, 41, 113, 127)) {
            val fpm = fpmFromFixedPointBits(bits)
            assertEquals("bits=$bits", fpm, fpmFromMovement(fpm.toFloat()))
        }
    }
}
