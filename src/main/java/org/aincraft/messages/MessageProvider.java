package org.aincraft.messages;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * Interface for message retrieval and formatting.
 * Implementations can load messages from different sources (YAML, database, etc.).
 */
public interface MessageProvider {

    /**
     * Gets the raw message template for a key.
     *
     * @param key the message key
     * @return the raw message string, or the key path if not found
     */
    String getRaw(MessageKey key);

    /**
     * Gets a formatted message Component with placeholder substitution.
     *
     * @param key the message key
     * @param args arguments to substitute for {0}, {1}, etc.
     * @return the formatted Component
     */
    Component get(MessageKey key, Object... args);

    /**
     * Gets a formatted message Component with TagResolvers for advanced MiniMessage features.
     *
     * @param key the message key
     * @param resolvers TagResolvers for placeholders
     * @return the formatted Component
     */
    Component getWithResolvers(MessageKey key, TagResolver... resolvers);

    /**
     * Reloads messages from the source.
     * Called when configuration is reloaded.
     */
    void reload();

    /**
     * Checks if a message key exists in the loaded messages.
     *
     * @param key the message key
     * @return true if the key has a message defined
     */
    boolean hasMessage(MessageKey key);
}
