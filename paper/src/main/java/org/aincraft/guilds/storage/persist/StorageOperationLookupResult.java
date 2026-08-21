package org.aincraft.guilds.storage.persist;

import java.util.Objects;
import java.util.Optional;

/** Typed outcome for durable storage operation journal lookups. */
public sealed interface StorageOperationLookupResult {

    enum Status {
        FOUND,
        NOT_FOUND,
        READ_FAILURE
    }

    Status status();

    Optional<StorageOperationRecord> record();

    String errorMessage();

    static StorageOperationLookupResult found(StorageOperationRecord record) {
        Objects.requireNonNull(record, "record");
        return new Found(record);
    }

    static StorageOperationLookupResult notFound() {
        return NotFound.INSTANCE;
    }

    static StorageOperationLookupResult readFailure(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            throw new IllegalArgumentException("errorMessage is required");
        }
        return new ReadFailure(errorMessage);
    }

    record Found(StorageOperationRecord value) implements StorageOperationLookupResult {
        @Override
        public Status status() {
            return Status.FOUND;
        }

        @Override
        public Optional<StorageOperationRecord> record() {
            return Optional.of(value);
        }

        @Override
        public String errorMessage() {
            return null;
        }
    }

    enum NotFound implements StorageOperationLookupResult {
        INSTANCE;

        @Override
        public Status status() {
            return Status.NOT_FOUND;
        }

        @Override
        public Optional<StorageOperationRecord> record() {
            return Optional.empty();
        }

        @Override
        public String errorMessage() {
            return null;
        }
    }

    record ReadFailure(String message) implements StorageOperationLookupResult {
        @Override
        public Status status() {
            return Status.READ_FAILURE;
        }

        @Override
        public Optional<StorageOperationRecord> record() {
            return Optional.empty();
        }

        @Override
        public String errorMessage() {
            return message;
        }
    }
}
