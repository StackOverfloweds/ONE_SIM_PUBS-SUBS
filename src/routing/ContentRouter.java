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

    protected Map<Integer, List<TupleDe<List<Boolean>, String>>> registeredTopics = new HashMap<>();


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

        // Specify the final message after processing
        Message aMessage = (outgoing == null) ? incoming : outgoing;

        boolean isKDC = getHost().isKDC(); // Checking if this host is a KDC
        boolean isBroker = getHost().isBroker(); // Checking if this host is a broker
        boolean isSubscriber = getHost().isSubscriber();
        boolean isFinalRecipient = isFinalDest(aMessage, getHost());
        boolean isFirstDelivery = isFinalRecipient && !isDeliveredMessage(aMessage);
        List<DTNHost> allHost = SimScenario.getInstance().getHosts();
        // If not a broker and not an end recipient, add to the outbound buffer
        if (!isBroker && !isFinalRecipient) {
            addToMessages(aMessage, false);
        }

        // Jika host adalah Publisher
        if (getHost().isPublisher()) {
            System.out.println("Publisher");

            // Kirimkan pesan ke Broker

            for (DTNHost broker : allHost) {
                if (broker.isBroker() && broker.getRouter() instanceof ContentRouter) {
                    ContentRouter brokerRouter = (ContentRouter) broker.getRouter();

                    // Kirimkan pesan ke Broker untuk memverifikasi topik dan subtopik
                    brokerRouter.addToMessages(aMessage, false);
                }
            }

        } else if (isBroker) {
            // if the host is a broker, send it to the KDC to register the topic first
            for (DTNHost KDC : allHost) {
                if (KDC.isKDC() && KDC.getRouter() instanceof ContentRouter) {
                    ContentRouter KDCRouter = (ContentRouter) KDC.getRouter();
                    if (RegisterTopic(aMessage, KDC)) {
                            KDCRouter.addToMessages(aMessage, false);
                    }
                }
                continue;
            }
        } else if (isKDC) {
            // if the host is a KDC, first if it get the interest from subscriber with topic and sub topic it will check from the register topic if its have KDC will building NAKT Tree
            for (DTNHost subscriber : allHost) {
                if (subscriber.isSubscriber() && subscriber.getRouter() instanceof ContentRouter) {
                    ContentRouter subscriberRouter = (ContentRouter) subscriber.getRouter();

                }
            }


        } else if (isSubscriber) {

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

        // Iterate over each sub-topic in the map
        for (Map.Entry<Integer, List<TupleDe<List<Boolean>, String>>> entry : topicMap.entrySet()) {
            Integer subTopic = entry.getKey();
            List<TupleDe<List<Boolean>, String>> topicData = entry.getValue();

            // Iterate over each topic entry and register it
            for (TupleDe<List<Boolean>, String> tuple : topicData) {
                List<Boolean> topicList = tuple.getFirst();  // Topic boolean values
                String publisherId = tuple.getSecond();  // Publisher ID

                // Log the registration process
                System.out.println("Registering topic with SubTopic: " + subTopic + " for Publisher: " + publisherId);

                // Check if the topic already exists in the map
                if (!registeredTopics.containsKey(subTopic)) {
                    // If not, create a new list for this subTopic
                    registeredTopics.put(subTopic, new ArrayList<>());
                }

                // Append the new topic data (publisher and topic list) to the existing list for this subTopic
                registeredTopics.get(subTopic).add(new TupleDe<>(topicList, publisherId));
            }
        }

        // If all topics were successfully registered, return true
        return true;
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
        System.out.println("test");
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
 