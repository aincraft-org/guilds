package org.aincraft.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;

/**
 * Utility for formatting messages with MiniMessage and standard tags.
 */
public final class MessageFormatter {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.builder()
            .tags(TagResolver.resolver(
                    StandardTags.color(),
                    StandardTags.decorations()
            ))
            .build();

    // Message format constants
    public static final String HEADER = "<gold>=== %s ===%s</gold>";
    public static final String USAGE = "<yellow>%s<reset> <gray>- %s</gray>";
    public static final String ERROR = "<red>%s</red>";
    public static final String SUCCESS = "<green>%s</green>";
    public static final String INFO = "<yellow>%s<reset>: <white>%s</white>";
    public static final String HIGHLIGHT = "<gold>%s</gold>";
    public static final String WARNING = "<yellow>%s</yellow>";
    public static final String SUBHEADER = "<yellow>%s</yellow>";

    private MessageFormatter() {
    }

    /**
     * Formats a message using MiniMessage syntax.
     */
    public static Component format(String template, Object... args) {
        String formatted = String.format(template, args);
        return MINI_MESSAGE.deserialize(formatted);
    }

    /**
     * Deserializes a raw MiniMessage string.
     */
    public static Component deserialize(String message) {
        return MINI_MESSAGE.deserialize(message);
    }
}
