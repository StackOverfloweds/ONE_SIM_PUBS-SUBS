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
    protected static final String LCNUM = "LCNUM";

    // Static maps for managing topics, subscriptions, encryption, and authentication keys
    public static Map<DTNHost, TupleDe<List<Boolean>, List<TupleDe<Integer, Integer>>>> subscribedTopics;
    // Key: topic & subscriber ID, Value: list of numeric attributes
    public static Map<DTNHost, List<TupleDe<Boolean, Integer>>> registeredTopics;
    public static Map<DTNHost, TupleDe<String, String>> keyEncryption;
    public static Map<DTNHost, List<TupleDe<String, String>>> keyAuthentication;
    private Map<DTNHost, List<TupleDe<List<Boolean>, List<Integer>>>> tempTopicsM; //for temp regis
    // 🔹 Temporary storage for subscribers' interests
    private Map<DTNHost, List<TupleDe<TupleDe<List<Double>, List<Boolean>>, List<TupleDe<Integer, Integer>>>>> tempSubscribersM;

    // Static integer for local counter number
    public static int lcnum;

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
        // Ensure only publishers can register topics
        if (!otherNode.isPublisher()) {
            return;
        }

        // Ensure this node has previously exchanged data
        if (!tempTopicsM.containsKey(otherNode)) {
            return;
        }

        // 🔹 Find the KDC host
        DTNHost kdcHost = null;
        for (DTNHost host : SimScenario.getInstance().getHosts()) {
            if (host.isKDC()) {
                kdcHost = host;
                break; // Stop once KDC is found
            }
        }

        // Ensure that a KDC exists before registering
        if (kdcHost == null) {
            return;
        }

        List<TupleDe<List<Boolean>, List<Integer>>> tempTopics = tempTopicsM.get(otherNode);
        if (tempTopics == null || tempTopics.isEmpty()) {
            return;
        }

        // Initialize registeredTopics entry if not present
        if (!registeredTopics.containsKey(otherNode)) {
            registeredTopics.put(otherNode, new ArrayList<>());
        }

        List<TupleDe<Boolean, Integer>> registeredList = registeredTopics.get(otherNode);
        Set<TupleDe<Boolean, Integer>> existingEntries = new HashSet<>(registeredList);

        // Process topics while avoiding duplicates
        List<TupleDe<Boolean, Integer>> tempRegistered = new ArrayList<>();

        for (TupleDe<List<Boolean>, List<Integer>> tuple : tempTopics) {
            for (int i = 0; i < Math.min(tuple.getFirst().size(), tuple.getSecond().size()); i++) {
                TupleDe<Boolean, Integer> newEntry = new TupleDe<>(tuple.getFirst().get(i), tuple.getSecond().get(i));

                if (!existingEntries.contains(newEntry)) {
                    // 🔹 Try to add the registered topic to KDC buffer
                    if (!addToBufferForKDC(kdcHost, newEntry)) {
                        System.out.println("❌ Buffer addition failed, canceling registration for: " + otherNode);

                        // Rollback: Remove previously registered entries
                        registeredList.removeAll(tempRegistered);
                        return;
                    }

                    registeredList.add(newEntry);
                    existingEntries.add(newEntry);
                    tempRegistered.add(newEntry);
                }
            }
        }

//        System.out.println("✅ Topics registered for publisher: " + otherNode);

        // Clear tempTopicsM after processing
        tempTopicsM.clear();
