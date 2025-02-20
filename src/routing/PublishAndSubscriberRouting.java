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

/**
 * PublishAndSubscriberRouting implements a topic-based Publish-Subscribe routing mechanism
 * for Delay-Tolerant Networks (DTNs). It manages secure encryption, subscriber authentication,
 * and message forwarding using Numeric Attribute Key Trees (NAKT).
 */
public class PublishAndSubscriberRouting extends CCDTN {
    // Maps for managing topics, subscriptions, encryption, and authentication keys
    public static Map<TupleDe<String, List<Boolean>>, List<TupleDe<Integer, Integer>>> subscribedTopics; // key for the topic and id of subscriber, value is for list of numeric atribute
    public static Map<Integer, List<TupleDe<Boolean, String>>> registeredTopics;
    public static Map<String, TupleDe<String, String>> keyEncryption;
    public static Map<String, List<TupleDe<String, String>>> keyAuthentication;
    public static int lcnum;

    private boolean topicVall;
    private int subTopicVall;

    // for report
    private int msgReceived = 0;
    private int msgTransferred = 0;
    private int dataReceived = 0;
    private int dataTransferred = 0;

    // Handlers for registration and subscription management
    private KDCRegistrationProcessor processor;
    private BrokerHandler brokerHandler;


    /**
     * namespace settings ({@value})
     */
    private static final String PUBSROUTING_NS = "PublishAndSubscriberRouting";
    protected static final String LCNUM = "LCNUM";

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
        lcnum = ccSettings.getInt(LCNUM);
        initNAKT();
        // Ensure keyEncryption is initialized
        if (keyEncryption == null) {
            keyEncryption = new HashMap<>();
        } else {
            keyEncryption = brokerHandler.getKeyEncryption();
            for (Map.Entry<String, TupleDe<String, String>> entry : keyEncryption.entrySet()) {
                keyEncryption.put(entry.getKey(), entry.getValue());  // Deep copy to avoid reference issues
            }
        }

        // Initialize registeredTopics with a deep copy if needed
        registeredTopics = processor.getRegisteredTopics();
        if (registeredTopics == null) {
            registeredTopics = new HashMap<>();
        } else {
            for (Map.Entry<Integer, List<TupleDe<Boolean, String>>> entry : registeredTopics.entrySet()) {
                List<TupleDe<Boolean, String>> list = entry.getValue();
                registeredTopics.put(entry.getKey(), new ArrayList<>(list));  // Deep copy of the list
            }
        }

        // Handle subscribedTopics with deep copy logic if it's not null
        if (subscribedTopics == null) {
            subscribedTopics = new HashMap<>();
        } else {
            subscribedTopics = brokerHandler.getSubscribedTopics();
            for (Map.Entry<TupleDe<String, List<Boolean>>, List<TupleDe<Integer, Integer>>> entry : subscribedTopics.entrySet()) {
                // You should add the processing logic here for subscribedTopics if necessary
                // For example, deep copying the values of subscribedTopics if needed:
                TupleDe<String, List<Boolean>> key = entry.getKey();
                List<TupleDe<Integer, Integer>> value = entry.getValue();
                subscribedTopics.put(key, new ArrayList<>(value));  // Deep copy the list
            }
        }

        // Initialize keyAuthentication map
        if (keyAuthentication == null) {
            keyAuthentication = new HashMap<>();
        }

