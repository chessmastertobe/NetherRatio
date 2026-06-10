package org.doraji.netherratio.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for the coordinate conversion math shared by portal travel
 * and the /netherratio calc command.
 */
class CoordinateMathTest {

    private static final double DELTA = 1e-9;

    @Test
    void toNetherWithVanillaRatioAndNoOffset() {
        assertEquals(100.0, CoordinateMath.toNether(800.0, 8.0, 0.0), DELTA);
        assertEquals(-100.0, CoordinateMath.toNether(-800.0, 8.0, 0.0), DELTA);
        assertEquals(0.0, CoordinateMath.toNether(0.0, 8.0, 0.0), DELTA);
    }

    @Test
    void toOverworldWithVanillaRatioAndNoOffset() {
        assertEquals(800.0, CoordinateMath.toOverworld(100.0, 8.0, 0.0), DELTA);
        assertEquals(-800.0, CoordinateMath.toOverworld(-100.0, 8.0, 0.0), DELTA);
        assertEquals(0.0, CoordinateMath.toOverworld(0.0, 8.0, 0.0), DELTA);
    }

    @Test
    void toNetherWithCustomRatio() {
        assertEquals(400.0, CoordinateMath.toNether(800.0, 2.0, 0.0), DELTA);
        assertEquals(50.0, CoordinateMath.toNether(800.0, 16.0, 0.0), DELTA);
    }

    @Test
    void toOverworldWithCustomRatio() {
        assertEquals(800.0, CoordinateMath.toOverworld(400.0, 2.0, 0.0), DELTA);
        assertEquals(800.0, CoordinateMath.toOverworld(50.0, 16.0, 0.0), DELTA);
    }

    @Test
    void toNetherWithPositiveOffset() {
        // README example: ratio 8, offset-x 1000 -> overworld 800 lands at nether 1100
        assertEquals(1100.0, CoordinateMath.toNether(800.0, 8.0, 1000.0), DELTA);
    }

    @Test
    void toNetherWithNegativeOffset() {
        assertEquals(-400.0, CoordinateMath.toNether(800.0, 8.0, -500.0), DELTA);
    }

    @Test
    void toOverworldSubtractsOffsetBeforeScaling() {
        assertEquals(800.0, CoordinateMath.toOverworld(1100.0, 8.0, 1000.0), DELTA);
        assertEquals(800.0, CoordinateMath.toOverworld(-400.0, 8.0, -500.0), DELTA);
    }

    @Test
    void conversionsAreInverseOfEachOther() {
        double[] coordinates = {0.0, 1.0, -1.0, 137.5, -29999968.0, 29999968.0};
        double[] ratios = {1.0, 2.0, 8.0, 16.0, 0.5};
        double[] offsets = {0.0, 1000.0, -500.0, 0.25};

        for (double coordinate : coordinates) {
            for (double ratio : ratios) {
                for (double offset : offsets) {
                    double roundTrip = CoordinateMath.toOverworld(
                        CoordinateMath.toNether(coordinate, ratio, offset), ratio, offset);
                    assertEquals(coordinate, roundTrip, 1e-6,
                        "round trip failed for coordinate=" + coordinate
                            + ", ratio=" + ratio + ", offset=" + offset);
                }
            }
        }
    }

    @Test
    void clampReturnsValueWithinBounds() {
        assertEquals(50.0, CoordinateMath.clamp(50.0, -100.0, 100.0), DELTA);
        assertEquals(-100.0, CoordinateMath.clamp(-100.0, -100.0, 100.0), DELTA);
        assertEquals(100.0, CoordinateMath.clamp(100.0, -100.0, 100.0), DELTA);
    }

    @Test
    void clampLimitsValuesOutsideBounds() {
        assertEquals(100.0, CoordinateMath.clamp(250.0, -100.0, 100.0), DELTA);
        assertEquals(-100.0, CoordinateMath.clamp(-250.0, -100.0, 100.0), DELTA);
    }
}
