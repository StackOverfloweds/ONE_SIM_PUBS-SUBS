/*
 * @(#)ContentRouter.java
 *
 * Copyright 2025 by Bryan (HaiPigGi-StackOverfloweds)
 *
 * This class implements the routing logic for the CCDTN (Content-Centric DTN) router.
 * It handles message forwarding, subscription-based content delivery, and secure decryption.
 */

package routing;

import KDC.Subscriber.DecryptUtil;
import core.*;

import java.util.*;

import routing.community.Duration;
import routing.util.TupleDe;

public class CCDTN extends ActiveRouter {

    // Constant for storing message topics
    public static final String MESSAGE_TOPICS_S = "topic";
    public static final String MESSAGE_REGISTER_S = "KDC_Register_";
    public static final String MESSAGE_SUBSCRIBE_S = "KDC_Subscribe_";


    // Maps to store connection timestamps and history
    protected Map<DTNHost, Double> startTimestamps;
    protected Map<DTNHost, List<Duration>> connHistory;

    /**
     * Constructor: Initializes CCDTN with settings.
     *
     * @param s Settings object for configuration
     */
    public CCDTN(Settings s) {
        super(s);
        this.startTimestamps = new HashMap<>();
        this.connHistory = new HashMap<>();
    }

    /**
     * Copy constructor: Creates a deep copy of an existing CCDTN router.
     *
     * @param c The CCDTN instance to copy
     */
    protected CCDTN(CCDTN c) {
        super(c);
        startTimestamps = new HashMap<>(c.startTimestamps);
        connHistory = new HashMap<>(c.connHistory);
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
     * Checks if a message has reached its final destination based on the subscriber's interest.
     *
     * @param m       The message being evaluated
     * @param host    The host receiving the message
     * @param keyAuth The map containing authentication keys for subscribers
     * @return True if the message is at its final destination, false otherwise
     */
    protected boolean isFinalDest(Message m, DTNHost host, Map<DTNHost, List<TupleDe<String, String>>> keyAuth) {
        Map<Boolean, TupleDe<Integer, String>> finalDestMap = (Map<Boolean, TupleDe<Integer, String>>) m.getProperty(MESSAGE_TOPICS_S);

//        System.out.println("FINAL DEST MAP: " + finalDestMap);
        if (finalDestMap == null || finalDestMap.isEmpty()) {
            return false;
        }

        List<Boolean> hostTopicNode = host.getSocialProfileOI();
        List<Double> hostWeightNode = host.getSocialProfile();
        System.out.println("halo top");

        if (hostTopicNode == null || hostWeightNode == null || hostTopicNode.isEmpty() || hostWeightNode.isEmpty()) {
            return false;
        }


        for (Map.Entry<Boolean, TupleDe<Integer, String>> entry : finalDestMap.entrySet()) {
            if (hostTopicNode.contains(entry.getKey())) {
                double weight = hostWeightNode.get(hostTopicNode.indexOf(entry.getKey()));
                if (weight > 0) { // Check if the topic weight is valid
                    return authenticateSubscriber(host, entry.getValue().getSecond(), keyAuth);
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
        Map<Boolean, TupleDe<Integer, String>> topicMap = (Map<Boolean, TupleDe<Integer, String>>) m.getProperty(MESSAGE_TOPICS_S);
        List<Boolean> topicNode = host.getSocialProfileOI();
        if (topicMap == null || topicNode == null) {
            return false;
        }

        Iterator<Boolean> itTop = topicMap.keySet().iterator();
        int i = 0;
        while (itTop.hasNext()) {
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

    protected Message addToBufferForKDCToRegistration(DTNHost kdcHost, Map<DTNHost, TupleDe<Boolean, Integer>> topicEntry) {
        if (!kdcHost.isKDC()) {
            return null;
        }

        // Generate a new message
        String msgId = "KDC_REGISTER_" + System.currentTimeMillis();
        DTNHost sender = getHost(); // The node that registered the topic

        // Calculate message size dynamically from topicEntry
        int msgSize = topicEntry.size();

        makeRoomForMessage(msgSize);
        // Create a new message with the correct constructor
        Message newMessage = new Message(sender, kdcHost, msgId, msgSize);
        // Add message to buffer using addToMessages()
        newMessage.addProperty(MESSAGE_REGISTER_S, topicEntry);
        addToMessages(newMessage, false);
//        System.out.println("✅ Successfully added registration message to KDC buffer: " + msgId);
        return newMessage;
    }

    protected Message addToBufferForKDCToSubscribe(DTNHost kdcHost, Map<DTNHost,TupleDe<Boolean, TupleDe<Integer, Integer>>> topicEntry) {
        if (!kdcHost.isKDC()) {
            return null;
        }

        // Generate a new message
        String msgId = "KDC_SUBSCRIBE_" + System.currentTimeMillis();
        DTNHost sender = getHost(); // The subscriber who registered the topic

        // Calculate message size dynamically from topicEntry
        int msgSize = topicEntry.size();
        // Ensure buffer has enough space before adding
        makeRoomForMessage(msgSize);
        // Create a new message with the correct constructor
        Message newMessage = new Message(sender, kdcHost, msgId, msgSize);
        newMessage.addProperty(MESSAGE_SUBSCRIBE_S, topicEntry);
        addToMessages(newMessage, false);
//        System.out.println("✅ Successfully added subscription message to KDC buffer: " + msgId);
        return newMessage;
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
