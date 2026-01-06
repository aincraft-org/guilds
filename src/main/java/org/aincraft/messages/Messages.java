package org.aincraft.messages;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Static facade for message access throughout the plugin.
 * Must be initialized before use via {@link #init(MessageProvider)}.
 */
public final class Messages {

    private static MessageProvider provider;

    private Messages() {
    }

    /**
     * Initializes the Messages system with the given provider.
     * Must be called during plugin startup.
     *
     * @param messageProvider the provider to use
     */
    public static void init(MessageProvider messageProvider) {
        provider = messageProvider;
    }

    /**
     * Gets the underlying provider. Used for advanced operations.
     *
     * @return the message provider
     */
    public static MessageProvider provider() {
        checkInitialized();
        return provider;
    }

    /**
     * Gets a formatted message Component.
     *
     * @param key the message key
     * @param args arguments to substitute for {0}, {1}, etc.
     * @return the formatted Component
     */
    public static Component get(MessageKey key, Object... args) {
        checkInitialized();
        return provider.get(key, args);
    }

    /**
     * Gets a formatted message Component with TagResolvers.
     *
     * @param key the message key
     * @param resolvers TagResolvers for placeholders
     * @return the formatted Component
     */
    public static Component getWithResolvers(MessageKey key, TagResolver... resolvers) {
        checkInitialized();
        return provider.getWithResolvers(key, resolvers);
    }

    /**
     * Gets the raw message string.
     *
     * @param key the message key
     * @return the raw message
     */
    public static String getRaw(MessageKey key) {
        checkInitialized();
        return provider.getRaw(key);
    }

    /**
     * Sends a formatted message to a CommandSender.
     *
     * @param sender the recipient
     * @param key the message key
     * @param args arguments to substitute
     */
    public static void send(CommandSender sender, MessageKey key, Object... args) {
        sender.sendMessage(get(key, args));
    }

    /**
     * Sends a formatted message to a Player.
     *
     * @param player the recipient
     * @param key the message key
     * @param args arguments to substitute
     */
    public static void send(Player player, MessageKey key, Object... args) {
        player.sendMessage(get(key, args));
    }

    /**
     * Reloads all messages from the source.
     */
    public static void reload() {
        checkInitialized();
        provider.reload();
    }

    private static void checkInitialized() {
        if (provider == null) {
            throw new IllegalStateException("Messages not initialized. Call Messages.init() first.");
        }
    }
}
