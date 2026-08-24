package org.aincraft.guilds.storage.codec;

import org.aincraft.guilds.territory.storage.OpaqueItemPayload;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class ItemStackStorageCodecTest {
    private final ItemStackStorageCodec codec = new ItemStackStorageCodec(MainThreadVerifier.ANY);

    @Test
    void encodeUsesSerializedBytesAndSchema() {
        ItemStack item = mock(ItemStack.class);
        byte[] bytes = "diamond-stack".getBytes(StandardCharsets.UTF_8);
        when(item.serializeAsBytes()).thenReturn(bytes);

        OpaqueItemPayload payload = codec.encode(item);

        assertEquals(ItemStackStorageCodec.SCHEMA, payload.schema());
        assertEquals(Base64.getEncoder().encodeToString(bytes), payload.payload());
        assertEquals(fingerprint(bytes), payload.fingerprint());
    }

    @Test
    void decodeRestoresItemStackBytes() {
        byte[] bytes = "guild-blade".getBytes(StandardCharsets.UTF_8);
        OpaqueItemPayload payload = new OpaqueItemPayload(
                ItemStackStorageCodec.SCHEMA,
                fingerprint(bytes),
                Base64.getEncoder().encodeToString(bytes));
        ItemStack restored = mock(ItemStack.class);

        try (MockedStatic<ItemStack> itemStacks = mockStatic(ItemStack.class)) {
            itemStacks.when(() -> ItemStack.deserializeBytes(bytes)).thenReturn(restored);

            assertEquals(restored, codec.decode(payload));
        }
    }

    @Test
    void fingerprintIsDeterministicSha256() throws Exception {
        byte[] bytes = "deterministic-bytes".getBytes(StandardCharsets.UTF_8);
        ItemStack item = mock(ItemStack.class);
        when(item.serializeAsBytes()).thenReturn(bytes);

        OpaqueItemPayload first = codec.encode(item);
        OpaqueItemPayload second = codec.encode(item);

        assertEquals(first.fingerprint(), second.fingerprint());
        assertEquals(
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),
                first.fingerprint());
    }

    @Test
    void rejectsFingerprintMismatch() {
        byte[] bytes = "payload-bytes".getBytes(StandardCharsets.UTF_8);
        OpaqueItemPayload payload = new OpaqueItemPayload(
                ItemStackStorageCodec.SCHEMA,
                "deadbeef",
                Base64.getEncoder().encodeToString(bytes));

        assertThrows(IllegalArgumentException.class, () -> codec.decode(payload));
    }

    @Test
    void rejectsUnsupportedSchema() {
        OpaqueItemPayload payload = new OpaqueItemPayload("legacy:v0", "abc", "def");

        assertThrows(IllegalArgumentException.class, () -> codec.decode(payload));
    }

    @Test
    void differentBytePayloadsProduceDifferentFingerprints() {
        ItemStack firstItem = mock(ItemStack.class);
        ItemStack secondItem = mock(ItemStack.class);
        when(firstItem.serializeAsBytes()).thenReturn(new byte[] {1, 2, 3});
        when(secondItem.serializeAsBytes()).thenReturn(new byte[] {4, 5, 6});

        assertNotEquals(codec.encode(firstItem).fingerprint(), codec.encode(secondItem).fingerprint());
    }

    @Test
    void roundTripPreservesSerializedBytes() {
        byte[] bytes = new byte[] {9, 8, 7, 6};
        ItemStack item = mock(ItemStack.class);
        when(item.serializeAsBytes()).thenReturn(bytes);
        OpaqueItemPayload payload = codec.encode(item);
        ItemStack restored = mock(ItemStack.class);
        when(restored.serializeAsBytes()).thenReturn(bytes);

        try (MockedStatic<ItemStack> itemStacks = mockStatic(ItemStack.class)) {
            itemStacks.when(() -> ItemStack.deserializeBytes(bytes)).thenReturn(restored);

            assertArrayEquals(bytes, restored.serializeAsBytes());
            assertEquals(restored, codec.decode(payload));
        }
    }

    private static String fingerprint(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
