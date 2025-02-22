package input;

import core.Settings;

/**
 * Extended MessageEvent for NAKT.
 * Supports three message types: Register (R), Subscribe (S), and Message (M).
 */
public class MessageEventGeneratorNAKT extends MessageEvent {
    /** Enum for message types */
    public enum MessageType {
        REGISTER, SUBSCRIBE, MESSAGE
    }

    private static int globalIdCounter = 0; // Global ID counter
    private static final String DEFAULT_PREFIX = "M"; // Default prefix

    /**
     * Constructor for MessageEventGeneratorNAKT
     * @param from Address of the sender node
     * @param to Address of the receiver node
     * @param s Settings to determine the message type
     * @param time Time when the message event occurs
     */
    public MessageEventGeneratorNAKT(int from, int to, Settings s, double time) {
        super(from, to, generateID(s), time);
    }

    /**
     * Generates a new message ID with the appropriate type prefix.
     * @param s Settings to determine the message prefix
     * @return Formatted message ID (e.g., R1, S2, M3)
     */
    private static String generateID(Settings s) {
        // Cek apakah MESSAGE_ID_PREFIX_S ada di Settings, jika tidak gunakan default "M"
        String prefix = s.contains(MessageEventGenerator.MESSAGE_ID_PREFIX_S)
                ? s.getSetting(MessageEventGenerator.MESSAGE_ID_PREFIX_S)
                : DEFAULT_PREFIX;

        if (prefix.equalsIgnoreCase("R")) {
            prefix = "R";
        } else if (prefix.equalsIgnoreCase("S")) {
            prefix = "S";
        } else {
            prefix = DEFAULT_PREFIX;
        }

        return prefix + (++globalIdCounter); // Unique ID
    }

    @Override
    public String toString() {
        return "MessageEventGeneratorNAKT: " + id + " from " + fromAddr + " to " + toAddr + " at " + time;
    }
}
