package org.aincraft.towny.utils;

import org.aincraft.towny.models.BroadcastMessage;

import java.time.format.DateTimeFormatter;

/**
 * Formats {@link BroadcastMessage} instances for player display.
 */
public final class BroadcastFormatter {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("MMM dd, HH:mm");

    private BroadcastFormatter() {
    }

    /**
     * Render a broadcast for chat display, with priority-aware colors and indicators.
     */
    public static String format(BroadcastMessage broadcast) {
        StringBuilder message = new StringBuilder();

        String typeColor = getTypeColor(broadcast.getPriority());
        String priorityIndicator = getPriorityIndicator(broadcast.getPriority());

        switch (broadcast.getMessageType()) {
            case BroadcastMessage.Type.ALERT:
                message.append(typeColor).append("[§cALERT").append(typeColor).append("] ");
                break;
            case BroadcastMessage.Type.ANNOUNCEMENT:
                message.append(typeColor).append("[§eANNOUNCE").append(typeColor).append("] ");
                break;
            case BroadcastMessage.Type.WELCOME:
                message.append(typeColor).append("[§aWELCOME").append(typeColor).append("] ");
                break;
            case BroadcastMessage.Type.WARNING:
                message.append(typeColor).append("[§4WARNING").append(typeColor).append("] ");
                break;
            case BroadcastMessage.Type.CELEBRATION:
                message.append(typeColor).append("[§6CELEBRATE").append(typeColor).append("] ");
                break;
            case BroadcastMessage.Type.ECONOMIC:
                message.append(typeColor).append("[§2ECONOMY").append(typeColor).append("] ");
                break;
            default:
                message.append(typeColor).append("[§fBROADCAST").append(typeColor).append("] ");
        }

        if (broadcast.getPriority() >= BroadcastMessage.Priority.HIGH) {
            message.append(priorityIndicator).append(" ");
        }

        message.append("§f").append(broadcast.getTitle()).append("\n");
        message.append("§7").append(broadcast.getContent()).append("\n");
        message.append("§8- ").append(broadcast.getSenderName())
               .append(" §8(").append(broadcast.getCreatedAt().format(TIMESTAMP))
               .append("§8)");

        return message.toString();
    }

    private static String getTypeColor(int priority) {
        switch (priority) {
            case BroadcastMessage.Priority.CRITICAL:
                return "§4";
            case BroadcastMessage.Priority.URGENT:
                return "§c";
            case BroadcastMessage.Priority.HIGH:
                return "§6";
            case BroadcastMessage.Priority.NORMAL:
                return "§e";
            case BroadcastMessage.Priority.LOW:
                return "§a";
            default:
                return "§f";
        }
    }

    private static String getPriorityIndicator(int priority) {
        switch (priority) {
            case BroadcastMessage.Priority.CRITICAL:
                return "‼";
            case BroadcastMessage.Priority.URGENT:
                return "⚠";
            case BroadcastMessage.Priority.HIGH:
                return "⬆";
            default:
                return "";
        }
    }
}