        // Ensure deep copy of keyAuthentication from brokerHandler if needed
        Map<String, List<TupleDe<String, String>>> tempKeyAuth = brokerHandler.getKeyAuthentication();
        if (tempKeyAuth != null) {
            for (Map.Entry<String, List<TupleDe<String, String>>> entry : tempKeyAuth.entrySet()) { // Deep copy the list
                keyAuthentication.put(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Copy Constructor: Creates a deep copy of an existing PublishAndSubscriberRouting instance.
     *
     * @param r The instance to be copied.
     */
    protected PublishAndSubscriberRouting(PublishAndSubscriberRouting r) {
        // Call the superclass copy constructor
        super(r);
        lcnum = r.lcnum;
        initNAKT();

        // Handle keyEncryption copy similarly
        if (r.keyEncryption == null) {
            keyEncryption = new HashMap<>();
        } else {
            keyEncryption = new HashMap<>(r.keyEncryption);
            for (Map.Entry<String, TupleDe<String, String>> entry : keyEncryption.entrySet()) {
                keyEncryption.put(entry.getKey(), entry.getValue());  // Deep copy values
            }
        }

        // Initialize registeredTopics as a new HashMap if it is null in the original object
        if (r.registeredTopics == null) {
            registeredTopics = new HashMap<>();
        } else {
            registeredTopics = new HashMap<>(r.registeredTopics);
            // Deep copy each list inside registeredTopics
            for (Map.Entry<Integer, List<TupleDe<Boolean, String>>> entry : r.registeredTopics.entrySet()) {
                List<TupleDe<Boolean, String>> valueCopy = new ArrayList<>(entry.getValue());
                registeredTopics.put(entry.getKey(), valueCopy);
            }
        }

        // Handle subscribedTopics copying
        if (r.subscribedTopics == null) {
            subscribedTopics = new HashMap<>();
        } else {
            subscribedTopics = new HashMap<>(r.subscribedTopics);
            // Deep copy the values for subscribedTopics
            for (Map.Entry<TupleDe<String, List<Boolean>>, List<TupleDe<Integer, Integer>>> entry : r.subscribedTopics.entrySet()) {
                List<TupleDe<Integer, Integer>> valueCopy = new ArrayList<>(entry.getValue());
                subscribedTopics.put(entry.getKey(), valueCopy);
            }
        }

        // Initialize keyAuthentication map
        if (r.keyAuthentication == null) {
            keyAuthentication = new HashMap<>();
        } else {
            keyAuthentication = new HashMap<>();
            // Deep copy of keyAuthentication
            for (Map.Entry<String, List<TupleDe<String, String>>> entry : r.keyAuthentication.entrySet()) {
                keyAuthentication.put(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Initializes the Numeric Attribute Key Tree (NAKT) processors.
     */
    private void initNAKT() {
        this.processor = new KDCRegistrationProcessor();
        this.brokerHandler = new BrokerHandler();
    }

    /**
     * Handles connection changes between hosts.
     * If a connection is established, registers the publisher with the broker
     * and checks the subscriber's interests.
     *
     * @param con The connection that has changed.
     */
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

    /**
     * Creates a new message to be published.
     * Ensures that the host is registered and encrypts the message
     * before adding it to the message queue.
     *
     * @param msg The message object to be created.
     * @return true if the message is successfully created, false otherwise.
     */
    @Override
    public boolean createNewMessage(Message msg) {
        List<DTNHost> allHosts = SimScenario.getInstance().getHosts();

        if (keyEncryption.isEmpty() || allHosts == null) {
            return false;
        }

        // Ambil ID host saat ini
        String hostId = String.valueOf(getHost().getRouter().getHost());

        // Cek apakah host saat ini terdaftar dalam registeredTopics menggunakan Map.Entry
        boolean isRegistered = false;

        Map<Integer, List<TupleDe<Boolean, String>>> registered = registeredTopics;
        if (registered == null || registered.isEmpty()) {
            return false;
        }

        // Iterasi melalui registeredTopics untuk memeriksa pendaftaran host
        for (Map.Entry<Integer, List<TupleDe<Boolean, String>>> entry : registered.entrySet()) {
            List<TupleDe<Boolean, String>> valueList = entry.getValue();
//            System.out.println("get");
            for (TupleDe<Boolean, String> tuple : valueList) {
                if (tuple.getSecond().equals(hostId)) {
                    isRegistered = true; // Host terdaftar sebagai registered
                    break;
                }
            }
            if (isRegistered) {
                break; // Keluar dari loop jika sudah terdaftar
            }
        }

        if (!isRegistered) {
            return false; // Jika tidak terdaftar, hentikan proses
        }

        // Proses untuk membuat pesan baru
        for (DTNHost host : allHosts) {
            String getHostId = String.valueOf(host.getRouter().getHost());

            // Cari host yang sesuai dengan getHostId
            if (!keyEncryption.containsKey(getHostId)) {
                continue;  // Lewati jika tidak cocok
            }

            TupleDe<String, String> keys = keyEncryption.get(getHostId);

            if (keys == null || keys.isEmpty()) {  // Pastikan list tidak kosong
                continue;
            }

            // Ambil topic dan subTopic dari host yang cocok
            if (getHostId.equals(hostId)) {
                topicVall = host.getTopicValue();
                subTopicVall = host.getSubTopic();
            }

            if (subTopicVall <= 0) {
                return false;
            }

//            String randomMessage = EncryptionUtil.generateRandomString(20); // 20 karakter
            String randomMessage = "abcdefghijABCDEFGHIJ"; // 20 karakter
            String keyEncrypt = keys.getSecond(); // Gunakan key dari Tuple
            String getBinaryPath = keys.getFirst();

            String hashedMessage = EncryptionUtil.encryptMessage(randomMessage, keyEncrypt);

            Map<Boolean, TupleDe<Integer, String>> messageData = new HashMap<>();
            TupleDe<Integer, String> value = new TupleDe<>(subTopicVall, hashedMessage);
            messageData.put(topicVall, value);
            System.out.println("get msg before encrytion: " + randomMessage);
            System.out.println("get key encryption: " + keyEncrypt);
            System.out.println("get msg after encrytion: " + hashedMessage);
            // **5. Tambahkan ke properti message**
            makeRoomForMessage(msg.getSize());
            msg.setTtl(this.msgTtl);
            msg.addProperty(MESSAGE_TOPICS_S, messageData);
            addToMessages(msg, true);
            System.out.println("success create msg with encrypt" + msg.getProperty(MESSAGE_TOPICS_S));
            return super.createNewMessage(msg);
        }
        return false;
    }


    /**
     * Handles the transfer of messages between hosts.
     * If the message is destined for a registered subscriber,
     * it verifies authentication before final delivery.
     *
     * @param id   The ID of the transferred message.
     * @param from The sender host of the message.
     * @return The message object after transfer.
     */
    @Override
    public Message messageTransferred(String id, DTNHost from) {
        Message incoming = removeFromIncomingBuffer(id, from);
        if (incoming == null) {
            throw new SimError("No message found with ID " + id + " in the incoming buffer.");
        }

        if (keyAuthentication == null || keyAuthentication.isEmpty()) {
            return null;
        }

        incoming.setReceiveTime(SimClock.getTime());

        Message outgoing = incoming;
        for (Application app : getApplications(incoming.getAppID())) {
            outgoing = app.handle(outgoing, getHost());
            if (outgoing == null) {
                break;
            }
        }
        // Cek apakah host subscriber sudah terdaftar dalam subscribedTopics
        String hostId = String.valueOf(getHost().getRouter().getHost());
        boolean isSubscribed = false;

        // Loop melalui subscribedTopics untuk mencocokkan hostId
        for (Map.Entry<TupleDe<String, List<Boolean>>, List<TupleDe<Integer, Integer>>> entry : subscribedTopics.entrySet()) {
            TupleDe<String, List<Boolean>> key = entry.getKey();
            if (key.getFirst().equals(hostId)) {
                isSubscribed = true; // Host terdaftar sebagai subscriber
                break;
            }
        }

        if (!isSubscribed) {
            return null; // Jika tidak terdaftar, hentikan proses
        }

        Message aMessage = (outgoing == null) ? incoming : outgoing;
        boolean isFinalRecipient = isFinalDest(aMessage, getHost(), keyAuthentication);
        boolean isFirstDelivery = isFinalRecipient && !isDeliveredMessage(aMessage);


        if (outgoing != null && !isFinalRecipient) {
            addToMessages(aMessage, false);
        }
        if (isFirstDelivery) {
            this.deliveredMessages.put(id, aMessage);
        }

        for (MessageListener ml : this.mListeners) {
            ml.messageTransferred(aMessage, from, getHost(), isFirstDelivery);
        }

        this.msgReceived++;
        this.dataReceived += aMessage.getSize();

        return aMessage;
    }

    /**
     * Handles the final steps after a message transfer is completed.
     * Increments counters for successful message transfers.
     *
     * @param con The connection involved in the message transfer.
     */
    @Override
    protected void transferDone(Connection con) {
        if (con == null || con.getMessage() == null) {
            return;
        }

        this.msgTransferred++;
        this.dataTransferred += con.getMessage().getSize();

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

    /**
     * Attempts to transfer messages to other nodes based on interest similarity.
     * - Scans all connections for potential recipients.
     * - Checks if the other node is a subscriber.
     * - Sorts messages based on interest similarity and attempts to transfer them.
     *
     * @return The message that was successfully transferred, or null if no transfer occurred.
     */
    private Tuple<Message, Connection> tryOtherMessages() {
        List<Tuple<Message, Connection>> messages = new ArrayList<>();
        List<Tuple<Message, Connection>> tempMessages = new ArrayList<>();

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
                        tempMessages.add(new Tuple<>(msg, con));
                    }
                }
            }
        }

        // Sort messages based on interest similarity
        Collections.sort(tempMessages, new InterestSimilarityComparator());

        messages.addAll(tempMessages);
        tempMessages.clear();

        // If no messages are found, return null
        if (messages.isEmpty()) {
            return null;
        }

        // Try to transfer the messages
        return tryMessagesForConnected(messages);
    }

    /**
     * Gets the total amount of data received by this router.
     *
     * @return The total amount of received data.
     */
    public int getTotalDataRcv() {
        return this.dataReceived;
    }

    /**
     * Gets the total amount of data transferred by this router.
     *
     * @return The total amount of transferred data.
     */
    public int getTotalDataTrf() {
        return this.dataTransferred;
    }

    /**
     * Gets the total number of messages received by this router.
     *
     * @return The total number of received messages.
     */
    public int getMsgReceived() {
        return this.msgReceived;
    }

    /**
     * Gets the total number of messages transferred by this router.
     *
     * @return The total number of transferred messages.
     */
    public int getMsgTransferred() {
        return this.msgTransferred;
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

}
