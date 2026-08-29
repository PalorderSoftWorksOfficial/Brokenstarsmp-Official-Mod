package com.palordersoftworks.brokenstarsmpmod.messages;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.Map;

public final class MiniMessageApi {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final String PLACEHOLDER_PREFIX = "bsmp_placeholder_";

    private MiniMessageApi() {
    }

    public static Component parse(String message) {
        return MINI_MESSAGE.deserialize(message);
    }

    public static Component parse(String message, Map<String, String> placeholders) {
        String resolvedMessage = message;
        TagResolver.Builder resolverBuilder = TagResolver.builder();

        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String tag = PLACEHOLDER_PREFIX + sanitize(entry.getKey());
            resolvedMessage = resolvedMessage.replace("${" + entry.getKey() + "}", "<" + tag + ">");
            resolverBuilder.resolver(Placeholder.unparsed(tag, entry.getValue()));
        }

        return MINI_MESSAGE.deserialize(resolvedMessage, resolverBuilder.build());
    }

    private static String sanitize(String value) {
        StringBuilder result = new StringBuilder(value.length());

        for (char character : value.toLowerCase().toCharArray()) {
            if ((character >= 'a' && character <= 'z')
                    || (character >= '0' && character <= '9')
                    || character == '_'
                    || character == '-') {
                result.append(character);
            } else {
                result.append('_');
            }
        }

        return result.toString();
    }
}
