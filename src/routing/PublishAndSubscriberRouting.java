package routing;

import KDC.NAKT.NAKTBuilder;
import com.sun.org.apache.xpath.internal.operations.Bool;
import core.*;
import routing.util.TupleDe;

import java.util.*;

public class PublishAndSubscriberRouting extends ContentRouter {

    protected Map<TupleDe<String, List<Boolean>>, List<TupleDe<Integer, Integer>>> subscribedTopics; // key for the topic and id of subscriber, value is for list of numeric atribute

    private List<Double> interest;
    private List<Boolean> ownInterest;
    private List<TupleDe<Integer, Integer>> numericAttribute;
    private List<Integer> numericAttribute2;
    private int lcnum;

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
    }

    @Override
    public void changedConnection(Connection con) {
        super.changedConnection(con);
        DTNHost host = getHost();
        DTNHost otherNode = con.getOtherNode(host);
        if (con.isUp()) {
            // Connection is up
            InterestCheck(otherNode);
        }
    }

//    private void KDCheck(DTNHost otherNode) {
//        List<DTNHost> allHosts = SimScenario.getInstance().getHosts();
//
//        // Check if hosts list is valid
//        if (allHosts == null || allHosts.isEmpty()) {
//            System.err.println("❌ Error: No hosts found in the simulation.");
//            return;
//        }
//
//        // Ensure time has elapsed before performing check
//        if ((SimClock.getTime() - lastUpdateTime) < updateInterval) {
//
//            for (DTNHost host : allHosts) {
//                if (host == null) {
//                    System.err.println("Warning: Encountered a null host in the simulation scenario.");
//                    continue;
//                }
//
//                if (host.isKDC()) {
//                    // Check in KDC if that have topic registered
//                    if (CheckRegisterAndSubs()) {

    /// /                        System.out.println("Register True && Subscribe True");
//                    }
//
//
//                }
//
//            }
//
//        }
//    }
//
//    public Boolean CheckRegisterAndSubs() {
//        Map<Integer, List<TupleDe<Boolean, String>>> topics = getRegisteredTopics();
//        Map<TupleDe<String, List<Boolean>>, List<TupleDe<Integer, Integer>>> subscribedTopics = getSubscribedTopics();
//        if (topics == null || subscribedTopics == null) {
//            return false;
//        }
//        return true;
//    }
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
        int minSize = Math.min(Math.min(interest.size(), ownInterest.size()),
                Math.min(numericAttribute.size(), numericAttribute2.size()));

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
                String subscriberId = String.valueOf(host.getAddress());
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
            System.err.println("Error: No broker found to send subscription.");
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

    private boolean forwardToKDC(Map<TupleDe<String, List<Boolean>>, List<TupleDe<Integer, Integer>>> subscriptions, DTNHost broker) {
        List<DTNHost> allHosts = SimScenario.getInstance().getHosts();

        // Check if allHosts is null or empty
        if (allHosts == null || allHosts.isEmpty()) {
            System.err.println("Error: No hosts found in the simulation scenario.");
            return false;
        }

        // Find the KDC
        for (DTNHost host : allHosts) {
            if (host.isKDC() && host.getRouter() instanceof PublishAndSubscriberRouting) {
                PublishAndSubscriberRouting kdcRouter = (PublishAndSubscriberRouting) host.getRouter();

                // Forward the subscription data to the KDC
                kdcRouter.addSubscriptions(subscriptions);
//                System.out.println("Subscription forwarded to KDC: " + host);
                return true;
            }
        }

        System.err.println("Error: No KDC found to forward subscription.");
        return false;
    }

    private void addSubscriptions(Map<TupleDe<String, List<Boolean>>, List<TupleDe<Integer, Integer>>> subscriptions) {
        if (subscriptions == null || subscriptions.isEmpty()) {
            return;
        }

        Map<Integer, List<TupleDe<Boolean, String>>> topics = getRegisteredTopics(); // Integer = sub-topik
        if (topics == null) {
            return;
        }

        Map<TupleDe<String, List<Boolean>>, List<TupleDe<TupleDe<Boolean, Integer>, String>>> subscriberTopicMap = new HashMap<>();

        for (Map.Entry<TupleDe<String, List<Boolean>>, List<TupleDe<Integer, Integer>>> entry : subscriptions.entrySet()) {
            TupleDe<String, List<Boolean>> subscriberInfo = entry.getKey();
            List<TupleDe<Integer, Integer>> topicAttributes = entry.getValue();

            boolean topicMatches = false;
            List<TupleDe<TupleDe<Boolean, Integer>, String>> matchedTopics = new ArrayList<>();

            for (TupleDe<Integer, Integer> attr : topicAttributes) {
                int minValue = attr.getFirst();
                int maxValue = attr.getSecond();

                for (Map.Entry<Integer, List<TupleDe<Boolean, String>>> regEntry : topics.entrySet()) {
                    int registeredTopic = regEntry.getKey();
                    List<TupleDe<Boolean, String>> registeredValues = regEntry.getValue();

                    for (TupleDe<Boolean, String> registeredValue : registeredValues) {
                        Boolean topicBoolean = registeredValue.getFirst();
                        String idPubs = registeredValue.getSecond();

                        if (registeredTopic >= minValue && registeredTopic <= maxValue && subscriberInfo.getSecond().contains(topicBoolean)) {
                            topicMatches = true;
                            matchedTopics.add(new TupleDe<>(new TupleDe<>(topicBoolean, registeredTopic), idPubs)); // Struktur baru
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

            // Jika sukses subscribe, buat NAKT
            NAKTBuilder nakt = new NAKTBuilder(lcnum);
            nakt.buildNAKT(subscriberTopicMap, existingAttributes);
        }
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
