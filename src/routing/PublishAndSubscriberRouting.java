/*
 * @(#)PublishAndSubscriberRouting.java
 *
 * Copyright 2025 by Bryan (HaiPigGi-StackOverfloweds)
 *
 */
package routing;

import KDC.NAKT.NAKTBuilder;
import KDC.Publisher.EncryptionUtil;
import KDC.Subscriber.KeySubscriber;
import core.*;
import routing.util.TupleDe;

import java.util.*;

/**
 * PublishAndSubscriberRouting implements a topic-based Publish-Subscribe routing mechanism
 * for Delay-Tolerant Networks (DTNs). It manages secure encryption, subscriber authentication,
 * and message forwarding using Numeric Attribute Key Trees (NAKT).
 */
public class PublishAndSubscriberRouting extends CCDTN implements KeySubscriber {
    // Namespace settings
    private static final String PUBSROUTING_NS = "PublishAndSubscriberRouting";
    private static final String LCNUM = "LCNUM";

    // Static maps for managing topics, subscriptions, encryption, and authentication keys
    private Map<DTNHost, TupleDe<List<Boolean>, List<TupleDe<Integer, Integer>>>> subscribedTopics;
    // Key: topic & subscriber ID, Value: list of numeric attributes
    private Map<DTNHost, List<TupleDe<Boolean, Integer>>> registeredTopics;
    private Map<DTNHost, TupleDe<String, String>> keyEncryption;
    private Map<DTNHost, List<TupleDe<String, String>>> keyAuthentication;
    private Map<DTNHost, List<TupleDe<List<Boolean>, List<Integer>>>> tempTopicsM; //for temp regis
    // 🔹 Temporary storage for subscribers' interests
    private Map<DTNHost, List<TupleDe<TupleDe<List<Double>, List<Boolean>>, List<TupleDe<Integer, Integer>>>>> tempSubscribersM;

    // Static integer for local counter number
    private static int lcnum;

    // Counters for reporting message and data statistics
    private int msgReceived = 0;
    private int msgTransferred = 0;
    private int dataReceived = 0;
    private int dataTransferred = 0;


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
        this.tempTopicsM = new HashMap<>();
        this.tempSubscribersM = new HashMap<>();
        this.keyEncryption = new HashMap<>();
        // Initialize registeredTopics with a deep copy if needed
        this.registeredTopics = new HashMap<>();
        // Handle subscribedTopics with deep copy logic if it's not null
        this.subscribedTopics = new HashMap<>();
        // Initialize keyAuthentication map
        this.keyAuthentication = new HashMap<>();

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
        tempTopicsM = r.tempTopicsM;
        tempSubscribersM = r.tempSubscribersM;
        // Handle keyEncryption copy similarly
        keyEncryption = new HashMap<>(r.keyEncryption);
        // Initialize registeredTopics as a new HashMap if it is null in the original object
        registeredTopics = new HashMap<>(r.registeredTopics);
        // Handle subscribedTopics copying
        subscribedTopics = new HashMap<>(r.subscribedTopics);
        // Initialize keyAuthentication map
        keyAuthentication = new HashMap<>(r.keyAuthentication);
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

        // 🔹 Panggil metode untuk generate topic dan sub-topic untuk publisher
        List<Boolean> localTopicVall = host.getTopicValue();
        List<Integer> localSubTopicVall = host.getSubTopic();

