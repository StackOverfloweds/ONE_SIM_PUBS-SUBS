package core;

import java.util.List;

public interface MessageLogger {
    void logMessage(Message message);

    List<Message> getMessages();

    List<Message> getMessagesFrom(DTNHost host);

    List<Message> getMessagesTo(DTNHost host);
}
