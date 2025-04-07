/*
 * @(#)PublishAndSubscriberRouting.java
 *
 * Copyright 2025 by Bryan (HaiPigGi-StackOverfloweds)
 *
 */
package routing;

import core.*;
import routing.KDC.NAKT.KDCLoad;
import routing.KDC.Publisher.EncryptionUtil;
import routing.KDC.Subscriber.KeySubscriber;
import routing.util.TupleDe;

import java.util.*;

/**
 * PublishAndSubscriberRouting implements a topic-based Publish-Subscribe routing mechanism
 * for Delay-Tolerant Networks (DTNs). It manages secure encryption, subscriber authentication,
 * and message forwarding using Numeric Attribute Key Trees (NAKT).
 */
public class PublishAndSubscriberRouting extends CCDTN implements KeySubscriber, KDCLoad {
    // Namespace settings
    private static final String PUBSROUTING_NS = "PublishAndSubscriberRouting";

    /**
     * Constructor: Initializes PublishAndSubscriberRouting with settings, topic registration,
     * encryption, and subscriber authentication details.
     *
     * @param s Settings object for configuring the routing mechanism.
     */
    public PublishAndSubscriberRouting(Settings s) {
        // Call the superclass constructor to initialize inherited fields
        super(s);
        Settings ccSettings = new Settings(PUBSROUTING_NS);
    }

    /**
     * Copy Constructor: Creates a deep copy of an existing PublishAndSubscriberRouting instance.
     *
     * @param r The instance to be copied.
     */
    protected PublishAndSubscriberRouting(PublishAndSubscriberRouting r) {
        // Call the superclass copy constructor
        super(r);
    }

    /**
     * Creates multiple new messages to be published.
     * Ensures that the host is registered and encrypts the messages
     * before adding them to the message queue.
     *
     * @param msg The message object to be created.
     * @return true if at least one message is successfully created, false otherwise.
     */
    @Override
    public boolean createNewMessage(Message msg) {
        // Ambil properti pesan
        Map<DTNHost, TupleDe<String, String>> getKeyEnc =
                (Map<DTNHost, TupleDe<String, String>>) msg.getProperty(MESSAGE_KEY_ENCRYPTION_S);

        Map<DTNHost, List<TupleDe<Boolean, Integer>>> getTopPubs =
                (Map<DTNHost, List<TupleDe<Boolean, Integer>>>) msg.getProperty(MESSAGE_REGISTER_S);

        // Validasi data
        if (getKeyEnc == null || getKeyEnc.isEmpty() || getTopPubs == null || getTopPubs.isEmpty()) {
            return false;
        }

        boolean success = false;

        for (Map.Entry<DTNHost, List<TupleDe<Boolean, Integer>>> entryTop : getTopPubs.entrySet()) {
            DTNHost pubsId = entryTop.getKey();
            List<TupleDe<Boolean, Integer>> values = entryTop.getValue();

            // 🛑 Cek apakah list values kosong
            if (values == null || values.isEmpty()) {
                continue;
            }

            TupleDe<Boolean, Integer> topPub = values.get(0); // topic sub-topic publisher

            // Ambil langsung sebagai TupleDe
            TupleDe<String, String> keyPub = getKeyEnc.get(pubsId);

            if (keyPub == null) {
                continue;
            }

//            System.out.println("pub id : " + pubsId);

            // Generate random message
            String randomMessage = "abcdefghijABCDEFGHIJ"; // 20 karakter
            String hashedMessage = EncryptionUtil.encryptMessage(randomMessage, keyPub.getSecond());

            Map<Boolean, TupleDe<Integer, String>> messageData = new HashMap<>();
            messageData.put(topPub.getFirst(), new TupleDe<>(topPub.getSecond(), hashedMessage));

//            System.out.println("get msg before encryption: " + randomMessage);
//            System.out.println("get key encryption: " + keyPub.getSecond());
//            System.out.println("get msg after encryption: " + hashedMessage);

            makeRoomForMessage(msg.getSize());
            msg.setTtl(this.msgTtl);
            msg.addProperty(MESSAGE_TOPICS_S, messageData);
            addToMessages(msg, true); // new msg add to buffer
            if (sendForPublishing(msg)) {
//                System.out.println("success create msg with encrypt" + msg.getProperty(MESSAGE_TOPICS_S));
                success = true;
            }
        }

        return success;
    }


