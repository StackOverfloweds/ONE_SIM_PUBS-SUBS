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
import KDC.Subscriber.DecryptUtil;
import KDC.Subscriber.SubscriptionManager;
import core.*;
import routing.util.TupleDe;

import java.util.*;


public class PublishAndSubscriberRouting extends CCDTN {

    public static Map<TupleDe<String, List<Boolean>>, List<TupleDe<Integer, Integer>>> subscribedTopics; // key for the topic and id of subscriber, value is for list of numeric atribute
    public static Map<Integer, List<TupleDe<Boolean, String>>> registeredTopics;
    public static Map<String, TupleDe<String, String>> keyEncryption;
    public static Map<String, TupleDe<String, String>> keyAuthentication;
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
        Map<String, TupleDe<String, String>> tempKeyAuth = brokerHandler.getKeyAuthentication();
        if (tempKeyAuth != null) {
            for (Map.Entry<String, TupleDe<String, String>> entry : tempKeyAuth.entrySet()) { // Deep copy the list
                keyAuthentication.put(entry.getKey(), entry.getValue());
            }
        }
    }

    protected PublishAndSubscriberRouting(PublishAndSubscriberRouting r) {
        // Call the superclass copy constructor
        super(r);
        lcnum = r.lcnum;

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
            for (Map.Entry<String, TupleDe<String, String>> entry : r.keyAuthentication.entrySet()) {
                keyAuthentication.put(entry.getKey(), entry.getValue());
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

        Map<Integer, List<TupleDe<Boolean,String>>> registered = registeredTopics;
        if (registered == null || registered.isEmpty()) {
            return false;
        }

        // Iterasi melalui registeredTopics untuk memeriksa pendaftaran host
        for (Map.Entry<Integer, List<TupleDe<Boolean, String>>> entry : registered.entrySet()) {
            List<TupleDe<Boolean, String>> valueList = entry.getValue();
//            System.out.println("get");
            for (TupleDe<Boolean, String> tuple : valueList) {
                if (tuple.getSecond().equals(hostId)) {
                    System.out.println("nice");
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

            String randomMessage = EncryptionUtil.generateRandomString(20); // 20 karakter
            System.out.println("check random msg " + randomMessage);

            String keyEncrypt = keys.getSecond(); // Gunakan key dari Tuple

            String hashedMessage = EncryptionUtil.hashWithHmacSHA256(randomMessage, keyEncrypt);

            Map<Boolean, TupleDe<Integer, String>> messageData = new HashMap<>();
            TupleDe<Integer, String> value = new TupleDe<>(subTopicVall, hashedMessage);
            messageData.put(topicVall, value);

            // **5. Tambahkan ke properti message**
            makeRoomForMessage(msg.getSize());
            msg.setTtl(this.msgTtl);
            msg.addProperty(MESSAGE_TOPICS_S, messageData);
            addToMessages(msg, true);
            System.out.println("success create msg "+msg.getProperty(MESSAGE_TOPICS_S));
            return super.createNewMessage(msg);
        }
        return false;
    }


    /**
     * metode in routing publishAndSubscriberRouting
     *
     * @param id   Id of the transferred message
     * @param from Host the message was from (previous hop)
     * @return
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

        Message outgoing = incoming;
        for (Application app : getApplications(incoming.getAppID())) {
            outgoing = app.handle(outgoing, getHost());
            if (outgoing == null) {
                break; // Pesan dihentikan oleh aplikasi
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
        boolean isFinalRecipient = isFinalDest(aMessage, getHost(),keyAuthentication);
        boolean isFirstDelivery = isFinalRecipient && !isDeliveredMessage(aMessage);

        if (aMessage != null && !isFinalRecipient) {
            addToMessages(aMessage, false);
        }

        if (isFirstDelivery) {
            this.deliveredMessages.put(id, aMessage);
        }

        for (MessageListener ml : this.mListeners) {
            ml.messageTransferred(aMessage, from, getHost(), isFirstDelivery);
        }

        // Mengecek Topic dan Key Authentication
        Message msg = aMessage;
        Map<Boolean, TupleDe<Integer, String>> topicMap = getTopicMap(msg);

        if (topicMap == null || topicMap.isEmpty()) {
            return null;
        }

        List<Boolean> ownInterest = getHost().getOwnInterest();
        if (ownInterest == null || ownInterest.isEmpty()) {
            return null;
        }

        // Loop melalui topicMap dan bandingkan hanya boolean
        for (Map.Entry<Boolean, TupleDe<Integer, String>> entry : topicMap.entrySet()) {
            Boolean topicBoolean = entry.getKey();
            String topicName = entry.getValue().getSecond();

            if (ownInterest.contains(topicBoolean)) {
                boolean isSubscriberMatched = authenticateSubscriber(from, topicName, keyAuthentication);
                if (isSubscriberMatched) {
                    addToMessages(msg, false);
                    System.out.println("Message added to subscriber buffer.");
                    return aMessage;
                } else {
                    System.out.println("No matching subscriber found for decryption.");
                    return null;
                }
            } else {
                System.out.println("No matching interest found for topic: " + topicName);
                return null;
            }
        }

        return null;
    }
    /**
     * Method is called just before a transfer is finalized
     * at {@link #update()}.
     * Subclasses that are interested of the event may want to override this.
     * @param con The connection whose transfer was finalized
     */
    @Override
    public void transferDone(Connection con) {
        // Retrieve the message ID and the message object from the connection
        String msgId = con.getMessage().getId();
        Message msg = getMessage(msgId);

        if (msg == null) {
            return;
        }

        // Check if the current host is the final recipient of the message
        boolean isFinalRecipient = isFinalDest(msg, getHost(), keyAuthentication);

        // If the host is not the final recipient, add the message to the buffer for further forwarding
        if (!isFinalRecipient) {
            addToMessages(msg, false);
        }

        // Check if the message has already been delivered
        boolean isDelivered = isDeliveredMessage(msg);

        // If the message has not been delivered, handle delivery confirmation
        if (!isDelivered) {
            handleDeliveryConfirmation(msg);
        }

        // Notify all message listeners about the transfer completion
        for (MessageListener ml : mListeners) {
            ml.messageTransferred(msg, con.getOtherNode(getHost()), getHost(), isDelivered);
        }
    }

    private void handleDeliveryConfirmation(Message msg) {
        msg.updateProperty(MESSAGE_TOPICS_S, getTopicMap(msg));
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
