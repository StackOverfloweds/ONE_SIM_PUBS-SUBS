/*
 * @(#)PublishAndSubscriberRouting.java
 *
 * Copyright 2025 by Bryan (HaiPigGi-StackOverfloweds)
 *
 */
package routing;

import core.*;
import routing.util.TupleDe;

import java.util.*;

/**
 * PublishAndSubscriberRouting implements a topic-based Publish-Subscribe routing mechanism
 * for Delay-Tolerant Networks (DTNs). It manages secure encryption, subscriber authentication,
 * and message forwarding using Numeric Attribute Key Trees (NAKT).
 */
public class PublishAndSubscriberRouting extends CCDTN {
    // Namespace settings
    private static final String PUBSROUTING_NS = "PublishAndSubscriberRouting";
    // Static maps for managing topics, subscriptions, encryption, and authentication keys
    private Map<DTNHost, TupleDe<List<Boolean>, List<TupleDe<Integer, Integer>>>> subscribedTopics;
    // Key: topic & subscriber ID, Value: list of numeric attributes
    private Map<DTNHost, List<TupleDe<Boolean, Integer>>> registeredTopics;
    private Map<DTNHost, List<TupleDe<List<Boolean>, List<Integer>>>> tempTopicsM; //for temp regis
    // 🔹 Temporary storage for subscribers' interests
    private Map<DTNHost, List<TupleDe<TupleDe<List<Double>, List<Boolean>>, List<TupleDe<Integer, Integer>>>>> tempSubscribersM;
    /**
     * update interval diset dari settings
     */
    private double updateInterval;

    /**
     * nilai update interval atur di settings
     */
    private static final String UPDATE_INTERVAL = "updateInterval";

