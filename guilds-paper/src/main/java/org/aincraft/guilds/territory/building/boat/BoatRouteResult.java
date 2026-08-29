package org.aincraft.guilds.territory.building.boat;

/** Geometry-only result of a bounded boat connectivity check. */
public record BoatRouteResult(Status status, double distance) {
    public BoatRouteResult {
        if (status == null) {
            throw new NullPointerException("status");
        }
        if (status == Status.CONNECTED) {
            if (!Double.isFinite(distance) || distance < 0.0) {
                throw new IllegalArgumentException("connected distance must be finite and non-negative");
            }
        } else if (distance != -1.0) {
            throw new IllegalArgumentException("non-connected routes do not carry a distance");
        }
    }

    public static BoatRouteResult connected(double distance) {
        return new BoatRouteResult(Status.CONNECTED, distance);
    }

    public static BoatRouteResult disconnected() {
        return new BoatRouteResult(Status.DISCONNECTED, -1.0);
    }

    public static BoatRouteResult pending() {
        return new BoatRouteResult(Status.PENDING, -1.0);
    }

    public static BoatRouteResult unavailable() {
        return new BoatRouteResult(Status.UNAVAILABLE, -1.0);
    }

    public boolean isConnected() {
        return status == Status.CONNECTED;
    }

    public Status state() {
        return status;
    }

    public double navigableDistance() {
        return distance;
    }

    public boolean isCacheable() {
        return status == Status.CONNECTED || status == Status.DISCONNECTED;
    }

    /** Alias emphasizing that this is the only route data retained. */
    public double scalarDistance() {
        return distance;
    }

    public boolean isScalarOnly() {
        return true;
    }

    public enum Status {
        CONNECTED,
        DISCONNECTED,
        PENDING,
        UNAVAILABLE
    }
}
