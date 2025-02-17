/*
 * @(#)ContentRouter.java
 *
 * Copyright 2025 by Bryan (HaiPigGi-StackOverfloweds)
 *
 */
package routing;

import KDC.Subscriber.BrokerHandler;
import core.*;

import java.util.*;

import routing.community.Duration;
import routing.util.TupleDe;

public class CCDTN extends ActiveRouter {

    // Create some initial variables
    public static final String MESSAGE_TOPICS_S = "topic";
    protected Map<DTNHost, Double> startTimestamps;
    protected Map<DTNHost, List<Duration>> connHistory;

    public CCDTN(Settings s) {
        super(s);
        this.startTimestamps = new HashMap<>();
        this.connHistory = new HashMap<>();
    }

    // Copy constructor
    protected CCDTN(CCDTN c) {
        super(c);
        startTimestamps = new HashMap<>(c.startTimestamps);  // Deep copy of maps
        connHistory = new HashMap<>(c.connHistory);           // Deep copy of maps

    }

    @Override
    public void changedConnection(Connection con) {
        DTNHost peer = con.getOtherNode(getHost());

        if (con.isUp()) {
            CCDTN othRouter = (CCDTN) peer.getRouter();
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


    protected Boolean isFinalDest(Message m, DTNHost host) {
        // Get the topics from the message
        @SuppressWarnings("unchecked")
        Map<Boolean, TupleDe<Integer, String>> topicMap;

        try {
            topicMap = (Map<Boolean, TupleDe<Integer, String>>) m.getProperty(MESSAGE_TOPICS_S);
        } catch (ClassCastException e) {
            System.out.println("Error: MESSAGE_TOPICS_S property is not valid.");
            return null;
        }

        if (topicMap == null || topicMap.isEmpty()) {
            return null;
        }

        // Ensure both lists have the same size
        List<Double> subscriberInterests = host.getInterest();
        if (subscriberInterests == null) {
            return false;
        }
//        System.out.println("Subscriber interests: " + subscriberInterests);
//        System.out.println("topic map size : " + topicMap);
            List<DTNHost> allHosts = SimScenario.getInstance().getHosts();
            for (DTNHost potentialSubscriber : allHosts) {
                if (potentialSubscriber.isSubscriber() && potentialSubscriber.getRouter() instanceof CCDTN) {
                    CCDTN subscriberRouter = (CCDTN) potentialSubscriber.getRouter();
                    // Check if the subscriber has matching interests
                    if (isFinalDestForSubscriber(m, potentialSubscriber)) {
                        subscriberRouter.addToMessages(m, false); // Forward message to subscriber
                    }
                }
            }

        // If host is a subscriber, check if it's the final destination
        boolean isFinal = isFinalDestForSubscriber(m, host);

        if (!isFinal) {
//            System.out.println("Subscriber " + host + " ignored message " + m.getId() + " due to no matching interest.");
            return false;
        }

        return isFinal; // Return true if the subscriber is the final destination
    }


    // Helper method to check if a subscriber matches the message's topics
    private Boolean isFinalDestForSubscriber(Message m, DTNHost subscriber) {
        Object property = m.getProperty(MESSAGE_TOPICS_S);

        // Pastikan `property` memiliki tipe yang diharapkan sebelum casting
        if (!(property instanceof Map)) {
            System.out.println("Error: MESSAGE_TOPICS_S is not a Map.");
            return false;
        }

        @SuppressWarnings("unchecked")
        Map<Boolean, TupleDe<Integer, String>> topicMap = (Map<Boolean, TupleDe<Integer, String>>) property;

        if (topicMap.isEmpty()) {
            System.out.println("Error: topicMap is empty.");
            return false;
        }

        // Ambil interest dari subscriber
        List<Double> subscriberInterests = subscriber.getInterest();
        if (subscriberInterests == null || subscriberInterests.isEmpty()) {
            System.out.println("Error: Subscriber interests are null or empty.");
            return false;
        }

        // Loop melalui setiap entry di topicMap
        for (Map.Entry<Boolean, TupleDe<Integer, String>> entry : topicMap.entrySet()) {
            Boolean topicStatus = entry.getKey(); // True/False menunjukkan apakah topik aktif
            TupleDe<Integer, String> topicData = entry.getValue();
            Integer topicID = topicData.getFirst();
            String topicName = topicData.getSecond();
            // Jika topik tidak aktif, lewati
            if (!topicStatus) {
                continue;
            }

            // Pastikan `topicID` ada dalam batas indeks subscriberInterests
            if (topicID < 0 || topicID >= subscriberInterests.size()) {
                continue;
            }

            // Cek apakah subscriber memiliki minat pada topik ini
            if (subscriberInterests.get(topicID) > 0) {
                System.out.println("Match found for subscriber " + subscriber.getAddress() + " on topic " + topicID);
                return true; // Subscriber cocok dengan topik
            }
        }

        return false; // Tidak ada kecocokan
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
        return new CCDTN(this);
    }


}
