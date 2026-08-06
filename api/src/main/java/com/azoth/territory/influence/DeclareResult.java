package com.azoth.territory.influence;

/** Declare/cancel outcome with a human-readable message. */
public record DeclareResult(DeclareStatus status, String message) {

    public DeclareResult {
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        message = message == null ? "" : message;
    }

    public static DeclareResult ok(DeclareStatus status, String message) {
        return new DeclareResult(status, message);
    }

    public static DeclareResult error(DeclareStatus status, String message) {
        return new DeclareResult(status, message);
    }

    public boolean isSuccess() {
        return status == DeclareStatus.DECLARED || status == DeclareStatus.CANCELLED;
    }
}