//        System.out.println("📌 registeredTopics size: " + registeredTopics.size());
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
            System.err.println("Error: Missing subscriber attributes.");
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
        // Ensure only Subscribers can Subscribe
        if (!otherNode.isSubscriber()) {
            return;
        }

        // Ensure this node has previously exchanged data
        List<TupleDe<TupleDe<List<Double>, List<Boolean>>, List<TupleDe<Integer, Integer>>>> tempDataList = tempSubscribersM.get(otherNode);
        if (tempDataList == null || tempDataList.isEmpty()) {
            return;
        }

        // 🔹 Find the KDC host
        DTNHost kdcHost = null;
        for (DTNHost host : SimScenario.getInstance().getHosts()) {
            if (host.isKDC()) {
                kdcHost = host;
                break;
            }
        }

        // Ensure that a KDC exists before subscribing
        if (kdcHost == null) {
            return;
        }

        // Ensure `subscribedTopics` entry exists
        if (!subscribedTopics.containsKey(otherNode)) {
            subscribedTopics.put(otherNode, new TupleDe<>(new LinkedList<>(), new LinkedList<>()));
        }
        TupleDe<List<Boolean>, List<TupleDe<Integer, Integer>>> subscribedList = subscribedTopics.get(otherNode);

        // Store existing entries to avoid duplicates
        Set<TupleDe<Boolean, TupleDe<Integer, Integer>>> existingEntries = new HashSet<>();

        // Get registered topics to check for matches
        Map<DTNHost, List<TupleDe<Boolean, Integer>>> topics = registeredTopics;

        // 🔹 **Inisialisasi subscriberTopicMap & existingAttributes agar tidak null**
        Map<TupleDe<DTNHost, List<Boolean>>, List<TupleDe<TupleDe<Boolean, Integer>, DTNHost>>> subscriberTopicMap = new HashMap<>();
        List<TupleDe<Integer, Integer>> existingAttributes = new ArrayList<>();

        // 🔹 Batasi jumlah langganan untuk mencegah alokasi berlebihan
        int maxSubscriptions = 10;
        int subscriptionCount = 0;
        boolean allSubscriptionsSuccessful = true;

        Iterator<TupleDe<TupleDe<List<Double>, List<Boolean>>, List<TupleDe<Integer, Integer>>>> tempIterator = tempDataList.iterator();
        while (tempIterator.hasNext()) {
            TupleDe<TupleDe<List<Double>, List<Boolean>>, List<TupleDe<Integer, Integer>>> tempData = tempIterator.next();
            List<Boolean> socialProfileOI = tempData.getFirst().getSecond();
            List<TupleDe<Integer, Integer>> topicAttributes = tempData.getSecond();

            if (topicAttributes.isEmpty() || socialProfileOI.isEmpty()) {
                continue; // Hindari pemrosesan jika data tidak lengkap
            }

            Iterator<TupleDe<Integer, Integer>> subTopicIterator = topicAttributes.iterator();
            while (subTopicIterator.hasNext()) {
                if (subscriptionCount >= maxSubscriptions) {
                    break;
                }

                TupleDe<Integer, Integer> subTopicEntry = subTopicIterator.next();
                boolean topicExists = false;

                for (Map.Entry<DTNHost, List<TupleDe<Boolean, Integer>>> entry : topics.entrySet()) {
                    DTNHost publisherHost = entry.getKey();
                    List<TupleDe<Boolean, Integer>> registeredValues = entry.getValue();

                    for (TupleDe<Boolean, Integer> registeredValue : registeredValues) {
                        Boolean topicBoolean = registeredValue.getFirst();
                        Integer registeredSubTopic = registeredValue.getSecond();

                        // ✅ **Cek apakah subscriber memiliki topik yang sama dengan publisher**
                        if (socialProfileOI.contains(topicBoolean) &&
                                registeredSubTopic >= subTopicEntry.getFirst() &&
                                registeredSubTopic <= subTopicEntry.getSecond()) {

                            topicExists = true;
                            TupleDe<Boolean, TupleDe<Integer, Integer>> newEntry = new TupleDe<>(topicBoolean, subTopicEntry);

                            if (!existingEntries.contains(newEntry)) {
                                subscribedList.getFirst().add(topicBoolean);
                                subscribedList.getSecond().add(subTopicEntry);
                                existingEntries.add(newEntry);
                                subscriptionCount++;

                                System.out.println("✅ Successfully subscribed to topic: " + topicBoolean + " | Sub-Topic: " + subTopicEntry);

                                // 🔹 **Tambahkan ke subscriberTopicMap**
                                TupleDe<TupleDe<Boolean, Integer>, DTNHost> publisherInfo =
                                        new TupleDe<>(new TupleDe<>(topicBoolean, registeredSubTopic), publisherHost);

                                TupleDe<DTNHost, List<Boolean>> subscriberInfo =
                                        new TupleDe<>(otherNode, socialProfileOI);

                                // Pastikan subscriberInfo ada dalam subscriberTopicMap
                                subscriberTopicMap.computeIfAbsent(subscriberInfo, k -> new ArrayList<>())
                                        .add(publisherInfo);

                                // 🔹 **Tambahkan existingAttributes**
                                existingAttributes.add(subTopicEntry);

                                // 🔹 **Tambahkan ke buffer KDC**
                                boolean addedToBuffer = addToBufferForKDCToSubscribe(kdcHost, newEntry, otherNode);
                                if (!addedToBuffer) {
                                    System.out.println("❌ Subscription failed: Unable to add message to KDC buffer.");
                                    allSubscriptionsSuccessful = false;
                                    break;
                                }
                            }
                        }
                    }
                    if (topicExists) break;
                }

                subTopicIterator.remove();
            }

            tempIterator.remove();
        }

        // **Jika tidak ada subscription yang berhasil, rollback semuanya**
        if (!allSubscriptionsSuccessful || subscriberTopicMap.isEmpty()) {
            System.out.println("❌ Rolling back subscriptions for: " + otherNode);
            subscribedTopics.remove(otherNode);
            return;
        }

        // Bersihkan tempSubscribersM untuk subscriber ini setelah selesai
        tempSubscribersM.remove(otherNode);

        // 🔹 **Cek subscriberTopicMap sebelum membangun NAKT**
        if (subscriberTopicMap.isEmpty()) {
            System.out.println("⚠️ subscriberTopicMap is empty, skipping NAKT generation.");
            return;
        }

        // ✅ **Build NAKT dengan subscriberTopicMap dan existingAttributes**
        System.out.println("✅ Building NAKT...");
        NAKTBuilder nakt = new NAKTBuilder(lcnum);
        if (nakt.buildNAKT(subscriberTopicMap, existingAttributes)) {
            System.out.println("✅ NAKT successfully built.");

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

        // **Pastikan ada host, enkripsi, dan registeredTopics**
        if (allHosts == null || allHosts.isEmpty() || keyEncryption.isEmpty() || registeredTopics.isEmpty()) {
            System.out.println("❌ No hosts, encryption keys, or registered topics available.");
            return false;
        }

        DTNHost hostId = getHost();  // **Ambil ID host saat ini**

        // **Langsung cek apakah host memiliki registeredTopics**
        List<TupleDe<Boolean, Integer>> registeredList = registeredTopics.get(hostId);
        if (registeredList == null || registeredList.isEmpty()) {
            System.out.println("⚠️ No registered topics for host: " + hostId);
            return false;
        }

        boolean messageCreated = false;
        Set<TupleDe<Boolean, Integer>> sentMessages = new HashSet<>(); // 🔹 **Set untuk melacak pesan yang sudah dikirim**

        // **Iterasi melalui semua host**
        for (DTNHost host : allHosts) {
            TupleDe<String, String> keys = keyEncryption.get(host);

            if (keys == null || keys.getSecond() == null || keys.getSecond().isEmpty()) {
                continue;  // **Lewati host yang tidak memiliki kunci enkripsi**
            }

            for (Map.Entry<DTNHost, List<TupleDe<Boolean, Integer>>> entry : registeredTopics.entrySet()) {
                List<TupleDe<Boolean, Integer>> topicList = entry.getValue();

                if (topicList == null || topicList.isEmpty()) {
                    continue;
                }

                for (TupleDe<Boolean, Integer> getVal : topicList) {
                    // **Pastikan nilai valid**
                    if (getVal == null || getVal.getFirst() == null || getVal.getSecond() == null) {
                        continue;
                    }

                    // **Cek apakah pesan ini sudah pernah dikirim oleh host ini**
                    if (sentMessages.contains(getVal)) {
                        System.out.println("⚠️ Duplicate message detected, skipping: " + getVal);
                        continue;
                    }

                    // **Proses Enkripsi**
                    String randomMessage = "abcdefghijABCDEFGHIJ"; // 20 karakter
                    String keyEncrypt = keys.getSecond();
                    String hashedMessage = EncryptionUtil.encryptMessage(randomMessage, keyEncrypt);

                    Map<Boolean, TupleDe<Integer, String>> messageData = new HashMap<>();
                    TupleDe<Integer, String> value = new TupleDe<>(getVal.getSecond(), hashedMessage);
                    messageData.put(getVal.getFirst(), value);

                    // **Debugging**
                    System.out.println("🔹 Creating message...");
                    System.out.println("   - Plain Text: " + randomMessage);
                    System.out.println("   - Encryption Key: " + keyEncrypt);
                    System.out.println("   - Encrypted Text: " + hashedMessage);

                    // **Pastikan ada ruang sebelum menambahkan pesan**
                    if (!makeRoomForMessage(msg.getSize())) {
                        System.out.println("❌ Not enough space in buffer for message.");
                        return false;
                    }

                    // **Tambahkan pesan ke buffer**
                    msg.setTtl(this.msgTtl);
                    msg.addProperty(MESSAGE_TOPICS_S, messageData);
                    addToMessages(msg, true);
                    messageCreated = true;

                    // 🔹 **Tambahkan ke Set agar tidak dikirim dua kali**
                    sentMessages.add(getVal);

                    System.out.println("✅ Success create message with encryption: " + msg.getProperty(MESSAGE_TOPICS_S));
                }
            }
        }

        // **Kirim pesan hanya jika berhasil dibuat**
        return messageCreated && super.createNewMessage(msg);
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
        boolean isSubscribed = false;

        // Loop melalui subscribedTopics untuk mencocokkan hostId
        if (subscribedTopics.containsKey(getHost())) {
            isSubscribed = true;
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
            CCDTN othRouter = (CCDTN) other.getRouter();

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

                    if (othRouter.hasMessage(msg.getId())) {
                        continue; // skip messages that the other one has
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
