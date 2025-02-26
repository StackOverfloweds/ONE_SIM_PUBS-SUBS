/*
 * @(#)ContentRouter.java
 *
 * Copyright 2025 by Bryan (HaiPigGi-StackOverfloweds)
 *
 * This class implements the routing logic for the CCDTN (Content-Centric DTN) router.
 * It handles message forwarding, subscription-based content delivery, and secure decryption.
 */

package routing;

import routing.KDC.NAKT.KeyManager;
import routing.KDC.NAKT.NAKTBuilder;
import routing.KDC.Publisher.EncryptionUtil;
import routing.KDC.Subscriber.DecryptUtil;
import core.*;

import java.util.*;

import routing.community.Duration;
import routing.util.TupleDe;

public class CCDTN extends ActiveRouter {

    // Constant for storing message topics
    public static final String MESSAGE_TOPICS_S = "topic";
    public static final String MESSAGE_GET_REGISTER_S = "KDC_Get_Register_";
    public static final String MESSAGE_REGISTER_S = "KDC_Register_";
    public static final String MESSAGE_GET_SUBSCRIBE_S = "KDC_Get_Subscribe_";
    public static final String MESSAGE_SUBSCRIBE_S = "KDC_Subscribe_";
    public static final String MESSAGE_KEY_ENCRYPTION_S = "KDC_Key_Encryption_";
    public static final String MESSAGE_KEY_AUTHENTICATION_S = "KDC_Key_Authentication_";
    protected Map<DTNHost, TupleDe<String, String>> keyEncryption;
    protected Map<DTNHost, List<TupleDe<String, String>>> keyAuthentication;

    // Maps to store connection timestamps and history
    protected Map<DTNHost, Double> startTimestamps;
    protected Map<DTNHost, List<Duration>> connHistory;

    protected NAKTBuilder naktBuilder;
    protected KeyManager keyManager;

    protected MessageRegistryImpl messageRegistry;

    /**
     * Constructor: Initializes CCDTN with settings.
     *
     * @param s Settings object for configuration
     */
    public CCDTN(Settings s) {
        super(s);
        initNAKT();
        this.startTimestamps = new HashMap<>();
        this.connHistory = new HashMap<>();
        this.keyEncryption = new HashMap<>();
        this.keyAuthentication = new HashMap<>();
    }

    /**
     * Copy constructor: Creates a deep copy of an existing CCDTN router.
     *
     * @param c The CCDTN instance to copy
     */
    protected CCDTN(CCDTN c) {
        super(c);
        initNAKT();
        startTimestamps = new HashMap<>(c.startTimestamps);
        connHistory = new HashMap<>(c.connHistory);
        keyEncryption = new HashMap<>(c.keyEncryption);
        keyAuthentication = new HashMap<>(c.keyAuthentication);
    }

    private void initNAKT() {
        this.naktBuilder = new NAKTBuilder(4); // set lcnum 4
        this.keyManager = new KeyManager();
        this.messageRegistry = new MessageRegistryImpl();
    }


    /**
     * Handles changes in connection status.
     *
     * @param con The connection that changed state
     */
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

                // Retrieve or create connection history
                List<Duration> history = connHistory.computeIfAbsent(peer, k -> new LinkedList<>());

                // Add connection duration if it is valid
                if (etime - time > 0) {
                    history.add(new Duration(time, etime));
                }

