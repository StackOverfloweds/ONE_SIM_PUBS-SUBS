/*
 * @(#)PublishAndSubscriberRouting.java
 *
 * Copyright 2025 by Bryan (HaiPigGi-StackOverfloweds)
 *
 */
package routing;
import KDC.Publisher.BrokerRegistrationHandler;
import KDC.Publisher.EncryptionUtil;
import KDC.Publisher.KDCRegistrationProcessor;
import KDC.Subscriber.BrokerHandler;
import KDC.Subscriber.SubscriptionManager;
import core.*;
import routing.util.TupleDe;
import java.util.*;


public class PublishAndSubscriberRouting extends ContentRouter {

    public static Map<TupleDe<String, List<Boolean>>, List<TupleDe<Integer, Integer>>> subscribedTopics; // key for the topic and id of subscriber, value is for list of numeric atribute
    public static Map<Integer, List<TupleDe<Boolean, String>>> registeredTopics;
    public static Map<String, List<TupleDe<String, String>>> keyEncryption;
    public static Map<String, List<TupleDe<String, String>>> keyAuthentication;


    public static int lcnum;
    private boolean topicVall;
    private int subTopicVall;

    public KDCRegistrationProcessor processor = new KDCRegistrationProcessor();
    public BrokerHandler brokerHandler = new BrokerHandler();

    /**
     * namespace settings ({@value})
     */
    private static final String PUBSROUTING_NS = "PublishAndSubscriberRouting";
    protected static final String LCNUM = "LCNUM";

    public PublishAndSubscriberRouting(Settings s) {
        // Call the superclass constructor to initialize inherited fields
        super(s);
        Settings ccSettings = new Settings(PUBSROUTING_NS);
        lcnum = ccSettings.getInt(LCNUM);
        subscribedTopics = new HashMap<>();

        if (keyEncryption == null) {
            keyEncryption = new HashMap<>();
        } else {
            keyEncryption =brokerHandler.getKeyEncryption();
            for (Map.Entry<String, List<TupleDe<String, String>>> entry : keyEncryption.entrySet()) {
                List<TupleDe<String, String>> values = entry.getValue();
                keyEncryption.put(entry.getKey(), values);
            }
        }

        // for key Auth
        if (keyAuthentication == null) {
            keyAuthentication = new HashMap<>();
        } else {
            keyAuthentication =brokerHandler.getKeyAuthentication();

            for (Map.Entry<String, List<TupleDe<String, String>>> entry : keyAuthentication.entrySet()) {
                List<TupleDe<String, String>> values = entry.getValue();
                keyEncryption.put(entry.getKey(), values);
            }
        }

        registeredTopics = processor.getRegisteredTopics();

        if (registeredTopics == null) {
            registeredTopics = new HashMap<>();
        } else {
            registeredTopics = processor.getRegisteredTopics();

            for (Map.Entry<Integer, List<TupleDe<Boolean, String>>> entry : registeredTopics.entrySet()) {
                List<TupleDe<Boolean, String>> list = entry.getValue();
                registeredTopics.put(entry.getKey(), list);
            }
        }
    }

    protected PublishAndSubscriberRouting(PublishAndSubscriberRouting r) {
        // Call the superclass copy constructor
        super(r);
        lcnum = r.lcnum;
        subscribedTopics = new HashMap<>(r.subscribedTopics);
        keyEncryption = new HashMap<>(r.keyEncryption);
        keyAuthentication = new HashMap<>(r.keyAuthentication);

        // Initialize registeredTopics as a new HashMap if it is null in the original object
        if (r.registeredTopics == null) {
            registeredTopics = new HashMap<>();
        } else {
            registeredTopics = new HashMap<>(r.registeredTopics);

            // Copy each entry in registeredTopics to the new map
            for (Map.Entry<Integer, List<TupleDe<Boolean, String>>> entry : r.registeredTopics.entrySet()) {
                List<TupleDe<Boolean, String>> valueCopy = new ArrayList<>(entry.getValue());
                registeredTopics.put(entry.getKey(), valueCopy);
            }
        }
    }

    @Override
    public void changedConnection(Connection con) {
        super.changedConnection(con);
        DTNHost host = getHost();
        DTNHost otherNode = con.getOtherNode(host);
        topicVall = otherNode.getTopicValue();
        subTopicVall = otherNode.getSubTopic();
        if (con.isUp()) {
            // Connection is up
            // for pubs
            BrokerRegistrationHandler brokerHandler = new BrokerRegistrationHandler(host);
            brokerHandler.sendToBrokerForRegistration(topicVall, subTopicVall);
            SubscriptionManager subscriptionManager = new SubscriptionManager(otherNode);
            subscriptionManager.interestCheck(otherNode);
        }
    }