    private boolean sendForPublishing(Message msg) {
        // Get all connections
        Collection<Connection> connections = getConnections();
        if (connections == null) {
            System.err.println("Error: getConnections() is null!");
            return false;
        }

        // Get the host
        DTNHost host = getHost();
        if (host == null) {
            System.err.println("Error: getHost() is null!");
            return false;
        }
        List<DTNHost> brokerHosts = getAllBroker.getAllBrokers();

        // Iterate through all connections
        for (Connection con : connections) {
            DTNHost other = con.getOtherNode(host);
            PublishAndSubscriberRouting othRouter = (PublishAndSubscriberRouting) other.getRouter();
            if (othRouter.isTransferring()) {
                continue;
            }

            if (other.isBroker()) {
                brokerHosts.add(other);
            }

        }

        // Jika tidak ada broker, hentikan proses
        if (brokerHosts.isEmpty()) {
            return false;
        }
        // Kirim pesan ke semua broker
        for (DTNHost broker : brokerHosts) {
            if (broker.isBroker()) {
                addToMessages(msg, false); // send to broker for msg from publisher
            }
        }
        return true;
    }


    /**
     * Comparator for sorting messages based on the highest interest similarity.
     * Messages with a higher interest similarity will be prioritized (sorted in descending order).
     */
    private class InterestSimilarityComparator implements Comparator<Tuple<Message, Connection>> {
        @Override
        public int compare(Tuple<Message, Connection> tuple1, Tuple<Message, Connection> tuple2) {
            double d1 = sumList(countInterestTopic(tuple1.getKey(), tuple1.getValue().getOtherNode(getHost())));
            double d2 = sumList(countInterestTopic(tuple2.getKey(), tuple2.getValue().getOtherNode(getHost())));

            return Double.compare(d2, d1);
        }
    }

    /**
     * Helper method to sum the values in a list of interest similarity scores.
     *
     * @param lists A list of interest similarity values.
     * @return The total sum of all values in the list.
     */
    private double sumList(List<Double> lists) {
        double total = 0.0;
        for (double lst : lists) {
            total += lst;
        }
        return total;
    }

    /**
     * The main update method that gets called periodically.
     * - Checks if the router is currently transferring data or cannot start a new transfer.
     * - Tries to deliver messages to final recipients first.
     * - If no final recipient is found, attempts to transfer messages to other connected nodes.
     */
    @Override
    public void update() {
        super.update();
        // Jika sedang melakukan transfer atau tidak bisa memulai transfer, keluar
        if (isTransferring() || !canStartTransfer()) {
            return;
        }

        // Coba kirim pesan ke penerima akhir
        if (exchangeDeliverableMessages() != null) {
            return; // Jika berhasil transfer, hentikan proses selanjutnya
        }

        tryOtherMessages();

    }


    /**
     * Attempts to transfer messages to other nodes.
     * - Scans all connections for potential recipients.
     * - Checks if the other node is a subscriber.
     * - Sorts messages and attempts to transfer them.
     *
     * @return The message that was successfully transferred, or null if no transfer occurred.
     */
    private Tuple<Message, Connection> tryOtherMessages() {
        List<Tuple<Message, Connection>> messages = new ArrayList<>();

        // Get all connections
        Collection<Connection> connections = getConnections();
        if (connections == null) {
            return null;
        }

        // Get the host
        DTNHost host = getHost();
        if (host == null) {
            return null;
        }

        // Get the message collection
        Collection<Message> msgCollection = getMessageCollection();
        if (msgCollection.isEmpty()) {
            return null;
        }

        // Iterate through all connections
        for (Connection con : connections) {
            DTNHost other = con.getOtherNode(host);
            PublishAndSubscriberRouting othRouter = (PublishAndSubscriberRouting) other.getRouter();
            if (othRouter.isTransferring()) {
                continue;
            }

            // Iterate through the message collection
            for (Message msg : msgCollection) {
                if (msg == null) {
                    continue;
                }

                if (othRouter.hasMessage(msg.getId())) {
                    continue; // skip messages that the other one has
                }


                if (isSameInterest(msg, other)) {
                    messages.add(new Tuple<>(msg, con));
                }

            }
        }

        // Sort messages based on interest similarity
        messages.sort(new InterestSimilarityComparator());

        // If no messages are found, return null
        if (messages.isEmpty()) {
            return null;
        }

        // Try to transfer the messages
        return tryMessagesForConnected(messages);
    }


