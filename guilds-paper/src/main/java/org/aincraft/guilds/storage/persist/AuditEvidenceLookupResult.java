package org.aincraft.guilds.storage.persist;

/** Typed outcome for durable storage audit evidence lookups. */
public sealed interface AuditEvidenceLookupResult {

    enum Status {
        MATCHING,
        NONE,
        READ_FAILURE
    }

    Status status();

    String errorMessage();

    static AuditEvidenceLookupResult matching() {
        return Matching.INSTANCE;
    }

    static AuditEvidenceLookupResult none() {
        return None.INSTANCE;
    }

    static AuditEvidenceLookupResult readFailure(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            throw new IllegalArgumentException("errorMessage is required");
        }
        return new ReadFailure(errorMessage);
    }

    enum Matching implements AuditEvidenceLookupResult {
        INSTANCE;

        @Override
        public Status status() {
            return Status.MATCHING;
        }

        @Override
        public String errorMessage() {
            return null;
        }
    }

    enum None implements AuditEvidenceLookupResult {
        INSTANCE;

        @Override
        public Status status() {
            return Status.NONE;
        }

        @Override
        public String errorMessage() {
            return null;
        }
    }

    record ReadFailure(String message) implements AuditEvidenceLookupResult {
        @Override
        public Status status() {
            return Status.READ_FAILURE;
        }

        @Override
        public String errorMessage() {
            return message;
        }
    }
}
