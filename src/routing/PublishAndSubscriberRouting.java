package routing;

import core.*;
import routing.util.TupleDe;

import java.util.*;

public class PublishAndSubscriberRouting extends ContentRouter {

    protected Map<TupleDe<String, String>, List<TupleDe<Integer, Integer>>> subscribedTopics;

    private List<Double> interest;
    private List<Boolean> ownInterest;
    private List<TupleDe<Integer, Integer>> numericAttribute;
    private List<Integer> numericAttribute2;

    private double lastUpdateTime = 0;
    private double updateInterval;

    /**
     * namespace settings ({@value})
     */
    private static final String PUBSROUTING_NS = "PublishAndSubscriberRouting";
    private static final String UPDATE_INTERVAL = "updateInterval";

    public PublishAndSubscriberRouting(Settings s) {
        // Call the superclass constructor to initialize inherited fields
        super(s);
        Settings ccSettings = new Settings(PUBSROUTING_NS);
        updateInterval = ccSettings.getInt(UPDATE_INTERVAL);

        interest = new ArrayList<>();
        ownInterest = new ArrayList<>();
        numericAttribute = new ArrayList<>();
        numericAttribute2 = new ArrayList<>();
        subscribedTopics = new HashMap<>();
    }

    protected PublishAndSubscriberRouting(PublishAndSubscriberRouting r) {
        // Call the superclass copy constructor
        super(r);
        updateInterval = r.updateInterval;
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
            SubscribeTopic(otherNode);
        }
    }

    private void SubscribeTopic(DTNHost host) {
        System.out.println("Subscribing to " + host);

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

        // Ensure all lists have the same size to prevent index errors
        int minSize = Math.min(Math.min(interest.size(), ownInterest.size()),
                Math.min(numericAttribute.size(), numericAttribute2.size()));

        if (minSize == 0) {
            return; // Skip if any list is empty
        }

        // Create a map to hold the subscriptions
        Map<TupleDe<String, String>, List<TupleDe<Integer, Integer>>> subscriptions = new HashMap<>();

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
                TupleDe<String, String> topicKey = new TupleDe<>(subscriberId, MESSAGE_TOPICS_S);

                // Add the subscription to the map
                subscriptions.put(topicKey, topicAttributes);
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
            System.out.println("Subscription sent to broker: " + broker);
        } else {
            System.err.println("Failed to send subscription to broker.");
        }
    }

    private boolean sendSubscriptionToBroker(Map<TupleDe<String, String>, List<TupleDe<Integer, Integer>>> subscriptions, DTNHost broker) {
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
                System.out.println("Subscription forwarded to KDC by broker: " + broker);
                return true;
            } else {
                System.err.println("Failed to forward subscription to KDC.");
                return false;
            }
        }

        return false;
    }

    private boolean forwardToKDC(Map<TupleDe<String, String>, List<TupleDe<Integer, Integer>>> subscriptions, DTNHost broker) {
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
                System.out.println("Subscription forwarded to KDC: " + host);
                return true;
            }
        }

        System.err.println("Error: No KDC found to forward subscription.");
        return false;
    }

    private void addSubscriptions(Map<TupleDe<String, String>, List<TupleDe<Integer, Integer>>> subscriptions) {
        if (subscriptions == null || subscriptions.isEmpty()) {
            return;
        }

        // Add the subscriptions to the broker's subscription map
        for (Map.Entry<TupleDe<String, String>, List<TupleDe<Integer, Integer>>> entry : subscriptions.entrySet()) {
            TupleDe<String, String> subscriberInfo = entry.getKey();
            List<TupleDe<Integer, Integer>> topicAttributes = entry.getValue();

            // Add the subscription to the subscribedTopics map
            if (!subscribedTopics.containsKey(subscriberInfo)) {
                subscribedTopics.put(subscriberInfo, new ArrayList<>());
            }
            subscribedTopics.get(subscriberInfo).addAll(topicAttributes);
        }

        System.out.println("Subscriptions added to broker: " + subscriptions);
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

        // Periodic update for KDC to handle topic registration and subscription
        if ((SimClock.getTime() - lastUpdateTime) >= updateInterval) {
            lastUpdateTime = SimClock.getTime();
            List<DTNHost> allHosts = SimScenario.getInstance().getHosts();

            // Check if allHosts is null or empty
            if (allHosts == null || allHosts.isEmpty()) {
                System.err.println("Error: No hosts found in the simulation scenario.");
                return;
            }

            // Iterate through all hosts to find the KDC
            for (DTNHost host : allHosts) {
                if (host == null) {
                    System.err.println("Warning: Encountered a null host in the simulation scenario.");
                    continue;
                }

                // Check if the host is a KDC and has a ContentRouter
                if (host.isKDC() && host.getRouter() instanceof PublishAndSubscriberRouting) {
                    PublishAndSubscriberRouting kdcRouter = (PublishAndSubscriberRouting) host.getRouter();

                    // Check registered topics
                    Map<Integer, List<TupleDe<List<Boolean>, String>>> registeredTopics = kdcRouter.getRegisteredTopics();
                    if (registeredTopics == null || registeredTopics.isEmpty()) {
                        return;
                    } else {
                        System.out.println("KDC has registered topics:");
                        for (Map.Entry<Integer, List<TupleDe<List<Boolean>, String>>> entry : registeredTopics.entrySet()) {
                            System.out.println("Topic ID: " + entry.getKey());
                            for (TupleDe<List<Boolean>, String> tuple : entry.getValue()) {
                                System.out.println("  Publisher ID: " + tuple.getSecond());
                                System.out.println("  Topic Values: " + tuple.getFirst());
                            }
                        }
                    }

                    // Check subscribed topics
                    Map<TupleDe<String, String>, List<TupleDe<Integer, Integer>>> subscribedTopics = kdcRouter.getSubscribedTopics();
                    if (subscribedTopics == null || subscribedTopics.isEmpty()) {
                        return;
                    } else {
                        System.out.println("KDC has subscribed topics:");
                        for (Map.Entry<TupleDe<String, String>, List<TupleDe<Integer, Integer>>> entry : subscribedTopics.entrySet()) {
                            System.out.println("Subscriber ID: " + entry.getKey().getFirst());
                            for (TupleDe<Integer, Integer> tuple : entry.getValue()) {
                                System.out.println("  Numeric Attribute: " + tuple.getFirst() + " - " + tuple.getSecond());
                            }
                        }
                    }
                }
            }
        }
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

                    // Check if the message has the MESSAGE_TOPICS_S property
                    if (msg.getProperty(MESSAGE_TOPICS_S) != null) {
                        if (isSameInterest(msg, other)) {
                            messages.add(new Tuple<>(msg, con));
                        }
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

    public Map<TupleDe<String, String>, List<TupleDe<Integer, Integer>>> getSubscribedTopics() {
        return subscribedTopics;
    }

    // Method to replicate the router
    @Override
    public MessageRouter replicate() {
        return new PublishAndSubscriberRouting(this);
    }
}
