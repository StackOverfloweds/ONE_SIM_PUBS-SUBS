/*
 * Copyright 2024 Bryan (HaiPigGi)
 */
package core;

import java.util.*;

public class Buffer implements Iterable<Message> {
    /**
     * Message buffer size - setting id {@value}
     */
    public static final String B_SIZE_S = "bufferSize";

    /**
     * List to store messages
     */
    private List<Message> messages;

    /**
     * Size of the buffer
     */
    private int bufferSize;

    /**
     * Constructor
     */
    public Buffer() {
        this.messages = new ArrayList<>();
        this.bufferSize = Integer.MAX_VALUE; // Default size
    }

    public int getBufferSize(DTNHost host) {
        bufferSize = Integer.MAX_VALUE;

        Settings s = new Settings(); // Get settings from the router of the host

        if (s.contains(B_SIZE_S)) {
            bufferSize = s.getInt(B_SIZE_S);
        }

        return bufferSize;
    }

    /**
     * Returns an iterator over the messages in this buffer.
     *
     * @return an iterator over the messages in this buffer
     */
    @Override
    public Iterator<Message> iterator() {
        return messages.iterator();
    }

    /**
     * Adds a message to the buffer.
     *
     * @param message The message to add
     */
    public void addMessage(Message message) {
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }
        messages.add(message);
    }

    /**
     * Removes a message from the buffer.
     *
     * @param message The message to remove
     */
    public void removeMessage(Message message) {
        messages.remove(message);
    }
}
