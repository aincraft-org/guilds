package org.aincraft.guilds.storage.codec;

import org.aincraft.guilds.territory.storage.OpaqueItemPayload;
import org.bukkit.inventory.ItemStack;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

/** Verifies Bukkit item stack access occurs on the server main thread. */
@FunctionalInterface
interface MainThreadVerifier {
    void verifyMainThread();

    MainThreadVerifier ANY = () -> {};

    MainThreadVerifier BUKKIT = () -> {
        if (!org.bukkit.Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Item stack codec must run on the main thread");
        }
    };
}

/** Codec for Paper item stacks at the opaque storage boundary. */
public final class ItemStackStorageCodec {
    public static final String SCHEMA = "paper:v1";

    private final MainThreadVerifier mainThreadVerifier;

    public ItemStackStorageCodec() {
        this(MainThreadVerifier.BUKKIT);
    }

    public ItemStackStorageCodec(MainThreadVerifier mainThreadVerifier) {
        this.mainThreadVerifier = Objects.requireNonNull(mainThreadVerifier, "mainThreadVerifier");
    }

    public OpaqueItemPayload encode(ItemStack item) {
        mainThreadVerifier.verifyMainThread();
        Objects.requireNonNull(item, "item");
        byte[] bytes = item.serializeAsBytes();
        return new OpaqueItemPayload(SCHEMA, fingerprint(bytes), Base64.getEncoder().encodeToString(bytes));
    }

    public ItemStack decode(OpaqueItemPayload payload) {
        mainThreadVerifier.verifyMainThread();
        Objects.requireNonNull(payload, "payload");
        if (!SCHEMA.equals(payload.schema())) {
            throw new IllegalArgumentException("Unsupported item schema: " + payload.schema());
        }
        final byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(payload.payload());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid item payload", e);
        }
        if (!MessageDigest.isEqual(fingerprint(bytes).getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                payload.fingerprint().getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException("Item payload fingerprint mismatch");
        }
        try {
            return ItemStack.deserializeBytes(bytes);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid serialized item payload", e);
        }
    }

    private static String fingerprint(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 unavailable", e);
        }
    }
}
