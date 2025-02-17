/*
 * @(#)ContentRouter.java
 *
 * Copyright 2025 by Bryan (HaiPigGi-StackOverfloweds)
 *
 */
package routing;

import core.*;

import java.util.*;

import routing.community.Duration;
import routing.util.TupleDe;

public class ContentRouter extends ActiveRouter {

    // create some initial variable
    public static final String MESSAGE_TOPICS_S = "topic";
    // create sub topic

    protected Map<DTNHost, Double> startTimestamps;
    protected Map<DTNHost, List<Duration>> connHistory;



    public ContentRouter(Settings s) {
        super(s);
        this.startTimestamps = new HashMap<DTNHost, Double>();
        this.connHistory = new HashMap<DTNHost, List<Duration>>();
    }

    // Copy constructor
    protected ContentRouter(ContentRouter c) {
        super(c);
        startTimestamps = c.startTimestamps;
        connHistory = c.connHistory;
    }


    @Override
    public void changedConnection(Connection con) {
        DTNHost peer = con.getOtherNode(getHost());

        if (con.isUp()) {
            ContentRouter othRouter = (ContentRouter) peer.getRouter();
            this.startTimestamps.put(peer, SimClock.getTime());
            othRouter.startTimestamps.put(getHost(), SimClock.getTime());
        } else {
            if (startTimestamps.containsKey(peer)) {
                double time = startTimestamps.get(peer);
                double etime = SimClock.getTime();

                // Find or create the connection history list
                List<Duration> history;
                if (!connHistory.containsKey(peer)) {
                    history = new LinkedList<>();
                    connHistory.put(peer, history);
                } else history = connHistory.get(peer);

                // add this connection to the list
                if (etime - time > 0) history.add(new Duration(time, etime));

                startTimestamps.remove(peer);
            }
        }
    }

    @Override
    public Message messageTransferred(String id, DTNHost from) {
        // Delete messages from the inbox buffer
        Message incoming = removeFromIncomingBuffer(id, from);

        if (incoming == null) {
            throw new SimError("No message with ID " + id + " in the incoming buffer of " + getHost());
        }

        // Set message reception time
        incoming.setReceiveTime(SimClock.getTime());

        // Application process for incoming messages
        Message outgoing = incoming;
        for (Application app : getApplications(incoming.getAppID())) {
            // The order of applications is important because the output of the previous application is the input of the next.
            outgoing = app.handle(outgoing, getHost());
            if (outgoing == null) {
                break; // The application decides to discard the message
            }
        }

        // Specify the final message after processing
        Message aMessage = (outgoing == null) ? incoming : outgoing;

        // Get the topics from the message
        @SuppressWarnings("unchecked")
        Map<Integer, TupleDe<List<Boolean>, String>> topicMap;
        try {
            topicMap = (Map<Integer, TupleDe<List<Boolean>, String>>) aMessage.getProperty(MESSAGE_TOPICS_S);
        } catch (ClassCastException e) {
            System.out.println("Error: MESSAGE_TOPICS_S property is not of type Map<Integer, TupleDe<List<Boolean>, String>>.");
            return aMessage;
        }

        if (topicMap == null || topicMap.isEmpty()) {
            return aMessage;
        }

        // not finished

        return aMessage;
    }

    protected boolean isSameInterest(Message m, DTNHost host) {
        // Get the topics from the message
        @SuppressWarnings("unchecked")
        Map<TupleDe<String, String>, List<TupleDe<Integer, Integer>>> topicMap = new HashMap<>();
        Map<TupleDe<String, String>, ?> rawSubsMap = (Map<TupleDe<String, String>, ?>) m.getProperty(MESSAGE_TOPICS_S);

        if (rawSubsMap != null) {
            for (Map.Entry<TupleDe<String, String>, ?> entry : rawSubsMap.entrySet()) {
                if (entry.getValue() instanceof List<?>) {
                    topicMap.put(entry.getKey(), (List<TupleDe<Integer, Integer>>) entry.getValue());
                } else {
                    return false;
                }
            }
        }

        if (topicMap.isEmpty()) {
            System.err.println("No topics found in the message for subscription.");
            return false;
        }

        // Get the host's interests
        List<Boolean> topicNode = host.getOwnInterest();

        if (topicNode == null) {
            return false;
        }

        // Iterate through the topic map and check for matches
        for (Map.Entry<TupleDe<String, String>, List<TupleDe<Integer, Integer>>> entry : topicMap.entrySet()) {
            List<TupleDe<Integer, Integer>> topicMsgList = entry.getValue(); // Get the list of attributes

            if (topicMsgList.size() != topicNode.size()) {
                System.out.println("Mismatch in size of message topics and host interests");
                continue; // Skip this topic and check the next one
            }

            // Check if there's any match between the message's topics and the host's interests
            for (int i = 0; i < topicMsgList.size(); i++) {
                TupleDe<Integer, Integer> topicMsg = topicMsgList.get(i);
                if (topicNode.get(i) && topicMsg != null) {
                    return true; // Found a match
                }
            }
        }

        return false; // No match found
    }


    protected List<Double> countInterestTopic(Message m, DTNHost host) {
        boolean isSubscriber = host.isSubscriber();
        if (m.getProperty(MESSAGE_TOPICS_S) == null) {
            return null;
        }

        if (isSubscriber) {
            // Get the topics from the message
            @SuppressWarnings("unchecked")
            Map<Integer, TupleDe<List<Boolean>, String>> topicMap;
            try {
                topicMap = (Map<Integer, TupleDe<List<Boolean>, String>>) m.getProperty(MESSAGE_TOPICS_S);
            } catch (ClassCastException e) {
                System.out.println("Error: MESSAGE_TOPICS_S property is not of type Map<Integer, TupleDe<List<Boolean>, String>>.");
                return null;
            }

            if (topicMap == null || topicMap.isEmpty()) {
                return null;
            }

            // Get the host's interests and weights
            List<Boolean> topicNode = host.getOwnInterest();
            List<Double> weightNode = host.getInterest();

            if (topicNode == null || weightNode == null || topicNode.size() != weightNode.size()) {
                return null;
            }

            // Initialize a list to store the matching weights
            List<Double> valInterest = new ArrayList<>();

            // Iterate through the topic map and check for matches
            for (Map.Entry<Integer, TupleDe<List<Boolean>, String>> entry : topicMap.entrySet()) {
                List<Boolean> topicMsg = entry.getValue().getFirst(); // Extract the List<Boolean> from the TupleDe

                // Ensure both lists have the same size
                if (topicMsg.size() != topicNode.size()) {
                    System.out.println("Mismatch in size of message topics and host interests");
                    continue; // Skip this topic and check the next one
                }

                // Check for matches and add the corresponding weights to valInterest
                for (int i = 0; i < topicMsg.size(); i++) {
                    if (topicMsg.get(i).equals(topicNode.get(i))) {
                        valInterest.add(weightNode.get(i));
                    }
                }
            }

            return valInterest;
        }

        return null;
    }

    @Override
    public void update() {
        super.update();
//        System.out.println("update 1");

        if (isTransferring() || !canStartTransfer()) {
            return; // transferring, don't try other connections yet
        }

        // Try first the messages that can be delivered to final recipient
        if (exchangeDeliverableMessages() != null) {
            return; // started a transfer, don't try others (yet)
        }

        // then try any/all message to any/all connection
        this.tryAllMessagesToAllConnections();
    }



    @Override
    public MessageRouter replicate() {
        return new ContentRouter(this);
    }


}