    protected Map<Integer, List<TupleDe<Boolean, String>>> getRegisteredTopics() {
        // Ensure we return an empty map instead of null
        return this.registeredTopics != null ? this.registeredTopics : new HashMap<>();
    }

    @Override
    public boolean createNewMessage(Message msg) {
        List<DTNHost> allHosts = SimScenario.getInstance().getHosts();

        if (keyEncryption.isEmpty() || allHosts == null) {
            return false;
        }

        for (DTNHost host : allHosts) {
            String hostId = String.valueOf(host.getRouter().getHost());

            // Cari host yang sesuai dengan hostId
            if (!keyEncryption.containsKey(hostId)) {
                continue;  // Lewati jika tidak cocok
            }

            List<TupleDe<String, String>> keys = keyEncryption.get(hostId);

            if (keys == null || keys.isEmpty()) {  // Pastikan list tidak kosong
                continue;
            }

            // Ambil topic dan subTopic dari host yang cocok
            if (hostId.equals(String.valueOf(getHost().getRouter().getHost()))) {
                topicVall = host.getTopicValue();
                subTopicVall = host.getSubTopic();
            }
//
            System.out.println("topicVall: " + topicVall);
            System.out.println("subTopicVall: " + subTopicVall);

            String randomMessage = EncryptionUtil.generateRandomString(20); // 20 karakter

            String keyEncrypt = keys.get(0).getSecond(); // Gunakan key dari Tuple

            String hashedMessage = EncryptionUtil.hashWithHmacSHA256(randomMessage, keyEncrypt);

            Map<String, Object> messageData = new HashMap<>();
            messageData.put("topic", topicVall);
            messageData.put("subTopic", subTopicVall);
            messageData.put("msg", hashedMessage);

            // **5. Tambahkan ke properti message**
            makeRoomForMessage(msg.getSize());
            msg.setTtl(this.msgTtl);
            msg.addProperty(MESSAGE_TOPICS_S, messageData);
            System.out.println("Success to send message : " + msg.getProperty(MESSAGE_TOPICS_S));
            return super.createNewMessage(msg);
        }
        return false;
    }


    /**
     * Comparator untuk sorting message berdasarkan
     * Interest Similarity tertinggi
     * Sort DESC
     */
    private class InterestSimilarityComparator implements Comparator<Tuple<Message, Connection>> {
        @Override
        public int compare(Tuple<Message, Connection> tuple1, Tuple<Message, Connection> tuple2) {
            double d1 = sumList(countInterestTopic(tuple1.getKey(), tuple1.getValue().getOtherNode(getHost())));
            double d2 = sumList(countInterestTopic(tuple2.getKey(), tuple2.getValue().getOtherNode(getHost())));

            return Double.compare(d2, d1);
        }
    }

    // Helper method to sum list of interests (probabilities or matching scores)
    private double sumList(List<Double> lists) {
        double total = 0.0;
        for (double lst : lists) {
            total += lst;
        }
        return total;
    }


    @Override
    public void update() {
        super.update();

        // If transferring or cannot start transfer, skip further processing
        if (isTransferring() || !canStartTransfer()) {
            return;
        }

        // Try to deliver messages to final recipients
        if (exchangeDeliverableMessages() != null) {
            return; // Started a transfer, don't try others yet
        }

        // Try other messages for transfer
        tryOtherMessages();
    }


    private Tuple<Message, Connection> tryOtherMessages() {
        List<Tuple<Message, Connection>> messages = new ArrayList<>();

        // Get all connections
        Collection<Connection> connections = getConnections();
        if (connections == null) {
            System.err.println("Error: getConnections() is null!");
            return null;
        }

        // Get the host
        DTNHost host = getHost();
        if (host == null) {
            System.err.println("Error: getHost() is null!");
            return null;
        }

        // Get the message collection
        Collection<Message> msgCollection = getMessageCollection();
        if (msgCollection == null || msgCollection.isEmpty()) {
            System.err.println("Warning: No messages available for transfer.");
            return null;
        }

        // Iterate through all connections
        for (Connection con : connections) {
            DTNHost other = con.getOtherNode(host);
            if (other == null) {
                continue;
            }

            // Check if the other host is a subscriber
            if (other.isSubscriber()) {
                // Iterate through the message collection
                for (Message msg : msgCollection) {
                    if (msg == null) {
                        continue;
                    }
                    if (isSameInterest(msg, other)) {
                        messages.add(new Tuple<>(msg, con));
                    }
                }
            }
        }

        // If no messages are found, return null
        if (messages.isEmpty()) {
            return null;
        }

        // Sort messages based on interest similarity
        Collections.sort(messages, new InterestSimilarityComparator());

        // Try to transfer the messages
        return tryMessagesForConnected(messages);
    }

    // Method to replicate the router
    @Override
    public MessageRouter replicate() {
        return new PublishAndSubscriberRouting(this);
    }
}
