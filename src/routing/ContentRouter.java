/*
 * @(#)ContentRouter.java
 *
 * Copyright 2025 by Bryan (HaiPigGi-StackOverfloweds)
 *
 */


package routing;

import core.*;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import routing.community.Duration;
import routing.util.TupleDe;

public class ContentRouter extends ActiveRouter {

    // create some initial variable
    public static final String MESSAGE_TOPICS_S = "topic";

    protected Map<DTNHost, Double> startTimestamps;
    protected Map<DTNHost, List<Duration>> connHistory;

    protected Map<Integer, List<TupleDe<List<Boolean>, String>>> registeredTopics;
    protected Map<Boolean, List<TupleDe<TupleDe<Integer, Integer>, String>>> subscribedTopics; //second tuple is for the numeric atribute and string is for flag

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
                System.out.println("Store to broker: " + brokerRouter.getHost());
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

    private boolean forwardToKDC(Message message, DTNHost broker) {
        List<DTNHost> allHosts = SimScenario.getInstance().getHosts();

        for (DTNHost host : allHosts) {
            if (host.isKDC() && host.getRouter() instanceof ContentRouter) {
                ContentRouter kdcRouter = (ContentRouter) host.getRouter();
                System.out.println("Forwarding to KDC: " + kdcRouter.getHost());

                // Process the topic registration at the KDC
                if (processTopicRegistrationAtKDC(message)) {
                    // Clear the message after processing
                    return true;
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
        printRegisteredTopics();
        return true;
    }

    public void printRegisteredTopics() {
        if (registeredTopics == null || registeredTopics.isEmpty()) {
            System.out.println("No topics have been registered yet.");
            return;
        }

        System.out.println("Registered Topics:");
        for (Map.Entry<Integer, List<TupleDe<List<Boolean>, String>>> entry : registeredTopics.entrySet()) {
            int subTopic = entry.getKey();
            List<TupleDe<List<Boolean>, String>> topicList = entry.getValue();

            System.out.println("Sub-Topic: " + subTopic);
            for (TupleDe<List<Boolean>, String> tuple : topicList) {
                List<Boolean> topicValues = tuple.getFirst();
                String publisherId = tuple.getSecond();

                System.out.println("  Topic Values: " + topicValues);
                System.out.println("  Publisher ID: " + publisherId);
            }
            System.out.println();
        }
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
            throw new SimError("No message with ID " + id + " in the incoming " + "buffer of " + getHost());
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

        // message with no register
        Message messageNoRegis = (outgoing == null) ? incoming : outgoing;
        // Specify the final message after processing

        Message aMessage = (outgoing == null) ? incoming : outgoing;
        boolean isKDC = getHost().isKDC(); // Checking if this host is a KDC
        boolean isBroker = getHost().isBroker(); // Checking if this host is a broker
        boolean isSubscriber = getHost().isSubscriber();
        boolean isPublisher = getHost().isPublisher();
//        boolean isFinalRecipient = isFinalDest(aMessage, getHost());
//        boolean isFirstDelivery = isFinalRecipient && !isDeliveredMessage(aMessage);
        List<DTNHost> allHost = SimScenario.getInstance().getHosts();

//        // If not a broker and not an end recipient, add to the outbound buffer
//        if (!isBroker && !isFinalRecipient) {
//            addToMessages(aMessage, false);
//        }
        if (isPublisher) {
            for (DTNHost broker : allHost) {
                if (broker.isBroker() && broker.getRouter() instanceof ContentRouter) {
                    ContentRouter brokerRouter = (ContentRouter) broker.getRouter();
                    // check if the message is not value of hash key encryption

                    // send the topic and sub topic to broker to get a hash key from the KDC
                    brokerRouter.addToMessages(aMessage, false);
                }
            }
        }
        if (isBroker) {
            // if the host is a broker, send it to the KDC to register the topic first
            for (DTNHost KDC : allHost) {
//                if (KDC.isKDC() && KDC.getRouter() instanceof ContentRouter) {
//                    ContentRouter KDCRouter = (ContentRouter) KDC.getRouter();
//                    // Check if the message is from a publisher or subscriber
//                    if (from.isPublisher()) {
//                        //store to register the topic
//                        if (RegisterTopic(aMessage)) {
//                            KDCRouter.addToMessages(aMessage, false);
//                        }
//                    } else if (from.isSubscriber()) {
//                        // check msg is from subs
//                        System.out.println("Message propherty from subscriber : " + aMessage.getProperty(MESSAGE_TOPICS_S));
//                        //subsriber subs for some topic
//                        if (subscribeToTopic(aMessage, from, KDC)) {
//                            KDCRouter.addToMessages(aMessage, false);
//                        }
//                    }
//                }
            }
        }
        if (isKDC) {
            // if the host is a KDC, first if it get the interest from subscriber with topic and sub topic it will check from the register topic if its have KDC will building NAKT Tree
            for (DTNHost broker : allHost) {
                if (broker.isSubscriber() && broker.getRouter() instanceof ContentRouter) {
                    ContentRouter brokerRouter = (ContentRouter) broker.getRouter();
                    if (from.isSubscriber()) {

                    }

                }
            }

        }
        if (isSubscriber) {

            for (DTNHost broker : allHost) {
                if (broker.isBroker() && broker.getRouter() instanceof ContentRouter) {
                    ContentRouter brokerRouter = (ContentRouter) broker.getRouter();
                    brokerRouter.addToMessages(aMessage, false);
                }
            }

        }

//        if (isFirstDelivery) {
//            this.deliveredMessages.put(id, aMessage);
//        }
//
//        // broadcast all messages to all hosts
//        for (MessageListener ml : this.mListeners) {
//            ml.messageTransferred(aMessage, from, getHost(), isFirstDelivery);
//        }
        return aMessage;
    }


    protected boolean isMessageFromPublisherOrSubscriber(Message msg, boolean isPublisherFlag) {
        // Check if the message contains the expected flag for publisher or subscriber
        Boolean messageFlag = (Boolean) msg.getProperty(MESSAGE_TOPICS_S);
        if (messageFlag == null) {
            System.out.println("Flag not found in the message.");
            return false;
        }
        // Return true if the flag matches the expected type (publisher or subscriber)
        return messageFlag.equals(isPublisherFlag);
    }

    // this metode only KDC
    protected boolean RegisterTopic(Message m) {
        // Extract the topic information from the message
        Map<Integer, List<TupleDe<List<Boolean>, String>>> topicMap =
                (Map<Integer, List<TupleDe<List<Boolean>, String>>>) m.getProperty(MESSAGE_TOPICS_S);

        if (topicMap == null) {
            System.out.println("No topics found in the message for registration.");
            return false; // Return false if no topics are present in the message
        }

        // Iterate over each sub-topic in the map
        for (Map.Entry<Integer, List<TupleDe<List<Boolean>, String>>> entry : topicMap.entrySet()) {
            Integer subTopic = entry.getKey();
            List<TupleDe<List<Boolean>, String>> topicData = entry.getValue();

            if (topicData == null || topicData.isEmpty()) {
                System.out.println("SubTopic " + subTopic + " has no data.");
                continue; // Skip if there are no topics to register for this sub-topic
            }

            // Iterate over each topic entry and register it
            for (TupleDe<List<Boolean>, String> tuple : topicData) {
                if (tuple == null || tuple.getFirst() == null || tuple.getSecond() == null) {
                    System.out.println("Invalid topic data in SubTopic " + subTopic);
                    continue; // Skip invalid or null entries
                }

                List<Boolean> topicList = tuple.getFirst();  // Topic boolean values
                String publisherId = tuple.getSecond();      // Publisher ID

                // Check if the topic already exists in the map
                registeredTopics.putIfAbsent(subTopic, new ArrayList<>());

                // Append the new topic data (publisher and topic list) to the existing list for this subTopic
                registeredTopics.get(subTopic).add(new TupleDe<>(topicList, publisherId));
            }
        }

        // If all topics were successfully registered, return true
        return true;
    }

    // this metode only KDC
    protected boolean subscribeToTopic(Message m, DTNHost subscriber, DTNHost KDC) {
        if (KDC == null || !KDC.isKDC()) {
            return false; // Return false if the provided KDC is invalid
        }

        List<Boolean> topicNode = subscriber.getOwnInterest(); // Get subscriber's topic interests
        List<TupleDe<Integer, Integer>> numericAttributes = subscriber.getNumericAtribute(); // Get numeric attributes
        @SuppressWarnings("unchecked")
        List<Boolean> messageTopics = (List<Boolean>) m.getProperty(MESSAGE_TOPICS_S); // Get topics from the message

        if (topicNode == null || numericAttributes == null || messageTopics == null) {
            return false; // Return false if subscriber's interests, attributes, or message topics are null
        }

        // Check registered topics from KDC if they already have the same topics as the subscriber
        for (Map.Entry<Integer, List<TupleDe<List<Boolean>, String>>> entry : registeredTopics.entrySet()) {
            Integer subTopic = entry.getKey();
            List<TupleDe<List<Boolean>, String>> topicData = entry.getValue();

            // Check if the subscriber's topics match any registered topic
            for (TupleDe<List<Boolean>, String> tuple : topicData) {
                List<Boolean> registeredTopicList = tuple.getFirst(); // Registered topic boolean values

                boolean isMatching = true;
                for (int i = 0; i < Math.min(messageTopics.size(), registeredTopicList.size()); i++) {
                    if (messageTopics.get(i) != null && registeredTopicList.get(i) != null
                            && !messageTopics.get(i).equals(registeredTopicList.get(i))) {
                        isMatching = false;
                        break;
                    }
                }

                // If there's a matching topic, prepare and send the subscription
                if (isMatching) {
                    System.out.println("Subscriber matches registered topic for SubTopic: " + subTopic);

                    // Ensure the key `true` exists in the map
                    if (!subscribedTopics.containsKey(true)) {
                        subscribedTopics.put(true, new ArrayList<>());
                    }

                    // Create a TupleDe to store topic list and associated Subscriber ID
                    String SubscriberID = String.valueOf(getHost().getRouter().getHost().isSubscriber());

                    for (TupleDe<Integer, Integer> numericAttribute : numericAttributes) {
                        subscribedTopics.get(true).add(new TupleDe<>(numericAttribute, SubscriberID));
                    }

                    return true;
                }
            }
        }

        return false; // Return false if no matching topic was found
    }


    private Boolean isFinalDest(Message m, DTNHost host) {
        // Get the topics from the message
        @SuppressWarnings("unchecked") List<Boolean> messageTopics = (List<Boolean>) m.getProperty(MESSAGE_TOPICS_S);

        // Ensure the message topics and host's interests are not null
        if (messageTopics == null || host.getInterest() == null) {
            return false; // Return false if the topics or interests are null
        }

        // Ensure both lists have the same size
        List<Double> subscriberInterests = host.getInterest();
        if (messageTopics.size() != subscriberInterests.size()) {
            System.out.println("Mismatch in size of message topics and host interests");
            return false;
        }

        // If host is a broker, determine relevant subscribers
        if (host.isBroker()) {
            List<DTNHost> allHosts = SimScenario.getInstance().getHosts();
            for (DTNHost potentialSubscriber : allHosts) {
                if (potentialSubscriber.isSubscriber() && potentialSubscriber.getRouter() instanceof ContentRouter) {
                    ContentRouter subscriberRouter = (ContentRouter) potentialSubscriber.getRouter();

                    // Check if the subscriber has matching interests
                    if (isFinalDestForSubscriber(m, potentialSubscriber)) {
                        subscriberRouter.addToMessages(m, false); // Forward message to subscriber
                        System.out.println("Broker " + host + " sent message " + m.getId() + " to subscriber " + potentialSubscriber);
                    }
                }
            }
            return false; // Broker is not a final destination
        }

        // If host is a subscriber, check if it's the final destination
        boolean isFinal = isFinalDestForSubscriber(m, host);

        if (!isFinal) {
            System.out.println("Subscriber " + host + " ignored message " + m.getId() + " due to no matching interest.");
            return false;
        }

        return isFinal; // Return true if the subscriber is the final destination
    }

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
        List<Boolean> topicMsg;
        try {
            topicMsg = (List<Boolean>) m.getProperty(MESSAGE_TOPICS_S);
        } catch (ClassCastException e) {
            System.out.println("Error: MESSAGE_TOPICS_S property is not a List<Boolean>.");
            return false;
        }

        if (topicMsg == null) {
            return false;
        }

        // Get the host's interests
        List<Boolean> topicNode = host.getOwnInterest();

        if (topicNode == null) {
            return false;
        }

        // Ensure both lists have the same size
        if (topicMsg.size() != topicNode.size()) {
            System.out.println("Mismatch in size of message topics and host interests");
            return false; // Or handle this case as needed
        }

        // Check if there's any match between the message's topics and the host's interests
        for (int i = 0; i < topicMsg.size(); i++) {
            if (topicMsg.get(i).equals(topicNode.get(i))) {
                return true; // Found a match
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
            List<Boolean> topicMSG = (ArrayList) m.getProperty(MESSAGE_TOPICS_S);
            List<Boolean> topicNode = host.getOwnInterest();
            List<Double> weightNode = host.getInterest();

            List<Double> valInterest = new ArrayList<>();
            Iterator<Boolean> itTop = topicMSG.iterator();

            int i = 0;

            while (itTop.hasNext()) {
                if (itTop.next().equals(topicNode.get(i))) {
                    valInterest.add(weightNode.get(i));
                }
            }
            return valInterest;
        }
        return null;
    }

    //metode to get the interest from the host and message
    protected List<TupleDe<Integer, Integer>> extractMinMaxValueSubs(Message m, DTNHost host) {
        boolean isSubscriber = host.isSubscriber();
        if (m.getProperty(MESSAGE_TOPICS_S) == null) {
            return null;
        }
        if (isSubscriber) {
            List<TupleDe<Integer, Integer>> getNumericAttribute = host.getNumericAtribute(); // New method to subscribe to topics from Subscriber to KDC
            List<Boolean> topicMSG = (ArrayList<Boolean>) m.getProperty(MESSAGE_TOPICS_S);
            List<Boolean> topicNode = host.getOwnInterest();

            List<TupleDe<Integer, Integer>> minMaxValues = new ArrayList<>();

            for (int i = 0; i < topicMSG.size(); i++) {
                if (topicMSG.get(i).equals(topicNode.get(i))) {
                    minMaxValues.add(getNumericAttribute.get(i));
                }
            }

            return minMaxValues;
        }
        return null;
    }


    // Utility class for hashing and encryption
    static class HashingUtility {

        public static String hash(String input) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] hashBytes = md.digest(input.getBytes());

                StringBuilder sb = new StringBuilder();
                for (byte b : hashBytes) {
                    sb.append(String.format("%02x", b));
                }
                return sb.toString();
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("Hashing algorithm not found", e);
            }
        }

        public static String encryptMessage(String hash, String message) {
            // Simple encryption logic (for demonstration)
            return hash + "|" + message;
        }
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