        if (con.isUp()) {
            processLocalSubTopic(otherNode, host, localTopicVall, localSubTopicVall);
            interestCheck(otherNode);
        } else {
            processRegisteredTopics(otherNode);
            processSubscribedTopics(otherNode);
        }
    }


    private void processLocalSubTopic(DTNHost otherNode, DTNHost host, List<Boolean> localTopicVall, List<Integer> localSubTopicVall) {
        PublishAndSubscriberRouting othRouter = (PublishAndSubscriberRouting) otherNode.getRouter();

        // Initialize empty list
        List<TupleDe<List<Boolean>, List<Integer>>> listGetVal = new ArrayList<>();

        // Only allow publishers to have actual topics and subtopics
        if (host.isPublisher() && !localSubTopicVall.isEmpty()) {
            TupleDe<List<Boolean>, List<Integer>> getval = new TupleDe<>(localTopicVall, localSubTopicVall);
            listGetVal.add(getval);
        }

        // Store in tempTopicsM for both nodes
        this.tempTopicsM.put(otherNode, listGetVal);
        othRouter.tempTopicsM.put(host, listGetVal);
    }

    private void processRegisteredTopics(DTNHost otherNode) {
        // Pastikan hanya publisher yang dapat mendaftarkan topik
        if (!otherNode.isPublisher()) {
            return;
        }

        // Pastikan node memiliki data yang dapat diproses
        List<TupleDe<List<Boolean>, List<Integer>>> tempTopics = tempTopicsM.get(otherNode);
        if (tempTopics == null || tempTopics.isEmpty()) {
            return;
        }

        // Cari host KDC
        DTNHost kdcHost = SimScenario.getInstance().getHosts().stream()
                .filter(DTNHost::isKDC)
                .findFirst()
                .orElse(null);

        // Pastikan KDC tersedia sebelum mendaftarkan topik
        if (kdcHost == null) {
            return;
        }

        // Proses topik yang akan didaftarkan
        Map<DTNHost, TupleDe<Boolean, Integer>> getTop = new HashMap<>();

        for (TupleDe<List<Boolean>, List<Integer>> tuple : tempTopics) {
            int minSize = Math.min(tuple.getFirst().size(), tuple.getSecond().size());

            for (int i = 0; i < minSize; i++) {
                Boolean firstValue = tuple.getFirst().get(i);
                Integer secondValue = tuple.getSecond().get(i);

                if (secondValue <= 0) {
                    continue; // Lewati entry yang tidak valid
                }

                TupleDe<Boolean, Integer> newEntry = new TupleDe<>(firstValue, secondValue);
                getTop.put(otherNode, newEntry);
            }
        }

        // Kirim data ke KDC jika ada entry yang valid
        if (!getTop.isEmpty()) {
            addToBufferForKDCToRegistration(kdcHost, getTop);
        }

        // Bersihkan tempTopicsM setelah selesai
        tempTopicsM.remove(otherNode);
    }


    /**
     * Checks and processes the interest of the subscriber.
     * It verifies the subscriber's interest attributes and temporarily stores them in tempSubscribersM.
     *
     * @param host The DTNHost object representing the subscriber.
     */
    private void interestCheck(DTNHost host) {
        // Get subscriber's interest attributes
        List<Double> socialProfile = host.getSocialProfile();
        List<Boolean> socialProfileOI = host.getSocialProfileOI();
        List<TupleDe<Integer, Integer>> numericAtribute = host.getNumericAtribute();

        if (socialProfile == null || socialProfileOI == null || numericAtribute == null) {
            return;
        }

        // 🔹 Store the subscriber's interest using TupleDe (no Object type)
        TupleDe<List<Double>, List<Boolean>> profileData = new TupleDe<>(socialProfile, socialProfileOI);
        TupleDe<TupleDe<List<Double>, List<Boolean>>, List<TupleDe<Integer, Integer>>> subscriberData =
                new TupleDe<>(profileData, numericAtribute);

        // 🔹 Store in temporary map
        List<TupleDe<TupleDe<List<Double>, List<Boolean>>, List<TupleDe<Integer, Integer>>>> listGetVal = new ArrayList<>();
        listGetVal.add(subscriberData);
        tempSubscribersM.put(host, listGetVal);
    }

    public void processSubscribedTopics(DTNHost otherNode) {
        if (!otherNode.isSubscriber()) return;

        List<TupleDe<TupleDe<List<Double>, List<Boolean>>, List<TupleDe<Integer, Integer>>>> tempDataList = tempSubscribersM.get(otherNode);
        if (tempDataList == null || tempDataList.isEmpty()) return;

        DTNHost kdcHost = SimScenario.getInstance().getHosts().stream()
                .filter(DTNHost::isKDC)
                .findFirst()
                .orElse(null);
        if (kdcHost == null) return;

        subscribedTopics.putIfAbsent(otherNode, new TupleDe<>(new LinkedList<>(), new LinkedList<>()));
        TupleDe<List<Boolean>, List<TupleDe<Integer, Integer>>> subscribedList = subscribedTopics.get(otherNode);
        Set<TupleDe<Boolean, TupleDe<Integer, Integer>>> existingEntries = new HashSet<>();

        DTNHost host = getHost();
        if (host == null) return;

        Collection<Connection> connections = getConnections();
        if (connections == null || connections.isEmpty()) return;

        Collection<Message> msgCollection = getMessageCollection();
        if (msgCollection == null || msgCollection.isEmpty()) return;

        for (Connection con : connections) {
            DTNHost other = con.getOtherNode(host);
            PublishAndSubscriberRouting othRouter = (PublishAndSubscriberRouting) other.getRouter();
            if (othRouter.isTransferring()) continue;

            for (Message msg : msgCollection) {
                if (msg == null) return;

                Map<DTNHost, TupleDe<Boolean, Integer>> topics =
                        (Map<DTNHost, TupleDe<Boolean, Integer>>) msg.getProperty(MESSAGE_REGISTER_S);
                if (topics == null || topics.isEmpty()) continue;

                Map<TupleDe<DTNHost, List<Boolean>>, List<TupleDe<TupleDe<Boolean, Integer>, DTNHost>>> subscriberTopicMap = new HashMap<>();
                List<TupleDe<Integer, Integer>> existingAttributes = new ArrayList<>();

                int maxSubscriptions = 10;
                int subscriptionCount = 0;
                boolean allSubscriptionsSuccessful = true;

                Iterator<TupleDe<TupleDe<List<Double>, List<Boolean>>, List<TupleDe<Integer, Integer>>>> tempIterator = tempDataList.iterator();
                while (tempIterator.hasNext()) {
                    TupleDe<TupleDe<List<Double>, List<Boolean>>, List<TupleDe<Integer, Integer>>> tempData = tempIterator.next();
                    List<Boolean> socialProfileOI = tempData.getFirst().getSecond();
                    List<TupleDe<Integer, Integer>> topicAttributes = tempData.getSecond();

                    if (topicAttributes.isEmpty() || socialProfileOI.isEmpty()) continue;

                    Iterator<TupleDe<Integer, Integer>> subTopicIterator = topicAttributes.iterator();
                    while (subTopicIterator.hasNext() && subscriptionCount < maxSubscriptions) {
                        TupleDe<Integer, Integer> subTopicEntry = subTopicIterator.next();
                        boolean topicExists = false;

                        for (Map.Entry<DTNHost, TupleDe<Boolean, Integer>> entry : topics.entrySet()) {
                            DTNHost publisherHost = entry.getKey();
                            TupleDe<Boolean, Integer> registeredValues = entry.getValue();
                            Boolean topicBoolean = registeredValues.getFirst();
                            Integer registeredSubTopic = registeredValues.getSecond();

                            if (socialProfileOI.contains(topicBoolean) &&
                                    registeredSubTopic >= subTopicEntry.getFirst() &&
                                    registeredSubTopic <= subTopicEntry.getSecond()) {
                                topicExists = true;
                                TupleDe<Boolean, TupleDe<Integer, Integer>> newEntry = new TupleDe<>(topicBoolean, subTopicEntry);

                                if (existingEntries != null && existingEntries.add(newEntry)) { // ✅ Cek null sebelum akses
                                    subscribedList.getFirst().add(topicBoolean);
                                    subscribedList.getSecond().add(subTopicEntry);
                                    subscriptionCount++;

                                    subscriberTopicMap.computeIfAbsent(new TupleDe<>(otherNode, socialProfileOI), k -> new ArrayList<>())
                                            .add(new TupleDe<>(new TupleDe<>(topicBoolean, registeredSubTopic), publisherHost));

                                    Map<DTNHost, TupleDe<Boolean, TupleDe<Integer, Integer>>> topicEntry = new HashMap<>();

                                    // ✅ Perbaikan: Buat TupleDe<Integer, Integer> baru
                                    TupleDe<Integer, Integer> subTopicTuple = new TupleDe<>(registeredSubTopic, 0); // Sesuaikan angka kedua jika perlu

                                    // ✅ Buat tuple sesuai dengan tipe yang diminta
                                    TupleDe<Boolean, TupleDe<Integer, Integer>> tupleValue = new TupleDe<>(topicBoolean, subTopicTuple);
                                    topicEntry.put(otherNode, tupleValue);

                                    existingAttributes.add(subTopicEntry);
                                    subscribedTopics.put(otherNode, subscribedList);

                                    // ✅ Sekarang topicEntry memiliki tipe yang benar
//                                    addToBufferForKDCToSubscribe(kdcHost, topicEntry);
                                }
                            }
                            if (topicExists) break;
                        }
                        subTopicIterator.remove();
                    }
                    tempIterator.remove();
                }

                if (!allSubscriptionsSuccessful || subscriberTopicMap.isEmpty()) {
                    subscribedTopics.remove(otherNode);
                    return;
                }
                tempSubscribersM.remove(otherNode);
                subscribedTopics.remove(otherNode);

                if (subscriberTopicMap.isEmpty()) {
                    return;
                }

                // ✅ **Build NAKT dengan subscriberTopicMap dan existingAttributes**
//                System.out.println("✅ Building NAKT...");
                NAKTBuilder nakt = new NAKTBuilder(lcnum);
                if (nakt.buildNAKT(subscriberTopicMap, existingAttributes)) {
//                    System.out.println("✅ NAKT successfully built.");

                    Map<DTNHost, TupleDe<String, String>> publisherKeys = nakt.getKeysForPublisher();
                    Map<DTNHost, List<TupleDe<String, String>>> subscriberKeys = nakt.getKeysForSubscriber();

                    if (publisherKeys != null && !publisherKeys.isEmpty()) {
                        for (Map.Entry<DTNHost, TupleDe<String, String>> entryKey : publisherKeys.entrySet()) {
                            if (entryKey.getValue() != null && !entryKey.getValue().isEmpty()) {
                                keyEncryption.put(entryKey.getKey(), entryKey.getValue());
                            }
                        }
                    }

                    if (subscriberKeys != null && !subscriberKeys.isEmpty()) {
                        for (Map.Entry<DTNHost, List<TupleDe<String, String>>> entryKey : subscriberKeys.entrySet()) {
                            if (entryKey.getValue() != null && !entryKey.getValue().isEmpty()) {
                                keyAuthentication.put(entryKey.getKey(), entryKey.getValue());
                            }
                        }
                    }
                } else {
                    System.out.println("❌ Failed to build NAKT.");
                }
            }
        }
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
        // 🔍 **Validasi Awal**
        if (keyEncryption == null || keyEncryption.isEmpty()) {
            return false;
        }

        Collection<Connection> connections = getConnections();
        if (connections == null || connections.isEmpty()) {
            return false;
        }

        DTNHost host = getHost();
        if (host == null) {
            return false;
        }

        Collection<Message> msgCollection = getMessageCollection();
        if (msgCollection == null || msgCollection.isEmpty()) {
            return false;
        }

        System.out.println("📌 Starting message creation process...");

        List<Message> messagesToSend = new ArrayList<>();

        for (Connection con : connections) {
            DTNHost other = con.getOtherNode(host);
            PublishAndSubscriberRouting othRouter = (PublishAndSubscriberRouting) other.getRouter();

            if (othRouter.isTransferring()) {
                continue;
            }

            for (Message m : msgCollection) {
                if (m == null) {
                    continue;
                }

                Map<DTNHost, TupleDe<Boolean, Integer>> getRegisTop =
                        (Map<DTNHost, TupleDe<Boolean, Integer>>) m.getProperty(MESSAGE_REGISTER_S);

                if (getRegisTop == null || getRegisTop.isEmpty()) {
                    continue;
                }

                int messageCount = 0;
                for (Map.Entry<DTNHost, TupleDe<Boolean, Integer>> topic : getRegisTop.entrySet()) {
                    if (topic == null || topic.getValue() == null ||
                            topic.getValue().getFirst() == null ||
                            topic.getValue().getSecond() == null) {
                        continue;
                    }

                    DTNHost topicHost = topic.getKey();
                    TupleDe<Boolean, Integer> topicData = topic.getValue();

                    // **Cek apakah ada kunci enkripsi untuk host ini**
                    if (!keyEncryption.containsKey(topicHost)) {
                        continue;
                    }

                    TupleDe<String, String> encryptionKey = keyEncryption.get(topicHost);

                    // 🔒 **Proses Enkripsi**
                    String randomMessage = "abcdefghijABCDEFGHIJ" + messageCount; // 20 karakter unik
                    String hashedMessage = EncryptionUtil.encryptMessage(randomMessage, encryptionKey.getSecond());

                    Map<Boolean, TupleDe<Integer, String>> messageData = new HashMap<>();
                    messageData.put(topicData.getFirst(), new TupleDe<>(topicData.getSecond(), hashedMessage));

                    // **Buat duplikasi pesan sebelum ditambahkan**
                    makeRoomForMessage(msg.getSize());
                    msg.setTtl(this.msgTtl);
                    msg.addProperty(MESSAGE_TOPICS_S, messageData);

                    messagesToSend.add(msg);
                    messageCount++;

                    if (messageCount >= 3) {
                        break;
                    }
                }

                // **Kirim semua pesan yang berhasil dibuat**
                if (!messagesToSend.isEmpty()) {
                    for (Message message : messagesToSend) {
                        addToMessages(message, true);
                        super.createNewMessage(message);
                    }

                    System.out.println("✅ Message successfully created: " + msg.getProperty(MESSAGE_TOPICS_S));
                    return true;
                }
            }
        }
        return false; // 🔹 **Return false jika tidak ada pesan yang dibuat**
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
//        System.out.println("msg received: " + id);

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

        // Cek apakah host ini merupakan subscriber yang sah
        boolean isSubscribed = getHost().isSubscriber();

        if (!isSubscribed) {
//            System.out.println("⛔ Host " + getHost() + " is not a subscriber. Message dropped.");
            return null;
        }


        // Pesan akan diteruskan jika lolos validasi
        Message aMessage = (outgoing == null) ? incoming : outgoing;
        boolean isFinalRecipient = isFinalDest(aMessage, getHost(), keyAuthentication);
        boolean isFirstDelivery = isFinalRecipient && !isDeliveredMessage(aMessage);

//        System.out.println("🔍 Message processing: " + id + " | Final recipient: " + isFinalRecipient);

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
            PublishAndSubscriberRouting othRouter = (PublishAndSubscriberRouting) other.getRouter();

            if (othRouter.isTransferring()) {
                continue;
            }

            // Check if the other host is a subscriber
            if (other.isSubscriber()) {
                // Iterate through the message collection
                for (Message msg : msgCollection) {
                    if (msg == null) {
                        continue;
                    }

                    // Cek apakah message sesuai dengan minat penerima
                    if (isSameInterest(msg, other)) {
                        messages.add(new Tuple<>(msg, con));
                    }
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

    public Map<DTNHost, Integer> getKeys() {
        Map<DTNHost, Integer> keys = new HashMap<>();

        if (keyAuthentication == null) {
            return Collections.emptyMap(); // ✅ Return an empty map instead of null
        }

        for (Map.Entry<DTNHost, List<TupleDe<String, String>>> entry : keyAuthentication.entrySet()) {
            if (entry.getValue() != null) { // ✅ Prevents NullPointerException
                int keyCount = entry.getValue().size();
                keys.put(entry.getKey(), keyCount);
            }
        }

        return keys;
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