                startTimestamps.remove(peer);
            }
        }
    }

    @Override
    public boolean createNewMessage(Message msg) {
        List<Message> messages = messageRegistry.getAllMessages();
//        System.out.println("Creating new message "+messages);

        for (Message m : messages) {
//            System.out.println("get propherty "+m.getProperty(MESSAGE_KEY_ENCRYPTION_S));
        }
        if (!getHost().isPublisher()) {
            return false;
        }

        // Ambil properti pesan
        Map<DTNHost, TupleDe<String, String>> getKeyEnc =
                (Map<DTNHost, TupleDe<String, String>>) msg.getProperty(MESSAGE_KEY_ENCRYPTION_S);

        Map<DTNHost, List<TupleDe<Boolean, Integer>>> getTopPubs =
                (Map<DTNHost, List<TupleDe<Boolean, Integer>>>) msg.getProperty(MESSAGE_GET_REGISTER_S);

        // Validasi data
        if (getKeyEnc == null || getKeyEnc.isEmpty() || getTopPubs == null || getTopPubs.isEmpty()) {
            return false;
        }

//        System.out.println("get data : " + getKeyEnc);
//        System.out.println("get top pubs : " + getTopPubs);

        boolean success = false;

        for (Map.Entry<DTNHost, List<TupleDe<Boolean, Integer>>> entryTop : getTopPubs.entrySet()) {
            DTNHost pubsId = entryTop.getKey();
            List<TupleDe<Boolean, Integer>> values = entryTop.getValue();

            // 🛑 Cek apakah list values kosong
            if (values == null || values.isEmpty()) {
                System.out.println("Skipping empty values for " + pubsId);
                continue;
            }

            TupleDe<Boolean, Integer> topPub = values.get(0); // topic sub-topic publisher

            // Ambil langsung sebagai TupleDe
            TupleDe<String, String> keyPub = getKeyEnc.get(pubsId);

            if (keyPub == null) {
                System.out.println("No encryption key found for publisher: " + pubsId);
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
            System.out.println("create new message for " + pubsId);
            if (sendNewMessage(msg)) {
                System.out.println("success create msg with encrypt" + msg.getProperty(MESSAGE_TOPICS_S));
                success = true;
            }
        }

        return success && super.createNewMessage(msg);
    }


    private boolean sendNewMessage(Message msg) {
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
        List<DTNHost> brokerHosts = getAllBrokers();

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
                addToMessages(msg, true); // send to broker for this new msg
            }
        }
        return true;
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
        super.messageTransferred(id, from);

        Collection<Message> msgCollection = getMessageCollection();
        if (msgCollection.isEmpty()) {
            return null;
        }

        Collection<Connection> connections = getConnections();
        if (connections == null) {
            return null;
        }

        for (Connection con : connections) {
            DTNHost other = con.getOtherNode(getHost());
            PublishAndSubscriberRouting othRouter = (PublishAndSubscriberRouting) other.getRouter();
            if (othRouter.isTransferring()) {
                continue;
            }

            for (Message msg : msgCollection) {
                if (msg == null) {
                    continue;
                }
                System.out.println("msg: " + msg);

                boolean isFinalRecipient = isFinalDest(msg, other);
                boolean isFirstDelivery = isFinalRecipient && !isDeliveredMessage(msg);

                // Jika bukan penerima akhir, tambahkan ke antrean outgoing
                if (!isFinalRecipient) {
                    addToMessages(msg, false);
                }

                // Simpan ke daftar pesan yang sudah dikirim
                if (isFirstDelivery) {
                    this.deliveredMessages.put(id, msg);
                }

                // Beri tahu listener bahwa pesan telah ditransfer
                for (MessageListener ml : this.mListeners) {
                    ml.messageTransferred(msg, from, getHost(), isFirstDelivery);
                }

                return msg;
            }
        }

        return null;
    }



    /**
     * Checks if a message has reached its final destination based on the subscriber's interest.
     *
     * @param m    The message being evaluated
     * @param host The host receiving the message
     * @return True if the message is at its final destination, false otherwise
     */
    protected boolean isFinalDest(Message m, DTNHost host) {
        Map<Boolean, TupleDe<Integer, String>> finalDestMap =
                (Map<Boolean, TupleDe<Integer, String>>) m.getProperty(MESSAGE_TOPICS_S);
        Map<DTNHost, List<TupleDe<String, String>>> getKeyAuth =
                (Map<DTNHost, List<TupleDe<String, String>>>) m.getProperty(MESSAGE_KEY_AUTHENTICATION_S);

        if (finalDestMap == null || finalDestMap.isEmpty() || getKeyAuth == null || getKeyAuth.isEmpty()) {
            return false;
        }
        System.out.println("get host " + host);
        System.out.println("get data key : " + getKeyAuth);
        System.out.println("get top pubs : " + finalDestMap);

        // Get all connections
        Collection<Connection> connections = getConnections();
        if (connections == null) {
            return false;
        }

        for (Connection con : connections) {
            DTNHost other = con.getOtherNode(host);
            CCDTN othRouter = (CCDTN) other.getRouter();
            System.out.println("other  : " + other);

            if (othRouter.isTransferring()) {
                continue;
            }
            List<Boolean> hostTopicNode = other.getSocialProfileOI();
            List<Double> hostWeightNode = other.getSocialProfile();

            if (hostTopicNode == null || hostWeightNode == null || hostTopicNode.isEmpty() || hostWeightNode.isEmpty()) {
                return false;
            }
            System.out.println("subsriber topic : " + hostTopicNode);
            System.out.println("subcriber weight : " + hostWeightNode);


            for (Map.Entry<Boolean, TupleDe<Integer, String>> entry : finalDestMap.entrySet()) {
                if (hostTopicNode.contains(entry.getKey())) {
                    double weight = hostWeightNode.get(hostTopicNode.indexOf(entry.getKey()));
                    if (weight > 0) { // Check if the topic weight is valid
//                    return authenticateSubscriber(host, entry.getValue().getSecond());
                    }
                }
            }
        }






        return false;
    }

    /**
     * Authenticates a subscriber by attempting to decrypt the received message using available keys.
     *
     * @param from      The DTNHost requesting authentication
     * @param topicName The encrypted message
     * @param keyAuth   The map containing keys for decryption
     * @return True if decryption is successful, false otherwise
     */
    protected boolean authenticateSubscriber(DTNHost from, String topicName, Map<DTNHost, List<TupleDe<String, String>>> keyAuth) {
        // Map lokal untuk melacak pesan yang sudah diterima dalam metode ini saja
        Map<DTNHost, Set<String>> receivedMessages = new HashMap<>();
        for (Map.Entry<DTNHost, List<TupleDe<String, String>>> entry : keyAuth.entrySet()) {
            DTNHost subscriberId = entry.getKey();
            List<TupleDe<String, String>> keyList = entry.getValue();

            if (keyList == null || keyList.isEmpty()) {
                continue;
            }

            for (DTNHost getSub : SimScenario.getInstance().getHosts()) {
                if (getSub.getRouter() instanceof CCDTN) {
                    if (connHistory.containsKey(subscriberId)) {
//                        System.out.println("same");
                        TupleDe<String, String> decryptedContent = DecryptUtil.decryptMessage(topicName, keyList);
//                        System.out.println("dekrip : "+decryptedContent);
                        if (decryptedContent != null && !decryptedContent.getSecond().isEmpty()) {
                            String decryptedMessage = decryptedContent.getSecond();

                            // Inisialisasi set lokal untuk subscriber jika belum ada
                            receivedMessages.putIfAbsent(subscriberId, new HashSet<>());

                            // Cek apakah subscriber sudah menerima pesan ini sebelumnya
                            if (receivedMessages.get(subscriberId).contains(decryptedMessage)) {
                                System.out.println("⚠️ DUPLICATE WARNING: Subscriber " + subscriberId + " sudah menerima pesan ini sebelumnya!");
                                continue; // Skip pesan yang duplikat
                            }

                            // Tambahkan pesan ke daftar yang sudah diterima oleh subscriber ini
                            receivedMessages.get(subscriberId).add(decryptedMessage);

                            System.out.println("🔹 Message: " + decryptedMessage);
                            return true;
                        }
                    }
                }
            }
        }

        System.out.println("❌ ERROR: No subscriber successfully decrypted the message!");
        return false;
    }


    /**
     * Determines if a host shares the same interests as a message.
     *
     * @param m    The message being evaluated
     * @param host The host to check
     * @return True if interests match, false otherwise
     */
    protected boolean isSameInterest(Message m, DTNHost host) {
        Map<Boolean, TupleDe<Integer, String>> topicMap =
                (Map<Boolean, TupleDe<Integer, String>>) m.getProperty(MESSAGE_TOPICS_S);

        List<Boolean> topicNode = host.getSocialProfileOI();

        if (topicMap == null || topicNode == null || topicNode.isEmpty()) {
            return false;  // Jika topicNode kosong, langsung return false
        }

        Iterator<Boolean> itTop = topicMap.keySet().iterator();
        int i = 0;

        while (itTop.hasNext() && i < topicNode.size()) {
            if (itTop.next().equals(topicNode.get(i))) {
                return true;
            }
            i++;
        }

        return false;
    }


    /**
     * Counts the interest weights that match the message topics.
     *
     * @param m    The message being evaluated
     * @param host The host whose interests are being compared
     * @return A list of matching interest weights
     */
    protected List<Double> countInterestTopic(Message m, DTNHost host) {
        Map<Boolean, TupleDe<Integer, String>> topicMap = (Map<Boolean, TupleDe<Integer, String>>) m.getProperty(MESSAGE_TOPICS_S);

        if (topicMap == null || topicMap.isEmpty()) {
            return null;
        }

        List<Boolean> topicNode = host.getSocialProfileOI();
        List<Double> weightNode = host.getSocialProfile();

        if (topicNode == null || weightNode == null || topicNode.size() != weightNode.size()) {
            return null;
        }

        List<Double> valInterest = new ArrayList<>();
        for (Boolean topic : topicMap.keySet()) {
            if (topicNode.contains(topic)) {
                valInterest.add(weightNode.get(topicNode.indexOf(topic)));
            }
        }

        return valInterest;
    }

    /**
     * Updates the router's state and handles message exchanges.
     */
    @Override
    public void update() {
        super.update();
        if (isTransferring() || !canStartTransfer()) {
            return; // If transferring, don't start another transfer
        }

        // Deliver messages to their final recipients
        if (exchangeDeliverableMessages() != null) {
            return;
        }

        // Try forwarding messages to all possible connections
        this.tryAllMessagesToAllConnections();

    }


    protected List<DTNHost> getAllKDCs() {
        List<DTNHost> kdcList = new ArrayList<>();
        for (DTNHost host : SimScenario.getInstance().getHosts()) { // Jika ada metode untuk mendapatkan semua host
            if (host.isKDC()) {
                kdcList.add(host);
            }
        }
        return kdcList;
    }

    protected List<DTNHost> getAllBrokers() {
        List<DTNHost> brokerList = new ArrayList<>();
        for (DTNHost host : SimScenario.getInstance().getHosts()) { // Jika ada metode untuk mendapatkan semua host
            if (host.isBroker()) {
                brokerList.add(host);
            }
        }
        return brokerList;
    }

    protected List<DTNHost> getAllPublisher() {
        List<DTNHost> publisherList = new ArrayList<>();
        for (DTNHost host : SimScenario.getInstance().getHosts()) { // Jika ada metode untuk mendapatkan semua host
            if (host.isPublisher()) {
                publisherList.add(host);
            }
        }
        return publisherList;
    }

    protected List<DTNHost> getAllSubscriber() {
        List<DTNHost> subsList = new ArrayList<>();
        for (DTNHost host : SimScenario.getInstance().getHosts()) { // Jika ada metode untuk mendapatkan semua host
            if (host.isSubscriber()) {
                subsList.add(host);
            }
        }
        return subsList;
    }


    public Message getAllMessage(Message filterMsg) {
        List<Message> messages = messageRegistry.getAllMessages();
        System.out.println("📩 Total Messages: " + messages.size());

        for (Message msg : messages) {
            if (msgMatchesFilter(msg, filterMsg)) {
                System.out.println("Message ID: " + msg.getId() + " | Content: " + msg.toString());
                return msg;
            }
        }
        return null;
    }

    public Message displayPendingMessages(Message filterMsg) {
        List<Message> pendingMessages = messageRegistry.getPendingMessages();
        System.out.println("⏳ Pending Messages: " + pendingMessages.size());

        for (Message msg : pendingMessages) {
            if (msgMatchesFilter(msg, filterMsg)) {
                System.out.println("Pending Message ID: " + msg.getId() + " | Content: " + msg.toString());
                return msg;
            }
        }
        return null;
    }

    private boolean msgMatchesFilter(Message msg, Message filterMsg) {
        return filterMsg == null || msg.getId().equals(filterMsg.getId());
    }

    /**
     * Creates a replica of this router.
     *
     * @return A new instance of CCDTN
     */
    @Override
    public MessageRouter replicate() {
        return new CCDTN(this);
    }
}

