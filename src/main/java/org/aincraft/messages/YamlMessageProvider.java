package org.aincraft.messages;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * YAML-backed implementation of MessageProvider.
 * Loads messages from messages_en.yml with full MiniMessage support.
 */
@Singleton
public class YamlMessageProvider implements MessageProvider {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{(\\d+)}");
    private static final String MESSAGES_FOLDER = "messages";
    private static final String DEFAULT_LOCALE = "en";

    private final MiniMessage miniMessage;
    private final Plugin plugin;
    private final Logger logger;
    private final Map<String, String> messages;

    @Inject
    public YamlMessageProvider(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.messages = new HashMap<>();
        this.miniMessage = MiniMessage.builder()
                .tags(TagResolver.resolver(
                        StandardTags.color(),
                        StandardTags.decorations(),
                        StandardTags.gradient(),
                        StandardTags.rainbow(),
                        StandardTags.clickEvent(),
                        StandardTags.hoverEvent(),
                        StandardTags.insertion(),
                        StandardTags.font(),
                        StandardTags.newline(),
                        StandardTags.reset()
                ))
                .build();

        reload();
    }

    @Override
    public String getRaw(MessageKey key) {
        String message = messages.get(key.getKey());
        if (message == null) {
            logger.warning("Missing message for key: " + key.getKey());
            return "<red>[Missing: " + key.getKey() + "]</red>";
        }
        return message;
    }

    @Override
    public Component get(MessageKey key, Object... args) {
        String raw = getRaw(key);
        String formatted = replacePlaceholders(raw, args);
        return miniMessage.deserialize(formatted);
    }

    @Override
    public Component getWithResolvers(MessageKey key, TagResolver... resolvers) {
        String raw = getRaw(key);
        return miniMessage.deserialize(raw, resolvers);
    }

    @Override
    public void reload() {
        messages.clear();

        File messagesFolder = new File(plugin.getDataFolder(), MESSAGES_FOLDER);
        if (!messagesFolder.exists()) {
            messagesFolder.mkdirs();
        }

        // Extract default messages file if it doesn't exist
        File defaultFile = new File(messagesFolder, "messages_" + DEFAULT_LOCALE + ".yml");
        if (!defaultFile.exists()) {
            extractDefaultMessages(defaultFile);
        }

        // Load messages from file
        if (defaultFile.exists()) {
            loadMessagesFromFile(defaultFile);
        } else {
            logger.warning("Could not load messages file: " + defaultFile.getPath());
        }

        logger.info("Loaded " + messages.size() + " messages");
    }

    @Override
    public boolean hasMessage(MessageKey key) {
        return messages.containsKey(key.getKey());
    }

    /**
     * Extracts the bundled messages file to the plugin data folder.
     */
    private void extractDefaultMessages(File destination) {
        String resourcePath = MESSAGES_FOLDER + "/messages_" + DEFAULT_LOCALE + ".yml";
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in != null) {
                Files.copy(in, destination.toPath());
                logger.info("Extracted default messages file to " + destination.getPath());
            } else {
                logger.warning("Could not find bundled resource: " + resourcePath);
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to extract messages file", e);
        }
    }

    /**
     * Loads messages from a YAML file, flattening nested keys.
     */
    private void loadMessagesFromFile(File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        loadSection("", config);
    }

    /**
     * Recursively loads messages from a configuration section.
     */
    private void loadSection(String prefix, ConfigurationSection section) {
        for (String key : section.getKeys(false)) {
            String fullKey = prefix.isEmpty() ? key : prefix + "." + key;

            if (section.isConfigurationSection(key)) {
                loadSection(fullKey, section.getConfigurationSection(key));
            } else {
                String value = section.getString(key);
                if (value != null) {
                    messages.put(fullKey, value);
                }
            }
        }
    }

    /**
     * Replaces indexed placeholders {0}, {1}, etc. with provided arguments.
     */
    private String replacePlaceholders(String message, Object... args) {
        if (args == null || args.length == 0) {
            return message;
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(message);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1));
            String replacement = (index < args.length && args[index] != null)
                    ? Matcher.quoteReplacement(String.valueOf(args[index]))
                    : matcher.group(0);
            matcher.appendReplacement(result, replacement);
        }
        matcher.appendTail(result);

        return result.toString();
    }
}
