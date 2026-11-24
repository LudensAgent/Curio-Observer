package dev.curio.observer.render;

/** Frame-time camera smoothing and shot hysteresis, independent of rendering and entity mutation. */
public final class BroadcastCameraState {
    private static final double MIN_THIRD_PERSON_DISTANCE = 1.25;
    private static final long OPEN_HOLD_NANOS = 800_000_000L;
    private static final long NO_OPEN_SPACE = Long.MIN_VALUE;
    private Object target;
    private Object world;
    private double eyeX, eyeY, eyeZ;
    private long lastNanos;
    private long openSince = NO_OPEN_SPACE;
    private boolean initialized;
    private boolean thirdPerson;
    private float yaw;
    private float pitch;
    private double distance;

    public record Frame(float yaw, float pitch, double distance, boolean thirdPerson) {}

    public Frame update(Object target, Object world, double eyeX, double eyeY, double eyeZ,
                        float targetYaw, float targetPitch,
                        double availableDistance, boolean roomy, double desiredDistance,
                        double smoothingSeconds, long nowNanos) {
        double dx = this.eyeX - eyeX;
        double dy = this.eyeY - eyeY;
        double dz = this.eyeZ - eyeZ;
        boolean discontinuity = !initialized || this.target != target || this.world != world
                || (dx * dx + dy * dy + dz * dz) > 64 || nowNanos < lastNanos;
        double elapsed = discontinuity ? 0 : Math.min(0.1, (nowNanos - lastNanos) / 1_000_000_000.0);
        if (discontinuity) {
            reset();
            initialized = true;
            yaw = targetYaw;
            pitch = targetPitch;
        }
        this.target = target;
        this.world = world;
        this.eyeX = eyeX;
        this.eyeY = eyeY;
        this.eyeZ = eyeZ;
        lastNanos = nowNanos;

        double available = Math.max(0, availableDistance);
        double desired = Math.max(MIN_THIRD_PERSON_DISTANCE, desiredDistance);
        boolean wasThirdPerson = thirdPerson;
        if (!roomy || available < MIN_THIRD_PERSON_DISTANCE) {
            thirdPerson = false;
            distance = 0;
            openSince = NO_OPEN_SPACE;
        } else if (!thirdPerson) {
            if (available >= Math.min(desired, 2.2)) {
                if (openSince == NO_OPEN_SPACE) openSince = nowNanos;
                if (nowNanos - openSince >= OPEN_HOLD_NANOS) {
                    thirdPerson = true;
                    distance = MIN_THIRD_PERSON_DISTANCE;
                }
            } else openSince = NO_OPEN_SPACE;
        }

        double blend = smoothingSeconds <= 0 ? 1 : -Math.expm1(-elapsed / smoothingSeconds);
        yaw += (float) (wrapDegrees(targetYaw - yaw) * blend);
        float desiredPitch = thirdPerson ? Math.max(-10, Math.min(25, targetPitch)) : targetPitch;
        if (thirdPerson != wasThirdPerson) pitch = desiredPitch;
        else pitch += (float) ((desiredPitch - pitch) * blend);
        if (thirdPerson) {
            double desiredOffset = Math.min(available, desired);
            if (desiredOffset < distance) distance = desiredOffset;
            else distance += (desiredOffset - distance) * blend;
            distance = Math.min(distance, available);
        }
        return new Frame(yaw, pitch, distance, thirdPerson);
    }

    public void reset() {
        target = null;
        world = null;
        eyeX = eyeY = eyeZ = 0;
        lastNanos = 0;
        openSince = NO_OPEN_SPACE;
        initialized = false;
        thirdPerson = false;
        yaw = 0;
        pitch = 0;
        distance = 0;
    }

    private static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360;
        if (wrapped >= 180) wrapped -= 360;
        if (wrapped < -180) wrapped += 360;
        return wrapped;
    }
}