    private double lastUpdateTime = 0;

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
        updateInterval = ccSettings.getInt(UPDATE_INTERVAL);
        this.tempTopicsM = new HashMap<>();
        this.tempSubscribersM = new HashMap<>();
        // Initialize registeredTopics with a deep copy if needed
        this.registeredTopics = new HashMap<>();
        // Handle subscribedTopics with deep copy logic if it's not null
        this.subscribedTopics = new HashMap<>();
    }

    /**
     * Copy Constructor: Creates a deep copy of an existing PublishAndSubscriberRouting instance.
     *
     * @param r The instance to be copied.
     */
    protected PublishAndSubscriberRouting(PublishAndSubscriberRouting r) {
        // Call the superclass copy constructor
        super(r);
        tempTopicsM = r.tempTopicsM;
        tempSubscribersM = r.tempSubscribersM;
        updateInterval = r.updateInterval;
        // Initialize registeredTopics as a new HashMap if it is null in the original object
        registeredTopics = new HashMap<>(r.registeredTopics);
        // Handle subscribedTopics copying
        subscribedTopics = new HashMap<>(r.subscribedTopics);
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
        super.createNewMessage(msg);
        // Buat daftar sub-topik
        List<TupleDe<Boolean, Integer>> setSubTopic = new ArrayList<>();
        Map<DTNHost, List<TupleDe<Boolean, Integer>>> setTop = new HashMap<>();

        Random rand = new Random();

        if (getHost().isPublisher()) { // Pastikan hanya publisher yang menjalankan ini
            for (int i = 0; i < 5; i++) { // Loop untuk 5 topik
                boolean topicValue = rand.nextBoolean();
                int subTopicValue = rand.nextInt(29) + 1;

                // Tambahkan tuple ke daftar
                setSubTopic.add(new TupleDe<>(topicValue, subTopicValue));
            }
            // Tambahkan daftar ke map dengan host sebagai key
            setTop.put(getHost(), new ArrayList<>(setSubTopic));
        }

        // Tambahkan data topik ke dalam pesan
        msg.addProperty(MESSAGE_GET_REGISTER_S, setTop);
        return sendMsgForRegistration(getHost(), msg);
    }


    private boolean sendMsgForRegistration(DTNHost host, Message msg) {
        // Ambil semua koneksi
        Collection<Connection> connections = getConnections();
        if (connections == null) {
            return false;
        }

        List<DTNHost> brokerHosts = getAllBrokers();

        // Kumpulkan semua broker yang terhubung
        for (Connection con : connections) {
            DTNHost other = con.getOtherNode(host);
            PublishAndSubscriberRouting othRouter = (PublishAndSubscriberRouting) other.getRouter();

            if (othRouter.isTransferring()) {
                continue; // Jika sedang mentransfer, lewati
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
            addToMessages(msg, false);
        }

        // Kumpulkan semua host yang merupakan KDC
        List<DTNHost> kdcHosts = new ArrayList<>();
        for (DTNHost otherHost : SimScenario.getInstance().getHosts()) {
            if (otherHost.isKDC() && otherHost.getRouter() instanceof PublishAndSubscriberRouting) {
                kdcHosts.add(otherHost);
            }
        }

        // Kirim pesan dari broker ke semua KDC
        if (!kdcHosts.isEmpty()) {
            for (DTNHost broker : brokerHosts) {
                for (DTNHost kdc : kdcHosts) {
                    Map<DTNHost, List<TupleDe<Boolean, Integer>>> registerData =
                            (Map<DTNHost, List<TupleDe<Boolean, Integer>>>) msg.getProperty(MESSAGE_GET_REGISTER_S);

                    if (registerData != null) {
                        msg.addProperty(MESSAGE_REGISTER_S, registerData); // Tandai bahwa msg sudah register
                    }

                    addToMessages(msg, false);
//                    System.out.println("Pesan register dikirim dari broker " + broker + " ke KDC: " + kdc);
                }
            }
        } else {
            return false;
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

        // Coba transfer ke node lain
        tryOtherMessages();
        processAndForwardMessages();

        // catat per interval waktu untuk msg yang sudah di buat
        if ((SimClock.getTime() - lastUpdateTime) >= updateInterval) {
            lastUpdateTime = SimClock.getTime();

            // Get the message collection
            Collection<Message> msgCollection = getMessageCollection();
            if (msgCollection.isEmpty()) {
                return;
            }
            Iterator<Message> msgIterator = new ArrayList<>(msgCollection).iterator();
            while (msgIterator.hasNext()) {
                Message msg = msgIterator.next();
                if (msg == null) {
                    continue;
                }
                messageRegistry.addMessage(msg);
//                    System.out.println("get property " + msg.getProperty(MESSAGE_KEY_ENCRYPTION_S));
            }


        }
    }

    private void processAndForwardMessages() {
        Collection<Message> msgCollection = getMessageCollection();
        if (msgCollection.isEmpty()) {
            return;
        }

        // Get all connections
        Collection<Connection> connections = getConnections();
        if (connections == null) {
            return;
        }
        List<DTNHost> getAllKDC = getAllKDCs();

        for (Connection con : connections) {
            DTNHost other = con.getOtherNode(getHost());
            PublishAndSubscriberRouting othRouter = (PublishAndSubscriberRouting) other.getRouter();
            if (othRouter.isTransferring()) {
                continue;
            }

            Iterator<Message> msgIterator = new ArrayList<>(msgCollection).iterator();
            while (msgIterator.hasNext()) {
                Message msg = msgIterator.next();
                if (msg == null) {
                    continue;
                }
                sendMsgToBrokerForSubs(other, msg);
                brokerSendToKDCForSubs(msg);
                buildNAKT(getAllKDC, msg);
            }

        }
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
        List<DTNHost> getAllKDC = getAllKDCs();

        // Iterate through all connections
        for (Connection con : connections) {
            DTNHost other = con.getOtherNode(host);
            PublishAndSubscriberRouting othRouter = (PublishAndSubscriberRouting) other.getRouter();
            if (othRouter.isTransferring()) {
                continue;
            }

            // Iterate through the message collection
            for (Message msg : msgCollection) {

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

    private boolean sendMsgToBrokerForSubs(DTNHost other, Message msg) {
        // Get all connections
        Collection<Connection> connections = getConnections();
        if (connections == null) {
            return false;
        }

        List<Boolean> topicNode = other.getSocialProfileOI();
        List<TupleDe<Integer, Integer>> getSubTopic = other.getNumericAtribute();

        if (topicNode == null || getSubTopic == null || getSubTopic.isEmpty()) {
            return false;
        }

        // Buat TupleDe yang berisi topicNode dan getSubTopic
        TupleDe<List<Boolean>, List<TupleDe<Integer, Integer>>> tupleData =
                new TupleDe<>(topicNode, getSubTopic);

        // Masukkan ke dalam Map dengan host sebagai key
        Map<DTNHost, List<TupleDe<List<Boolean>, List<TupleDe<Integer, Integer>>>>> hostDataMap = new HashMap<>();
        hostDataMap.put(other, Collections.singletonList(tupleData));

        // Tambahkan data ke pesan
        msg.addProperty(MESSAGE_GET_SUBSCRIBE_S, hostDataMap);
        // if its true
        if (sendToBrokerBuffer(other, msg)) {
            return true;
        }

        return false;
    }


    private boolean sendToBrokerBuffer(DTNHost other, Message msg) {
        Collection<Connection> connections = getConnections();
        if (connections == null) {
            System.err.println("Error: getConnections() is null!");
            return false;
        }

        for (Connection con : connections) {
            DTNHost others = con.getOtherNode(other);
            PublishAndSubscriberRouting othRouter =
                    (others.getRouter() instanceof PublishAndSubscriberRouting) ?
                            (PublishAndSubscriberRouting) others.getRouter() : null;

            if (othRouter == null || othRouter.isTransferring()) {
                continue; // Jika tidak valid atau sedang mentransfer, lewati
            }

            if (others.isBroker()) {
                addToMessages(msg, false); // Tambahkan pesan ke broker
            }
        }

        return true;
    }

    /**
     * @param msg
     * @return
     */
    private boolean brokerSendToKDCForSubs(Message msg) {
        Map<DTNHost, List<TupleDe<Boolean, Integer>>> registerData =
                (Map<DTNHost, List<TupleDe<Boolean, Integer>>>) msg.getProperty(MESSAGE_REGISTER_S);
        Map<DTNHost, List<TupleDe<List<Boolean>, List<TupleDe<Integer, Integer>>>>> getUnSubs =
                (Map<DTNHost, List<TupleDe<List<Boolean>, List<TupleDe<Integer, Integer>>>>>) msg.getProperty(MESSAGE_GET_SUBSCRIBE_S);

        if (registerData == null || registerData.isEmpty() || getUnSubs == null || getUnSubs.isEmpty()) {
            return false;
        }

        List<Boolean> regisBooleanList = new ArrayList<>();
        List<Boolean> subBooleanList = new ArrayList<>();

        // Mengambil semua nilai boolean dari registerData
        for (List<TupleDe<Boolean, Integer>> tuples : registerData.values()) {
            for (TupleDe<Boolean, Integer> tuple : tuples) {
                regisBooleanList.add(tuple.getFirst());
            }
        }

        // Mengambil semua nilai boolean dari getUnSubs
        for (List<TupleDe<List<Boolean>, List<TupleDe<Integer, Integer>>>> tuples : getUnSubs.values()) {
            for (TupleDe<List<Boolean>, List<TupleDe<Integer, Integer>>> tuple : tuples) {
                subBooleanList.addAll(tuple.getFirst());
            }
        }

        for (Boolean top : regisBooleanList) {
            if (subBooleanList.contains(top)) {
                // Mencari semua host yang merupakan KDC
                List<DTNHost> kdcHosts = new ArrayList<>();
                for (DTNHost otherHost : SimScenario.getInstance().getHosts()) {
                    if (otherHost.isKDC() && otherHost.getRouter() instanceof PublishAndSubscriberRouting) {
                        kdcHosts.add(otherHost);
                    }
                }

                // Jika ada KDC, kirim pesan ke semua KDC
                if (!kdcHosts.isEmpty()) {
                    for (DTNHost kdc : kdcHosts) {
                        if (getUnSubs != null) {
                            msg.addProperty(MESSAGE_SUBSCRIBE_S, getUnSubs);
                        }
                        addToMessages(msg, false);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Builds the NAKT key structure.
     * - Generates keys for publishers and subscribers.
     * - Assigns appropriate encryption keys to topics and attributes.
     *
     * @param kdcHosts host is the kdc to process the building
     * @param msg      get msg
     * @return true if the NAKT key structure is successfully built, false otherwise.
     */
    public boolean buildNAKT(List<DTNHost> kdcHosts, Message msg) {
        boolean success = false;

        for (DTNHost kdcHost : kdcHosts) {
            if (!kdcHost.isKDC()) continue;

            Map<DTNHost, List<TupleDe<Boolean, Integer>>> registerData =
                    (Map<DTNHost, List<TupleDe<Boolean, Integer>>>) msg.getProperty(MESSAGE_REGISTER_S);
            Map<DTNHost, List<TupleDe<List<Boolean>, List<TupleDe<Integer, Integer>>>>> getUnSubs =
                    (Map<DTNHost, List<TupleDe<List<Boolean>, List<TupleDe<Integer, Integer>>>>>) msg.getProperty(MESSAGE_SUBSCRIBE_S);

            if (registerData == null || registerData.isEmpty() || getUnSubs == null || getUnSubs.isEmpty()) {
                continue;
            }

            for (Map.Entry<DTNHost, List<TupleDe<Boolean, Integer>>> entry : registerData.entrySet()) {
                DTNHost subscriber = entry.getKey();
                List<TupleDe<Boolean, Integer>> subscriberInfo = entry.getValue();
                if (subscriberInfo == null || subscriberInfo.isEmpty()) continue;

                TupleDe<Boolean, Integer> firstEntry = subscriberInfo.get(0);
                int attributeValue = firstEntry.getSecond();
                boolean topicVal = firstEntry.getFirst();

                if (!keyEncryption.containsKey(subscriber)) {
                    String rootKey = keyManager.generateRootKey(topicVal);
                    List<TupleDe<String, String>> keyList = new ArrayList<>();
                    naktBuilder.encryptTreeNodes(0, naktBuilder.getNearestPowerOfTwo(attributeValue) - 1, rootKey, "", 1, keyList);

                    String binaryPathPubs = Integer.toBinaryString(attributeValue);
                    TupleDe<String, String> selectedKey = keyList.stream()
                            .filter(tuple -> tuple.getFirst().equals(binaryPathPubs))
                            .findFirst()
                            .orElse(null);

                    if (selectedKey != null) {
                        keyEncryption.put(subscriber, selectedKey);
                    }
                }

                if (!keyAuthentication.containsKey(subscriber)) {
                    List<TupleDe<String, String>> derivedKeys = new ArrayList<>();
                    List<TupleDe<Integer, Integer>> existingAttributes = new ArrayList<>();

                    List<TupleDe<List<Boolean>, List<TupleDe<Integer, Integer>>>> unsubData = getUnSubs.get(subscriber);
                    if (unsubData != null) {
                        for (TupleDe<List<Boolean>, List<TupleDe<Integer, Integer>>> unsubEntry : unsubData) {
                            existingAttributes.addAll(unsubEntry.getSecond());
                        }
                    }

                    if (!existingAttributes.isEmpty()) {
                        String rootKey = keyManager.generateRootKey(topicVal);
                        List<TupleDe<String, String>> keyList = new ArrayList<>();
                        naktBuilder.encryptTreeNodes(0, naktBuilder.getNearestPowerOfTwo(attributeValue) - 1, rootKey, "", 1, keyList);

                        for (TupleDe<Integer, Integer> range : existingAttributes) {
                            for (int i = range.getFirst(); i <= range.getSecond(); i++) {
                                String binaryPathSubs = Integer.toBinaryString(i);
                                for (TupleDe<String, String> keyTuple : keyList) {
                                    if (binaryPathSubs.startsWith(keyTuple.getFirst()) && !derivedKeys.contains(keyTuple)) {
                                        derivedKeys.add(keyTuple);
                                    }
                                }
                            }
                        }

                        if (!derivedKeys.isEmpty()) {
                            keyAuthentication.put(subscriber, derivedKeys);
                        }
                    }
                }
            }

            List<DTNHost> brokerList = new ArrayList<>();
            for (DTNHost host : SimScenario.getInstance().getHosts()) {
                if (host.isBroker()) brokerList.add(host);
            }

            // Kirim ke semua broker terlebih dahulu
            for (DTNHost broker : brokerList) {
                if (broker.isBroker()) {
                    addToMessages(msg, false);
                }

                List<DTNHost> getAllPubs = getAllPublisher();
                for (DTNHost pub : getAllPubs) {
                    // Broker meneruskan ke publisher dan subscriber
                    for (Map.Entry<DTNHost, List<TupleDe<Boolean, Integer>>> entry : registerData.entrySet()) {
                        DTNHost PublisherKey = entry.getKey();
                        if (pub.equals(PublisherKey)) {
//                            System.out.println("publisher have the key: " + PublisherKey);
                            if (keyEncryption != null) {
                                msg.addProperty(MESSAGE_KEY_ENCRYPTION_S, keyEncryption);
                            }
                            addToMessages(msg, false); // Menggunakan flag true untuk identifikasi routing via broker

                        }
                    }
                }
                List<DTNHost> getAllSubs = getAllSubscriber();
                for (DTNHost sub : getAllSubs) {
                    for (Map.Entry<DTNHost, List<TupleDe<List<Boolean>, List<TupleDe<Integer, Integer>>>>> entrySubscriber : getUnSubs.entrySet()) {
                        DTNHost SubscriberHost = entrySubscriber.getKey();
                        if (sub.equals(SubscriberHost)) {
//                            System.out.println("subscriber have the key: " + SubscriberHost);
                            if (keyAuthentication != null) {
                                msg.addProperty(MESSAGE_KEY_AUTHENTICATION_S, keyAuthentication);
                            }
                            addToMessages(msg, false);
                        }
                    }
                }
            }
            success = true;
        }
        if (success) {
            return super.createNewMessage(msg);
        }
        return false;
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
