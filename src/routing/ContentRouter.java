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

    protected Map<DTNHost, Double> startTimestamps;
    protected Map<DTNHost, List<Duration>> connHistory;

    protected Map<Integer, List<TupleDe<List<Boolean>, String>>> registeredTopics;

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
    public boolean createNewMessage(Message msg) {
        if (getHost().isPublisher() || getHost().isBroker() || getHost().isKDC()) {
            if (getHost().isPublisher()) {
                makeRoomForMessage(msg.getSize());

                msg.setTtl(this.msgTtl);
                Map<Integer, TupleDe<List<Boolean>, String>> topic = new HashMap<>();  // Initialize the topic map
                List<Boolean> topicList = new ArrayList<>();  // Create a list to store topic values

                int i = 0;
                while (i < 5) {  // Limit the size to 5 for topics
                    topicList.add(Math.random() < 0.5);  // Add random boolean value for topic
                    i++;
                }
                int subTopic = new Random().nextInt(10);  // Generate a random sub-topic

                // Create a TupleDe to store topic list and associated publisher ID
                String publisherId = String.valueOf(getHost().getRouter().getHost());  // Assuming the host's ID can represent the publisher's ID
                TupleDe<List<Boolean>, String> tuple = new TupleDe<>(topicList, publisherId);
//                System.out.printf("pubisherId: %s\n", publisherId);

                // Put the tuple in the topic map
                topic.put(subTopic, tuple);

                //send it unRegisTopic to KDC to get the key hash for hashing the message throught broker
                if (!topic.isEmpty()) {
                    // register the topic
                    try {
                        msg.addProperty(MESSAGE_TOPICS_S, topic);

                        if (sendToBrokerForRegistration(msg)) {
                            return true;
                        }
                    } catch (NullPointerException e) {
                        System.err.println("Error while processing topic registration: " + e.getMessage());
                        e.printStackTrace();
                        return false;
                    }
                }
            }

            return super.createNewMessage(msg);  // Call the superclass method to handle the message creation
        }
        return false;  // Return false if the host is not a publisher
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


    private boolean sendToBrokerForRegistration(Message message) {
        List<DTNHost> allHosts = SimScenario.getInstance().getHosts();

        @SuppressWarnings("unchecked")
        Map<Integer, TupleDe<List<Boolean>, String>> topicMap = new HashMap<>();
        Map<Integer, ?> rawTopicMap = (Map<Integer, ?>) message.getProperty(MESSAGE_TOPICS_S);
        if (rawTopicMap != null) {
            for (Map.Entry<Integer, ?> entry : rawTopicMap.entrySet()) {
                if (entry.getValue() instanceof TupleDe<?, ?>) {
                    topicMap.put(entry.getKey(), (TupleDe<List<Boolean>, String>) entry.getValue());
                } else {
                    System.err.println("Invalid entry in the topic map for key: " + entry.getKey());
                }
            }
        }

        if (topicMap == null || topicMap.isEmpty()) {
            System.err.println("No topics found in the message for registration.");
            return false;
        }

        for (Map.Entry<Integer, TupleDe<List<Boolean>, String>> entry : topicMap.entrySet()) {
            TupleDe<List<Boolean>, String> tuple = entry.getValue();
            if (tuple == null || tuple.getFirst() == null || tuple.getSecond() == null) {
                System.err.println("Invalid tuple for topic " + entry.getKey());
                return false; // Return false immediately if an invalid tuple is found
            }
        }

        boolean brokerRegistered = false;

        for (DTNHost getHost : allHosts) {
            if (getHost.isBroker() && getHost.getRouter() instanceof ContentRouter) {
                ContentRouter brokerRouter = (ContentRouter) getHost.getRouter();
//                System.out.println("Store to broker: " + brokerRouter.getHost());
                brokerRouter.addToMessages(message, false); // Assume addToMessages is used to store
                //after send it to broker...the broker will bring to kdc to register the topic

                if (forwardToKDC(message, brokerRouter.getHost())) {
                    brokerRegistered = true;
                }
            }
        }

        if (!brokerRegistered) {
            System.err.println("No available broker to handle the registration.");
            return false;
        }

        System.err.println("No available");
        return false;
    }


    protected boolean forwardToKDC(Message message, DTNHost broker) {
        List<DTNHost> allHosts = SimScenario.getInstance().getHosts();

        for (DTNHost host : allHosts) {
            if (host.isKDC() && host.getRouter() instanceof ContentRouter) {
//                ContentRouter kdcRouter = (ContentRouter) host.getRouter();
//                System.out.println("Forwarding to KDC: " + kdcRouter.getHost());

                // Process the topic registration at the KDC
                if (processTopicRegistrationAtKDC(message)) {
                    // Clear the message after processing
                    return true;
                } else { //if its want to subscribe topic

                }
            }
        }

        System.err.println("No available KDC to handle the registration.");
        return false;
    }

    private boolean processTopicRegistrationAtKDC(Message message) {
        @SuppressWarnings("unchecked")
        Map<Integer, TupleDe<List<Boolean>, String>> topicMap = new HashMap<>();
        Map<Integer, ?> rawTopicMap = (Map<Integer, ?>) message.getProperty(MESSAGE_TOPICS_S);
        if (rawTopicMap != null) {
            for (Map.Entry<Integer, ?> entry : rawTopicMap.entrySet()) {
                if (entry.getValue() instanceof TupleDe<?, ?>) {
                    topicMap.put(entry.getKey(), (TupleDe<List<Boolean>, String>) entry.getValue());
                } else {
                    System.err.println("Invalid entry in the topic map for key: " + entry.getKey());
                    return false;
                }
            }
        }

        if (topicMap == null || topicMap.isEmpty()) {
            System.err.println("No topics found in the message for registration.");
            return false;
        }

        for (Map.Entry<Integer, TupleDe<List<Boolean>, String>> entry : topicMap.entrySet()) {
            TupleDe<List<Boolean>, String> tuple = entry.getValue();
            if (tuple == null || tuple.getFirst() == null || tuple.getSecond() == null) {
                System.err.println("Invalid tuple for topic " + entry.getKey());
                return false; // Return false immediately if an invalid tuple is found
            }

            // Store the topic information in the registeredTopics map
            if (registeredTopics == null) {
                registeredTopics = new HashMap<>();
            }
            if (!registeredTopics.containsKey(entry.getKey())) {
                registeredTopics.put(entry.getKey(), new ArrayList<>());
            }
            registeredTopics.get(entry.getKey()).add(new TupleDe<>(tuple.getFirst(), tuple.getSecond()));
        }
//        printRegisteredTopics();
        return true;
    }

//    public void printRegisteredTopics() {
//        if (registeredTopics == null || registeredTopics.isEmpty()) {
//            System.out.println("No topics have been registered yet.");
//            return;
//        }
//
//        System.out.println("Registered Topics:");
//        for (Map.Entry<Integer, List<TupleDe<List<Boolean>, String>>> entry : registeredTopics.entrySet()) {
//            int subTopic = entry.getKey();
//            List<TupleDe<List<Boolean>, String>> topicList = entry.getValue();
//
//            System.out.println("Sub-Topic: " + subTopic);
//            for (TupleDe<List<Boolean>, String> tuple : topicList) {
//                List<Boolean> topicValues = tuple.getFirst();
//                String publisherId = tuple.getSecond();
//
//                System.out.println("  Topic Values: " + topicValues);
//                System.out.println("  Publisher ID: " + publisherId);
//            }
//            System.out.println();
//        }
//    }

    protected DTNHost findBroker () {
        for (Connection c : getConnections()) {
            DTNHost broker = c.getOtherNode(getHost());
            if (broker != null && broker.isBroker()) {
                return broker;

            }
        }
        return null;
    }


//    private Boolean isFinalDest(Message m, DTNHost host) {
//        // Get the topics from the message
//        @SuppressWarnings("unchecked")
//        Map<Integer, TupleDe<List<Boolean>, String>> topicMap;
//        try {
//            topicMap = (Map<Integer, TupleDe<List<Boolean>, String>>) m.getProperty(MESSAGE_TOPICS_S);
//        } catch (ClassCastException e) {
//            System.out.println("Error: MESSAGE_TOPICS_S property is not of type Map<Integer, TupleDe<List<Boolean>, String>>.");
//            return false;
//        }
//
//        if (topicMap == null || topicMap.isEmpty()) {
//            return false;
//        }
//
//        // Ensure both lists have the same size
//        List<Double> subscriberInterests = host.getInterest();
//        if (topicMap.size() != subscriberInterests.size()) {
//            System.out.println("Mismatch in size of message topics and host interests");
//            return false;
//        }
//
//        // If host is a broker, determine relevant subscribers
//        if (host.isBroker()) {
//            List<DTNHost> allHosts = SimScenario.getInstance().getHosts();
//            for (DTNHost potentialSubscriber : allHosts) {
//                if (potentialSubscriber.isSubscriber() && potentialSubscriber.getRouter() instanceof ContentRouter) {
//                    ContentRouter subscriberRouter = (ContentRouter) potentialSubscriber.getRouter();
//
//                    // Check if the subscriber has matching interests
//                    if (isFinalDestForSubscriber(m, potentialSubscriber)) {
//                        subscriberRouter.addToMessages(m, false); // Forward message to subscriber
//                        System.out.println("Broker " + host + " sent message " + m.getId() + " to subscriber " + potentialSubscriber);
//                    }
//                }
//            }
//            return false; // Broker is not a final destination
//        }
//
//        // If host is a subscriber, check if it's the final destination
//        boolean isFinal = isFinalDestForSubscriber(m, host);
//
//        if (!isFinal) {
//            System.out.println("Subscriber " + host + " ignored message " + m.getId() + " due to no matching interest.");
//            return false;
//        }
//
//        return isFinal; // Return true if the subscriber is the final destination
//    }

    // Helper method to check if a subscriber matches the message's topics
    private Boolean isFinalDestForSubscriber(Message m, DTNHost subscriber) {
        @SuppressWarnings("unchecked") List<Boolean> messageTopics = (List<Boolean>) m.getProperty(MESSAGE_TOPICS_S);
        List<Double> subscriberInterests = subscriber.getInterest();

        // Check if there's any match between the message's topics and the subscriber's interests
        for (int i = 0; i < messageTopics.size(); i++) {
            if (messageTopics.get(i) && subscriberInterests.get(i) > 0) {
                return true; // Found a match
            }
        }
        return false; // No match found
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
                    System.err.println("Invalid entry in the subscription map for key: " + entry.getKey());
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
