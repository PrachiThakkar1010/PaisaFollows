package com.paisafollows.common;

import java.nio.ByteBuffer;
import java.util.UUID;

/** Converts between Java UUIDs and MySQL BINARY(16) columns. */
public final class UuidUtil {

    private UuidUtil() {
    }

    public static UUID newId() {
        return UUID.randomUUID();
    }

    /** Use when writing to the database. */
    public static byte[] toBytes(UUID uuid) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }

    /** Use when reading from the database. */
    public static UUID fromBytes(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
