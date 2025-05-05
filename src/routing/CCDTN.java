/*
 * @(#)ContentRouter.java
 *
 * Copyright 2025 by Bryan (HaiPigGi-StackOverfloweds)
 *
 * This class implements the routing logic for the CCDTN (Content-Centric DTN) router.
 * It handles message forwarding, subscription-based content delivery, and secure decryption.
 */

package routing;

import routing.KDC.Broker.GetAllBroker;
import routing.KDC.NAKT.KeyManager;
import routing.KDC.Subscriber.DecryptUtil;
import core.*;

import java.util.*;

import routing.community.Duration;
import routing.util.TupleDe;

public class CCDTN extends ActiveRouter {

    // Constant for storing message topics
    public static final String MESSAGE_TOPICS_S = "topic";
    public static final String MESSAGE_REGISTER_S = "KDC_Register_";
    public static final String MESSAGE_KEY_ENCRYPTION_S = "KDC_Key_Encryption_";
    public static final String MESSAGE_KEY_AUTHENTICATION_S = "KDC_Key_Authentication_";

    public static Map<DTNHost, Integer> kdcLoad;
    public static Map<DTNHost, Integer> numberKeyLoad;
    public static Map<DTNHost, Integer> numberKeyLoadPublisher;
    // Maps to store connection timestamps and history
    protected Map<DTNHost, Double> startTimestamps;
    protected Map<DTNHost, List<Duration>> connHistory;

    protected KeyManager keyManager;
    protected GetAllBroker getAllBroker;
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
        this.kdcLoad = new HashMap<>();
        this.numberKeyLoad = new HashMap<>();
        this.numberKeyLoadPublisher = new HashMap<>();
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
        kdcLoad = new HashMap<>(c.kdcLoad);
        numberKeyLoad = new HashMap<>(c.numberKeyLoad);
        numberKeyLoadPublisher = new HashMap<>(c.numberKeyLoadPublisher);
    }

    private void initNAKT() {
        this.keyManager = new KeyManager();
        this.messageRegistry = new MessageRegistryImpl();
        this.getAllBroker = new GetAllBroker();
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
        incoming.setReceiveTime(SimClock.getTime());

        Message outgoing = incoming;
        for (Application app : getApplications(incoming.getAppID())) {
            outgoing = app.handle(outgoing, getHost());
            if (outgoing == null) {
                break;
            }
        }

        Message aMessage = (outgoing == null) ? incoming : outgoing;
        boolean isFinalRecipient = isFinalDest(aMessage, getHost());
        boolean isFirstDelivery = isFinalRecipient && !isDeliveredMessage(aMessage);

        // Jika bukan penerima akhir, tambahkan ke antrean outgoing
        if (outgoing != null && !isFinalRecipient) {
            addToMessages(aMessage, false);
        }
        if (isFirstDelivery) {
            this.deliveredMessages.put(id, aMessage);
        }
        for (MessageListener ml : this.mListeners) {
            ml.messageTransferred(aMessage, from, getHost(), isFirstDelivery);
        }

        return aMessage;
    }

    /**
     * Checks if a message has reached its final destination based on the subscriber's interest.
     *
     * @param m    The message being evaluated
     * @param host The host receiving the message
     * @return True if the message is at its final destination, false otherwise
     */
    protected boolean isFinalDest(Message m, DTNHost host) {
        Map<Boolean, TupleDe<Integer, String>> finalDestMap = (Map<Boolean, TupleDe<Integer, String>>) m.getProperty(MESSAGE_TOPICS_S);


        if (finalDestMap == null || finalDestMap.isEmpty()) {
            return false;
        }

        // create dummy for add msg
        Map<Boolean, TupleDe<Integer, String>> dummyMSGTOP = finalDestMap;

        // Get all connections
        Collection<Connection> connections = getConnections();
        if (connections == null) {
            return false;
        }

        for (Connection con : connections) {
            DTNHost other = con.getOtherNode(host);
            CCDTN othRouter = (CCDTN) other.getRouter();

            if (othRouter.isTransferring()) {
                continue;
            }
            List<Boolean> hostTopicNode = other.getSocialProfileOI();
            Map<DTNHost, List<TupleDe<String, String>>> getKeyAuth = (Map<DTNHost, List<TupleDe<String, String>>>) m.getProperty(MESSAGE_KEY_AUTHENTICATION_S);

            if (getKeyAuth == null || getKeyAuth.isEmpty()) {
                return false;
            }


            Map<DTNHost, List<TupleDe<String, String>>> dummyKEYAUTH = getKeyAuth;

            if (hostTopicNode == null || hostTopicNode.isEmpty()) {
                return false;
            }
            for (Map.Entry<Boolean, TupleDe<Integer, String>> entry : dummyMSGTOP.entrySet()) {
                if (hostTopicNode.contains(entry.getKey())) {
//                    System.out.println("its matched baybeee");
                    return authenticateSubscriber(entry.getValue().getSecond(), dummyKEYAUTH);
                }
            }
        }
        return false;
    }

    /**
     * Authenticates a subscriber by attempting to decrypt the received message using available keys.
     *
     * @param msgEncrypt The encrypted message
     * @param keyAuth    The map containing keys for decryption
     * @return True if decryption is successful, false otherwise
     */
    private boolean authenticateSubscriber(String msgEncrypt, Map<DTNHost, List<TupleDe<String, String>>> keyAuth) {
        // Map lokal untuk melacak pesan yang sudah diterima dalam metode ini saja
        Map<DTNHost, Set<String>> receivedMessages = new HashMap<>();
        for (Map.Entry<DTNHost, List<TupleDe<String, String>>> entry : keyAuth.entrySet()) {
            DTNHost subscriberId = entry.getKey();
            List<TupleDe<String, String>> keyList = entry.getValue();

            if (keyList == null || keyList.isEmpty()) {
                continue;
            }

            for (DTNHost host : SimScenario.getInstance().getHosts()) {
                    if (host.equals(subscriberId)) {
                        TupleDe<String, String> decryptedContent = DecryptUtil.decryptMessage(msgEncrypt, keyList);
//                        System.out.println("dekrip : "+decryptedContent);
                        if (decryptedContent != null && !decryptedContent.getSecond().isEmpty()) {
                            String decryptedMessage = decryptedContent.getSecond();

                            // Inisialisasi set lokal untuk subscriber jika belum ada
                            receivedMessages.putIfAbsent(subscriberId, new HashSet<>());

                            // Cek apakah subscriber sudah menerima pesan ini sebelumnya
                            if (receivedMessages.get(subscriberId).contains(decryptedMessage)) {
//                                System.out.println("⚠️ DUPLICATE WARNING: Subscriber " + subscriberId + " sudah menerima pesan ini sebelumnya!");
                                continue; // Skip pesan yang duplikat
                            }

                            // Tambahkan pesan ke daftar yang sudah diterima oleh subscriber ini
                            receivedMessages.get(subscriberId).add(decryptedMessage);

//                            System.out.println("🔹 Message: " + decryptedMessage);
                            return true;
                        }
                    }

            }
        }

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
        Map<Boolean, TupleDe<Integer, String>> topicMap = (Map<Boolean, TupleDe<Integer, String>>) m.getProperty(MESSAGE_TOPICS_S);

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

