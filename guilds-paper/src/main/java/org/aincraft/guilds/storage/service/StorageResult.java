package org.aincraft.guilds.storage.service;

import java.util.Objects;
import java.util.Optional;

/** Typed outcome for guild storage service operations. */
public final class StorageResult<T> {
    public enum Status {
        SUCCESS,
        UNAUTHORIZED,
        PERMISSION_DENIED,
        SLOT_OCCUPIED,
        SLOT_EMPTY,
        CONFLICT,
        STORAGE_ERROR,
        INVALID_ARGUMENT
    }

    private final Status status;
    private final T value;
    private final String errorMessage;

    private StorageResult(Status status, T value, String errorMessage) {
        this.status = Objects.requireNonNull(status, "status");
        this.value = value;
        this.errorMessage = errorMessage;
    }

    public static <T> StorageResult<T> success(T value) {
        return new StorageResult<>(Status.SUCCESS, value, null);
    }

    public static <T> StorageResult<T> failure(Status status, String errorMessage) {
        if (status == Status.SUCCESS) {
            throw new IllegalArgumentException("failure status cannot be SUCCESS");
        }
        return new StorageResult<>(status, null, errorMessage);
    }

    public Status status() {
        return status;
    }

    public Optional<T> value() {
        return Optional.ofNullable(value);
    }

    public String errorMessage() {
        return errorMessage;
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }
}
