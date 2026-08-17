package dev.mintychochip.territory.storage;

import org.bukkit.inventory.ItemStack;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/** Encodes Paper item stacks as opaque storage payloads. */
public final class PaperItemCodec {
    /** Schema written into every payload. */
    public static final String SCHEMA = "paper-itemstack-bytes-v1";

    /**
     * Encodes a non-empty stack.
     *
     * @param stack item
     * @return payload, or empty when the stack cannot be stored
     */
    public Optional<OpaqueItemPayload> encode(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
            return Optional.empty();
        }
        try {
            byte[] bytes = stack.serializeAsBytes();
            return Optional.of(new OpaqueItemPayload(SCHEMA, fingerprint(bytes),
                    Base64.getEncoder().encodeToString(bytes)));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /**
     * Decodes a payload produced by this codec.
     *
     * @param payload stored item
     * @return item stack, or empty when the payload cannot be read
     */
    public Optional<ItemStack> decode(OpaqueItemPayload payload) {
        if (payload == null || !SCHEMA.equals(payload.schema())) {
            return Optional.empty();
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(payload.payload());
            if (!fingerprint(bytes).equals(payload.fingerprint())) {
                return Optional.empty();
            }
            ItemStack stack = ItemStack.deserializeBytes(bytes);
            if (stack == null || stack.getType().isAir()) {
                return Optional.empty();
            }
            return Optional.of(stack);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static String fingerprint(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required", e);
        }
    }
}
