package routing;

import KDC.NAKT.NAKTBuilder;
import KDC.Publisher.EncryptionUtil;
import core.*;
import routing.util.TupleDe;

import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.*;

public class PublishAndSubscriberRouting extends ContentRouter {

    protected Map<TupleDe<String, List<Boolean>>, List<TupleDe<Integer, Integer>>> subscribedTopics; // key for the topic and id of subscriber, value is for list of numeric atribute
    protected Map<String, List<TupleDe<String, String>>> keyEncryption;
    protected Map<String, List<TupleDe<String, String>>> keyAuthentication;
    protected Map<Integer, List<TupleDe<Boolean, String>>> registeredTopics;

    private List<Double> interest;
    private List<Boolean> ownInterest;
    private List<TupleDe<Integer, Integer>> numericAttribute;
    private List<Integer> numericAttribute2;
    private int lcnum;
    private double lastUpdateTime = 0;
    private boolean topicVall;
    private int subTopicVall;
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
        interest = new ArrayList<>();
        ownInterest = new ArrayList<>();
        numericAttribute = new ArrayList<>();
        numericAttribute2 = new ArrayList<>();
        subscribedTopics = new HashMap<>();
        keyEncryption = new HashMap<>();
        keyAuthentication = new HashMap<>();
        registeredTopics = new HashMap<>();
    }

    protected PublishAndSubscriberRouting(PublishAndSubscriberRouting r) {
        // Call the superclass copy constructor
        super(r);
        lcnum = r.lcnum;
        interest = new ArrayList<>(r.interest);
        ownInterest = new ArrayList<>(r.ownInterest);
        numericAttribute = new ArrayList<>(r.numericAttribute);
        numericAttribute2 = new ArrayList<>(r.numericAttribute2);
        subscribedTopics = new HashMap<>(r.subscribedTopics);
        keyEncryption = new HashMap<>(r.keyEncryption);
        keyAuthentication = new HashMap<>(r.keyAuthentication);
        registeredTopics = new HashMap<>(r.registeredTopics);
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
            sendToBrokerForRegistration(topicVall, subTopicVall);
            InterestCheck(otherNode);
        }
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

    private boolean sendToBrokerForRegistration(boolean topic, int subTopic) {
        if (!getHost().isPublisher() || subTopic <= 0) {
            return false;  // Pastikan subTopic valid
        }

        List<DTNHost> allHosts = SimScenario.getInstance().getHosts();
        Map<Integer, TupleDe<Boolean, String>> topicMap = new HashMap<>();

        String publisherId = String.valueOf(getHost().getRouter().getHost());
        topicMap.put(subTopic, new TupleDe<>(topic, publisherId));

        // Pastikan nilai dalam topicMap tidak ada yang null
        for (Map.Entry<Integer, TupleDe<Boolean, String>> entry : topicMap.entrySet()) {
            TupleDe<Boolean, String> tuple = entry.getValue();
            if (tuple == null || tuple.getFirst() == null || tuple.getSecond() == null) {
                System.err.println("Invalid tuple for topic " + entry.getKey());
                return false;
            }
        }

        boolean brokerRegistered = false;

        // Cari broker untuk mendaftarkan topik
        for (DTNHost host : allHosts) {
            if (host.isBroker() && host.getRouter() instanceof PublishAndSubscriberRouting) {
                PublishAndSubscriberRouting brokerRouter = (PublishAndSubscriberRouting) host.getRouter();

                if (brokerRouter.getHost().equals(host)) { // Validasi broker yang sesuai
                    brokerRegistered = true;

                    // Kirim topik ke KDC melalui broker
                    if (!forwardToKDC(topicMap, brokerRouter.getHost())) {
                        System.err.println("Failed to forward topic registration to KDC.");
                        return false;
                    }
                }
            }
        }

        if (!brokerRegistered) {
            System.err.println("No available broker to handle the registration.");
            return false;
        }
        return true;
    }

    private boolean forwardToKDC(Map<?, ?> data, DTNHost broker) {
        List<DTNHost> allHosts = SimScenario.getInstance().getHosts();

        if (allHosts == null || allHosts.isEmpty()) {
            return false;
        }

        for (DTNHost host : allHosts) {
            if (host.isKDC()) {
                Object router = host.getRouter();

                if (router instanceof PublishAndSubscriberRouting) {
                    PublishAndSubscriberRouting psRouter = (PublishAndSubscriberRouting) router;


                    if (!data.isEmpty()) {
                        Object firstValue = data.values().iterator().next();

                        if (firstValue instanceof List<?>) {
                            psRouter.addSubscriptions((Map<TupleDe<String, List<Boolean>>, List<TupleDe<Integer, Integer>>>) data);
                            return true;
                        } else if (firstValue instanceof TupleDe<?, ?>) {
                            psRouter.processTopicRegistrationAtKDC((Map<Integer, TupleDe<Boolean, String>>) data);
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean processTopicRegistrationAtKDC(Map<Integer, TupleDe<Boolean, String>> topicMap) {

        if (topicMap == null || topicMap.isEmpty()) {
            System.err.println("No topics found in the map for registration.");
            return false;
        }

        // Validate the topic map
        for (Map.Entry<Integer, TupleDe<Boolean, String>> entry : topicMap.entrySet()) {
            TupleDe<Boolean, String> tuple = entry.getValue();
            if (tuple == null || tuple.getFirst() == null || tuple.getSecond() == null) {
                System.err.println("Invalid tuple for topic " + entry.getKey());
                return false;
            }
        }

        boolean anyRegistered = false; // Flag untuk melihat apakah ada yang berhasil didaftarkan

        for (Map.Entry<Integer, TupleDe<Boolean, String>> entry : topicMap.entrySet()) {
            int subTopic = entry.getKey();
            TupleDe<Boolean, String> tuple = entry.getValue();
//            System.out.println("Processing topic: " + subTopic + " -> " + tuple);

            // Gunakan computeIfAbsent untuk lebih efisien dalam inisialisasi
            registeredTopics.computeIfAbsent(subTopic, k -> new ArrayList<>());

            // Cek apakah sudah terdaftar sebelumnya
            boolean isRegistered = registeredTopics.get(subTopic).stream().anyMatch(existingTuple -> existingTuple.getSecond().equals(tuple.getSecond()));

            if (!isRegistered) {
                registeredTopics.get(subTopic).add(tuple);
//                System.out.println("✅ Registered topic " + subTopic + " for " + tuple.getSecond());
                anyRegistered = true;
            } else {
//                System.out.println("⚠️ Already registered: " + subTopic + " -> " + tuple.getSecond());
            }
        }

        return anyRegistered; // Hanya return true jika ada yang berhasil didaftarkan
    }

    private void InterestCheck(DTNHost host) {
        // Get all hosts in the simulation
        List<DTNHost> allHosts = SimScenario.getInstance().getHosts();
        if (allHosts == null || allHosts.isEmpty()) {
            System.err.println("Error: No hosts found in the simulation.");
            return;
        }

        // Get the subscriber's interests and attributes
        interest = host.getInterest();
        ownInterest = host.getOwnInterest();
        numericAttribute = host.getNumericAtribute();
        numericAttribute2 = host.getNumericAtribute2();

        // Validate lists before processing
        if (interest == null || ownInterest == null || numericAttribute == null || numericAttribute2 == null) {
            return; // Skip if any list is null
        }
//        System.out.println("get own interest: " + ownInterest);

        // Ensure all lists have the same size to prevent index errors
        int minSize = Math.min(Math.min(interest.size(), ownInterest.size()), Math.min(numericAttribute.size(), numericAttribute2.size()));

        if (minSize == 0) {
            return; // Skip if any list is empty
        }

        // Create a map to hold the subscriptions
        Map<TupleDe<String, List<Boolean>>, List<TupleDe<Integer, Integer>>> subscriptions = new HashMap<>();

        // Iterate through interests
        for (int i = 0; i < minSize; i++) {
            if (ownInterest.get(i)) { // Only subscribe if interested
                int minValue, maxValue;

                // Handle numeric attributes
                if (numericAttribute2.get(i) != null) {
                    minValue = numericAttribute2.get(i);
                    maxValue = minValue + new Random().nextInt(5) + 1;
                } else {
                    TupleDe<Integer, Integer> attr = numericAttribute.get(i);
                    if (attr == null) {
                        continue; // Skip if attribute is null
                    }
                    minValue = attr.getFirst();
                    maxValue = attr.getSecond();
                }
                // Create a topic attribute tuple
                List<TupleDe<Integer, Integer>> topicAttributes = new ArrayList<>();
                topicAttributes.add(new TupleDe<>(minValue, maxValue));

                // Create a unique identifier for the subscriber
                String subscriberId = String.valueOf(getHost().getRouter().getHost());
                TupleDe<String, List<Boolean>> topicKey = new TupleDe<>(subscriberId, ownInterest);

                // Add the subscription to the map
                subscriptions.put(topicKey, topicAttributes);
//                System.out.println("get subs : "+subscriptions);
            }
        }

        // If there are no subscriptions, return
        if (subscriptions.isEmpty()) {
            return;
        }

        // Find the broker from all hosts
        DTNHost broker = null;
        for (DTNHost h : allHosts) {
            if (h.isBroker()) {
                broker = h;
                break;
            }
        }

        if (broker == null) {
            return;
        }

        // Send the subscription data to the broker using a Map
        if (sendSubscriptionToBroker(subscriptions, broker)) {
//            System.out.println("Subscription sent to broker: " + broker);
        } else {
            System.err.println("Failed to send subscription to broker.");
        }
    }

    private boolean sendSubscriptionToBroker(Map<TupleDe<String, List<Boolean>>, List<TupleDe<Integer, Integer>>> subscriptions, DTNHost broker) {
        if (broker == null || subscriptions == null || subscriptions.isEmpty()) {
            return false;
        }

        // Check if the broker is a valid broker
        if (!broker.isBroker()) {
            System.err.println("Error: The specified host is not a broker.");
            return false;
        }

        // Forward the subscription data to the broker
        if (broker.getRouter() instanceof PublishAndSubscriberRouting) {
            PublishAndSubscriberRouting brokerRouter = (PublishAndSubscriberRouting) broker.getRouter();

            // Add the subscription data to the broker's subscription map
            brokerRouter.addSubscriptions(subscriptions);

            // Forward the subscription to the KDC
            if (forwardToKDC(subscriptions, broker)) {
//                System.out.println("Subscription forwarded to KDC by broker: " + broker);
                return true;
            } else {
                System.err.println("Failed to forward subscription to KDC.");
                return false;
            }
        }

        return false;
    }

    private void addSubscriptions(Map<TupleDe<String, List<Boolean>>, List<TupleDe<Integer, Integer>>> subscriptions) {
        if (subscriptions == null || subscriptions.isEmpty()) {
            return;
        }

        Map<Integer, List<TupleDe<Boolean, String>>> topics = getRegisteredTopics(); // Integer = sub-topik
        if (topics == null || topics.isEmpty()) {
            return;
        }
//        System.out.println("get topics : " + topics);

        Map<TupleDe<String, List<Boolean>>, List<TupleDe<TupleDe<Boolean, Integer>, String>>> subscriberTopicMap = new HashMap<>();

        for (Map.Entry<TupleDe<String, List<Boolean>>, List<TupleDe<Integer, Integer>>> entry : subscriptions.entrySet()) {
            TupleDe<String, List<Boolean>> subscriberInfo = entry.getKey();
            List<TupleDe<Integer, Integer>> topicAttributes = entry.getValue();

            boolean topicMatches = false;
            List<TupleDe<TupleDe<Boolean, Integer>, String>> matchedTopics = new ArrayList<>();

            if (topicAttributes == null || topicAttributes.isEmpty()) {
                return;
            }
            for (TupleDe<Integer, Integer> attr : topicAttributes) {
                int minValue = attr.getFirst();
                int maxValue = attr.getSecond();

                for (Map.Entry<Integer, List<TupleDe<Boolean, String>>> regEntry : topics.entrySet()) {
                    int subTopicPublisher = regEntry.getKey();
                    List<TupleDe<Boolean, String>> registeredValues = regEntry.getValue();

                    for (TupleDe<Boolean, String> registeredValue : registeredValues) {
                        Boolean topicBoolean = registeredValue.getFirst();
                        String idPubs = registeredValue.getSecond();

                        if (subTopicPublisher >= minValue && subTopicPublisher <= maxValue && subscriberInfo.getSecond().contains(topicBoolean)) {
                            topicMatches = true;
                            matchedTopics.add(new TupleDe<>(new TupleDe<>(topicBoolean, subTopicPublisher), idPubs)); // Struktur baru
                            break;
                        }
                    }
                    if (topicMatches) break;
                }
                if (!topicMatches) {
                    continue;
                }
            }

            if (!subscribedTopics.containsKey(subscriberInfo)) {
                subscribedTopics.put(subscriberInfo, new ArrayList<>());
            }
            List<TupleDe<Integer, Integer>> existingAttributes = subscribedTopics.get(subscriberInfo);
            for (TupleDe<Integer, Integer> attribute : topicAttributes) {
                if (!existingAttributes.contains(attribute)) {
                    existingAttributes.add(attribute);
                }
            }

            // Simpan subscriber dan topik yang cocok ke dalam Map
            if (!matchedTopics.isEmpty()) {
                subscriberTopicMap.put(subscriberInfo, matchedTopics);
            }

//            System.out.println("Added subscriptions for " + subscriberTopicMap);

            // Jika sukses subscribe, buat NAKT
            NAKTBuilder nakt = new NAKTBuilder(lcnum);
            if (nakt.buildNAKT(subscriberTopicMap, existingAttributes)) {
                Map<String, List<TupleDe<String, String>>> publisherKeys = nakt.getKeysForPublisher();
                Map<String, List<TupleDe<String, String>>> subscriberKeys = nakt.getKeysForSubscriber();

                // Pastikan tidak null & tidak kosong sebelum diproses
                if (publisherKeys != null && !publisherKeys.isEmpty()) {
//                    System.out.println("🔑 Publisher Keys:");
                    for (Map.Entry<String, List<TupleDe<String, String>>> entryKey : publisherKeys.entrySet()) {
                        if (entryKey.getValue() != null && !entryKey.getValue().isEmpty()) {
//                            System.out.println("Publisher ID: " + entryKey.getKey() + " | Keys: " + entryKey.getValue());

                            // Pastikan key sudah ada, jika belum buat list baru
                            if (!keyEncryption.containsKey(entryKey.getKey())) {
                                keyEncryption.put(entryKey.getKey(), new ArrayList<>());
                            }

                            // Tambahkan elemen satu per satu
                            for (TupleDe<String, String> key : entryKey.getValue()) {
                                keyEncryption.get(entryKey.getKey()).add(key);
                            }
                        }
                    }
                } else {
//                    System.out.println("❌ No Publisher Keys available.");
                }

                if (subscriberKeys != null && !subscriberKeys.isEmpty()) {
//                    System.out.println("🔑 Subscriber Keys:");
                    for (Map.Entry<String, List<TupleDe<String, String>>> entryKey : subscriberKeys.entrySet()) {
                        if (entryKey.getValue() != null && !entryKey.getValue().isEmpty()) {
//                            System.out.println("Subscriber ID: " + entryKey.getKey() + " | Keys: " + entryKey.getValue());
                        }
                    }
                } else {
//                    System.out.println("❌ No Subscriber Keys available.");
                }
            }

        }
    }

    protected Map<Integer, List<TupleDe<Boolean, String>>> getRegisteredTopics() {
        return this.registeredTopics;
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

    public Map<TupleDe<String, List<Boolean>>, List<TupleDe<Integer, Integer>>> getSubscribedTopics() {
        return this.subscribedTopics;
    }

    // Method to replicate the router
    @Override
    public MessageRouter replicate() {
        return new PublishAndSubscriberRouting(this);
    }
}
