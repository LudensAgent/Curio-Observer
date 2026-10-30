package dev.curio.observer.render;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

class BroadcastCameraStateTest {
    private final Object target = new Object();
    private final Object world = new Object();
    private final BroadcastCameraState state = new BroadcastCameraState();

    @Test void waitsForContinuousOpenSpaceAndRetreatsImmediatelyInTunnels() {
        assertFalse(frame(0, 3, true).thirdPerson());
        assertFalse(frame(0.79, 3, true).thirdPerson());
        var follow = frame(0.8, 3, true);
        assertTrue(follow.thirdPerson());
        assertTrue(follow.distance() >= 1.25);
        assertTrue(follow.distance() < 3);
        assertTrue(frame(0.9, 3, true).distance() > follow.distance());
        var tunnel = frame(0.91, 3, false);
        assertFalse(tunnel.thirdPerson());
        assertEquals(0, tunnel.distance());
        assertFalse(frame(2, 3, false).thirdPerson());
        assertFalse(frame(3, 3, true).thirdPerson());
        assertTrue(frame(3.8, 3, true).thirdPerson());
    }

    @Test void doorwayJitterResetsEntryHoldAndUsesSeparateExitThreshold() {
        frame(0, 3, true);
        assertFalse(frame(0.79, 2.19, true).thirdPerson());
        assertFalse(frame(1, 2.2, true).thirdPerson());
        assertFalse(frame(1.79, 2.2, true).thirdPerson());
        assertTrue(frame(1.8, 2.2, true).thirdPerson());
        assertTrue(frame(1.81, 1.8, true).thirdPerson());
        assertTrue(frame(1.82, 1.25, true).thirdPerson());
        assertFalse(frame(1.83, 1.24, true).thirdPerson());
        assertFalse(frame(1.84, 3, true).thirdPerson());
    }

    @Test void collisionClampsDistanceImmediatelyButRecoveryPullsBackGradually() {
        frame(0, 3, true);
        frame(0.8, 3, true);
        var before = frame(1, 3, true);
        var obstructed = frame(1.001, 1.3, true);
        assertTrue(before.distance() > 1.3);
        assertTrue(obstructed.thirdPerson());
        assertEquals(1.3, obstructed.distance());
        var recovering = frame(1.101, 3, true);
        assertTrue(recovering.distance() > 1.3);
        assertTrue(recovering.distance() < before.distance());
    }

    @Test void zeroSmoothingTracksImmediatelyAndThirdPersonPitchDoesNotLeakIntoFirstPerson() {
        update(0, 0, 85, 3, true, 0);
        var follow = update(0.8, 120, 85, 3, true, 0);
        assertTrue(follow.thirdPerson());
        assertEquals(120, follow.yaw());
        assertEquals(25, follow.pitch());
        assertEquals(3, follow.distance());
        assertEquals(-10, update(0.9, 120, -80, 3, true, 0).pitch());
        var tunnel = update(1, 135, -80, 3, false, 0.18);
        assertFalse(tunnel.thirdPerson());
        assertEquals(-80, tunnel.pitch());
        assertEquals(0, tunnel.distance());
    }

    @Test void yawCrossesZeroByTheShortestArc() {
        update(0, 359, 0, 0, false, 0.18);
        var turn = update(0.1, 1, 0, 0, false, 0.18);
        assertTrue(turn.yaw() > 359 && turn.yaw() < 361);
    }

    @Test void rotationUsesElapsedTimeRatherThanFrameCountAndCapsLongPauses() {
        assertEquals(simulate(30), simulate(120), 0.001);
        update(0, 0, 0, 0, false, 0.18);
        float afterPause = update(10, 90, 0, 0, false, 0.18).yaw();
        assertTrue(afterPause > 0 && afterPause < 45, "A render pause must not produce a full-speed camera whip");
    }

    @Test void targetWorldTeleportAndExplicitResetDiscardOldSmoothingAndShot() {
        frame(0, 3, true);
        assertTrue(frame(0.8, 3, true).thirdPerson());
        Object newTarget = new Object();
        assertSnap(state.update(newTarget, world, Vec3d.ZERO, 140, 80, 3, true, 3, 0.18, 900_000_000L), 140, 80);
        Object newWorld = new Object();
        assertSnap(state.update(newTarget, newWorld, Vec3d.ZERO, 200, -80, 3, true, 3, 0.18, 1_000_000_000L), 200, -80);
        assertSnap(state.update(newTarget, newWorld, new Vec3d(9, 0, 0), 270, 45, 3, true, 3, 0.18, 1_100_000_000L), 270, 45);
        state.reset();
        assertSnap(state.update(newTarget, newWorld, new Vec3d(9, 0, 0), 10, 70, 3, true, 3, 0.18, 1_200_000_000L), 10, 70);
    }

    @Test void configuredShortFollowDistanceCanStillEnterThirdPerson() {
        state.update(target, world, Vec3d.ZERO, 0, 0, 1.5, true, 1.5, 0.18, 0);
        assertTrue(state.update(target, world, Vec3d.ZERO, 0, 0, 1.5, true, 1.5, 0.18, 800_000_000L).thirdPerson());
    }

    private BroadcastCameraState.Frame frame(double seconds, double available, boolean roomy) {
        return update(seconds, 0, 0, available, roomy, 0.18);
    }

    private BroadcastCameraState.Frame update(double seconds, float yaw, float pitch, double available,
                                             boolean roomy, double smoothing) {
        return state.update(target, world, Vec3d.ZERO, yaw, pitch, available, roomy, 3, smoothing,
                Math.round(seconds * 1_000_000_000));
    }

    private float simulate(int framesPerSecond) {
        BroadcastCameraState simulation = new BroadcastCameraState();
        simulation.update(target, world, Vec3d.ZERO, 0, 0, 0, false, 3, 0.18, 0);
        BroadcastCameraState.Frame result = null;
        for (int frame = 1; frame <= framesPerSecond; frame++) {
            result = simulation.update(target, world, Vec3d.ZERO, 90, 0, 0, false, 3, 0.18,
                    Math.round(frame * 1_000_000_000.0 / framesPerSecond));
        }
        return result.yaw();
    }

    private static void assertSnap(BroadcastCameraState.Frame frame, float yaw, float pitch) {
        assertEquals(yaw, frame.yaw());
        assertEquals(pitch, frame.pitch());
        assertFalse(frame.thirdPerson());
        assertEquals(0, frame.distance());
    }
}