    /**
     * Creates a duplicate of the current router instance.
     *
     * @return A new instance of PublishAndSubscriberRouting with the same properties.
     */
    @Override
    public MessageRouter replicate() {
        return new PublishAndSubscriberRouting(this);
    }

//    public Map<DTNHost, Integer> getKeys() {
//        // Get the messages held by this host
//        Collection<Message> msgCollection = getMessageCollection();
//        if (msgCollection.isEmpty()) {
//            return Collections.emptyMap(); // Return empty map if no messages
//        }
//
//        // Get all current connections
//        Collection<Connection> connections = getConnections();
//        if (connections == null) {
//            return Collections.emptyMap();
//        }
//
//        // Map to store the total number of unique keys per subscriber
//        Map<DTNHost, Integer> keyCounts = new HashMap<>();
//
//        // Map to track the unique keys already seen for each subscriber
//        Map<DTNHost, Set<String>> seenKeys = new HashMap<>();
//
//        // Loop through each connection
//        for (Connection con : connections) {
//            DTNHost other = con.getOtherNode(getHost());
//            PublishAndSubscriberRouting otherRouter = (PublishAndSubscriberRouting) other.getRouter();
//
//            // Skip if the node is currently transferring
//            if (otherRouter.isTransferring()) {
//                continue;
//            }
//
//            // Skip if the other node is not a subscriber
//            if (!other.isSubscriber()) {
//                continue;
//            }
//
//            // Loop through each message
//            for (Message msg : msgCollection) {
//                if (msg == null) {
//                    continue;
//                }
//
//                // Extract the message key authentication property
//                Map<DTNHost, List<TupleDe<String, String>>> keyAuth =
//                        (Map<DTNHost, List<TupleDe<String, String>>>) msg.getProperty(MESSAGE_KEY_AUTHENTICATION_S);
//
//                if (keyAuth != null) {
//                    for (Map.Entry<DTNHost, List<TupleDe<String, String>>> entry : keyAuth.entrySet()) {
//                        DTNHost subscriber = entry.getKey();
//                        List<TupleDe<String, String>> tuples = entry.getValue();
//
//                        if (tuples != null) {
//                            // Get or create the set of keys already seen for this subscriber
//                            Set<String> uniqueKeys;
//                            if (seenKeys.containsKey(subscriber)) {
//                                uniqueKeys = seenKeys.get(subscriber);
//                            } else {
//                                uniqueKeys = new HashSet<>();
//                                seenKeys.put(subscriber, uniqueKeys);
//                            }
//
//                            int newKeyCount = 0; // Count of new unique keys in this message
//
//                            for (TupleDe<String, String> tuple : tuples) {
//                                String key = tuple.getSecond(); // Get the key string
//
//                                // Add key only if it's not seen before
//                                if (!uniqueKeys.contains(key)) {
//                                    uniqueKeys.add(key);
//                                    newKeyCount++;
//                                }
//                            }
//
//                            // Update the count of keys for the subscriber
//                            if (newKeyCount > 0) {
//                                int currentCount = keyCounts.getOrDefault(subscriber, 0);
//                                keyCounts.put(subscriber, currentCount + newKeyCount);
//                            }
//                        }
//                    }
//                }
//            }
//        }
//
//        return keyCounts;
//    }

    public Map<DTNHost, Integer> getKeys() {
        Collection<Message> msgCollection = getMessageCollection();
        if (msgCollection.isEmpty()) {
            return Collections.emptyMap();
        }

        Collection<Connection> connections = getConnections();
        if (connections == null) {
            return Collections.emptyMap();
        }

        Map<DTNHost, Integer> keyCounts = new HashMap<>();

        // Map to track the second values seen per subscriber (DTNHost)
        Map<DTNHost, Set<String>> seenKeys = new HashMap<>();

        for (Message msg : msgCollection) {
            if (msg == null) continue;

            Map<DTNHost, List<TupleDe<String, String>>> keyAuth =
                    (Map<DTNHost, List<TupleDe<String, String>>>) msg.getProperty(MESSAGE_KEY_AUTHENTICATION_S);

            if (keyAuth != null) {
                for (Map.Entry<DTNHost, List<TupleDe<String, String>>> entry : keyAuth.entrySet()) {
                    DTNHost subscriber = entry.getKey();
                    List<TupleDe<String, String>> tuples = entry.getValue();

                    if (tuples != null) {
                        for (TupleDe<String, String> tuple : tuples) {
                            String secondValue = tuple.getSecond();
                            if (secondValue == null) continue;

                            // Get or create the set of seen second values for this subscriber
                            Set<String> keysSeenForHost;
                            if (seenKeys.containsKey(subscriber)) {
                                keysSeenForHost = seenKeys.get(subscriber);
                            } else {
                                keysSeenForHost = new HashSet<>();
                                seenKeys.put(subscriber, keysSeenForHost);
                            }

                            // Skip if this host already used this secondValue
                            if (keysSeenForHost.contains(secondValue)) {
                                continue;
                            }

                            // Otherwise, count and add
                            keysSeenForHost.add(secondValue);
                            keyCounts.put(subscriber, keyCounts.getOrDefault(subscriber, 0) + 1);
                        }

                    }
                }
            }
        }

        return keyCounts;
    }



    public Map<DTNHost, Integer> getKDCLoad() {
        if (kdcLoad == null || kdcLoad.isEmpty()) {
            return Collections.emptyMap(); // Hindari return null
        }

        Map<DTNHost, Integer> computedKDCLoad = new HashMap<>();

        for (Map.Entry<DTNHost, Integer> entry : kdcLoad.entrySet()) {
            DTNHost kdcHost = entry.getKey();
            Integer loadCount = (entry.getValue() != null) ? entry.getValue() : 0;

            computedKDCLoad.put(kdcHost, loadCount);
        }

        return computedKDCLoad;
    }

}
