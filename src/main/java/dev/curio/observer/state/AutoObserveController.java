package dev.curio.observer.state;

import dev.curio.observer.config.ObserverConfig;

/** Decides at most one observation command per configured interval, independently of frame rate. */
public final class AutoObserveController {
    public record Session(boolean ready, boolean spectator, String ownName, String observedName, boolean targetOnline) {}
    private String target = "";
    private long nextAttemptAt;

    public void reset() {
        target = "";
        nextAttemptAt = 0;
    }

    /** Config is normalized at load/save; session values are read together on the client thread. */
    public String poll(ObserverConfig config, Session session, long now) {
        if (!config.autoObserve || config.defaultTarget.isEmpty() || !session.ready()
                || config.defaultTarget.equalsIgnoreCase(session.ownName())) {
            reset();
            return null;
        }
        if (!target.equalsIgnoreCase(config.defaultTarget)) {
            target = config.defaultTarget;
            nextAttemptAt = now;
        }
        if (session.spectator() && target.equalsIgnoreCase(session.observedName())) {
            nextAttemptAt = now + config.autoObserveSeconds * 1000L;
            return null;
        }
        if (!session.targetOnline() || now < nextAttemptAt) return null;
        nextAttemptAt = now + config.autoObserveSeconds * 1000L;
        return session.spectator() ? "spectate " + target : "gamemode spectator";
    }
}
