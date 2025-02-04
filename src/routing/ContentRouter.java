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
    protected Map<Boolean, List<TupleDe<TupleDe<Integer, Integer>, String>>> subscribedTopics; //second tuple is for the numeric atribute and string is for flag


    protected boolean isKDC = getHost().isKDC(); // Checking if this host is a KDC
    protected boolean isBroker = getHost().isBroker(); // Checking if this host is a broker
    protected boolean isSubscriber = getHost().isSubscriber();
    protected boolean isPublisher = getHost().isPublisher();


    public ContentRouter(Settings s) {
        super(s);
        this.startTimestamps = new HashMap<DTNHost, Double>();
        this.connHistory = new HashMap<DTNHost, List<Duration>>();
        this.registeredTopics = new HashMap<>();
        this.subscribedTopics = new HashMap<>();

    }

    // Copy constructor
    protected ContentRouter(ContentRouter c) {
        super(c);
        startTimestamps = c.startTimestamps;
        connHistory = c.connHistory;
        registeredTopics = c.registeredTopics;
        subscribedTopics = c.subscribedTopics;
    }

    @Override
    public boolean createNewMessage(Message msg) {
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

            // Add the topic map to the message properties
            msg.addProperty(MESSAGE_TOPICS_S, topic);

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
        Message publisherMessage = null;
        Message subscriberMessage = null;

        if (from.isPublisher()) {
            publisherMessage = (outgoing == null) ? incoming : outgoing;
        } else if (from.isSubscriber()) {
            subscriberMessage = (outgoing == null) ? incoming : outgoing;
        }

        Message aMessage = (outgoing == null) ? incoming : outgoing;

        boolean isFinalRecipient = isFinalDest(aMessage, getHost());
        boolean isFirstDelivery = isFinalRecipient && !isDeliveredMessage(aMessage);
        List<DTNHost> allHost = SimScenario.getInstance().getHosts();

        // If not a broker and not an end recipient, add to the outbound buffer
        if (!isBroker && !isFinalRecipient) {
            addToMessages(aMessage, false);
        }
        if (isPublisher) {
            System.out.println("Publisher");

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
                if (KDC.isKDC() && KDC.getRouter() instanceof ContentRouter) {
                    ContentRouter KDCRouter = (ContentRouter) KDC.getRouter();
                    // Check if the message is from a publisher or subscriber
                    if (from.isPublisher()) {
                        //store to register the topic
                        if (RegisterTopic(aMessage, KDC)) {
                            KDCRouter.addToMessages(aMessage, false);
                        }
                    } else if (from.isSubscriber()) {
                        //subsriber subs for some topic
                        if (subscribeToTopic(aMessage, from, KDC)) {
                            KDCRouter.addToMessages(aMessage, false);
                        }
                    }
                }
            }
        }
        if (isKDC) {
            // if the host is a KDC, first if it get the interest from subscriber with topic and sub topic it will check from the register topic if its have KDC will building NAKT Tree
            for (DTNHost subscriber : allHost) {
                if (subscriber.isSubscriber() && subscriber.getRouter() instanceof ContentRouter) {
                    ContentRouter subscriberRouter = (ContentRouter) subscriber.getRouter();

                }
            }

        }
        if (isSubscriber) {

            for (DTNHost broker : allHost) {
                if (broker.isBroker() && broker.getRouter() instanceof ContentRouter) {
                    ContentRouter brokerRouter = (ContentRouter) broker.getRouter();


                }
            }

        }

        if (isFirstDelivery) {
            this.deliveredMessages.put(id, aMessage);
        }

        // broadcast all messages to all hosts
        for (MessageListener ml : this.mListeners) {
            ml.messageTransferred(aMessage, from, getHost(), isFirstDelivery);
        }
        return aMessage;
    }


    protected boolean RegisterTopic(Message m, DTNHost host) {
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

        // If registration was successful, log success
        System.out.println("Topics successfully registered for host: " + host);

        // If all topics were successfully registered, return true
        return true;
    }

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
        @SuppressWarnings("unchecked") List<Boolean> topicMsg = (List<Boolean>) m.getProperty(MESSAGE_TOPICS_S);
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
 