package dev.curio.observer.render;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class BroadcastLightingTest {
    @Test void inactiveOrInvalidRequestsKeepThePlayersOwnGamma() {
        assertEquals(0.5, BroadcastLighting.gamma(0.5, false, 8));
        assertEquals(0.5, BroadcastLighting.gamma(0.5, true, Double.NaN));
        assertEquals(0.5, BroadcastLighting.gamma(0.5, true, Double.POSITIVE_INFINITY));
    }

    @Test void shaderNightVisionGrowsFromBrightToGammaFive() {
        assertEquals(0, BroadcastLighting.shaderNightVision(1));
        assertEquals(0, BroadcastLighting.shaderNightVision(0.5));
        assertEquals(0.5f, BroadcastLighting.shaderNightVision(3));
        assertEquals(1, BroadcastLighting.shaderNightVision(5));
        assertEquals(1, BroadcastLighting.shaderNightVision(15));
        assertEquals(0, BroadcastLighting.shaderNightVision(Double.NaN));
    }

    @Test void activeGammaGoesBeyondTheVanillaSliderButStaysBounded() {
        assertEquals(3, BroadcastLighting.gamma(1, true, 3));
        assertEquals(0.2, BroadcastLighting.gamma(1, true, 0.2), "a lower value is respected too");
        assertEquals(BroadcastLighting.MAX_GAMMA, BroadcastLighting.gamma(1, true, 99));
        assertEquals(0, BroadcastLighting.gamma(1, true, -2));
    }
}
