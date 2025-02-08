package routing;

import core.*;
import routing.util.TupleDe;

import java.util.*;

public class PublishAndSubscriberRouting extends ContentRouter {

    protected Map<TupleDe<String, String>, List<TupleDe<Integer, Integer>>> subscribedTopics; //second tuple is for the numeric atribute and string is for flag

    private List<Double> interest;
    private List<Boolean> ownInterest;
    private List<TupleDe<Integer, Integer>> numericAttribute;
    private List<Integer> numericAttribute2;

    public PublishAndSubscriberRouting(Settings s) {
        // Call the superclass constructor to initialize inherited fields
        super(s);

        this.interest = new ArrayList<>();
        this.ownInterest = new ArrayList<>();
        this.numericAttribute = new ArrayList<>();
        this.numericAttribute2 = new ArrayList<>();
        this.subscribedTopics = new HashMap<>();
    }

    protected PublishAndSubscriberRouting(PublishAndSubscriberRouting r) {
        // Call the superclass copy constructor
        super(r);

        this.interest = new ArrayList<>(r.interest);
        this.ownInterest = new ArrayList<>(r.ownInterest);
        this.numericAttribute = new ArrayList<>(r.numericAttribute);
        this.numericAttribute2 = new ArrayList<>(r.numericAttribute2);
        this.subscribedTopics = new HashMap<>(r.subscribedTopics);
    }

    @Override
    public void changedConnection(Connection con) {
        super.changedConnection(con);
        List<DTNHost> allHosts = SimScenario.getInstance().getHosts();
        DTNHost host = getHost();

        if (con.isUp()) {
            if (host.isSubscriber()) {
                DTNHost broker = findBroker();
                if (broker != null) {
                    interest = host.getInterest();
                    ownInterest = host.getOwnInterest();
                    numericAttribute = host.getNumericAtribute();
                    numericAttribute2 = host.getNumericAtribute2();

                    // Validate lists before processing
                    if (interest == null || ownInterest == null || numericAttribute == null || numericAttribute2 == null) {
                        System.err.println("Error: One or more lists are null!");
                        return;
                    }

                    // Ensure all lists have the same size to prevent index errors
                    int minSize = Math.min(Math.min(interest.size(), ownInterest.size()),
                            Math.min(numericAttribute.size(), numericAttribute2.size()));

                    if (minSize == 0) {
                        System.err.println("Error: One or more lists are empty!");
                        return;
                    }

                    // Iterate through interests and create subscriptions
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
                                    System.err.println("Warning: numericAttrs[" + i + "] is null, skipping...");
                                    continue;
                                }
                                minValue = attr.getFirst();
                                maxValue = attr.getSecond();
                            }

                            List<TupleDe<Integer, Integer>> topicAttributes = new ArrayList<>();
                            topicAttributes.add(new TupleDe<>(minValue, maxValue));
                            String subscriberId = String.valueOf(getHost().getRouter().getHost());
                            TupleDe<String, String> w = new TupleDe<>(subscriberId, MESSAGE_TOPICS_S); // w is a topic
                            // Create a map to hold the subscriptions
                            Map<TupleDe<String, String>, List<TupleDe<Integer, Integer>>> subscriptions = new HashMap<>();

                            // Add the subscription to the map
                            subscriptions.put(w, topicAttributes);

                            // Pastikan messageId sudah didefinisikan
                            String messageId = "SUBSCRIPTION_" + host.getAddress();

                            int messageSize = subscriptions.toString().length();

                            Message subscriptionMessage = new Message(host, broker, messageId, messageSize);

                            subscriptionMessage.addProperty(MESSAGE_TOPICS_S, subscriptions);
                            System.out.println("Created subscription: " + subscriptionMessage);

                            // Send to broker
                            for (DTNHost h : allHosts) {
                                if (h.isBroker() && h.getRouter() instanceof PublishAndSubscriberRouting) {
                                    ContentRouter brokerRouter = (PublishAndSubscriberRouting) h.getRouter();
                                    System.out.println("Adding message to broker: " + brokerRouter);
                                    brokerRouter.addToMessages(subscriptionMessage, true); // msg from subscriber to broker
                                    // metode to forwarding to KDC
                                    if (SubscribeToKDC(subscriptionMessage)) {
                                        System.out.println("Subscription to KDC successful for: " + messageId);
                                    } else {
                                        System.err.println("Subscription to KDC failed for: " + messageId);
                                    }

                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Connection down - clean up subscriptions
            if (subscribedTopics != null && subscribedTopics.containsKey(true)) {
                List<TupleDe<Integer, Integer>> subs = subscribedTopics.get(true);
                subs.removeIf(sub -> sub.getSecond().equals(getHost().getRouter().getHost()));

                if (subs.isEmpty()) {
                    subscribedTopics.remove(true);
                }
            }
        }
    }

    private boolean SubscribeToKDC(Message m) {
        List<DTNHost> allHosts = SimScenario.getInstance().getHosts();

        @SuppressWarnings("unchecked")
        Map<TupleDe<String, String>, List<TupleDe<Integer, Integer>>> topicMap = new HashMap<>();
        Map<TupleDe<String, String>, ?> rawSubsMap = (Map<TupleDe<String, String>, ?>) m.getProperty(MESSAGE_TOPICS_S);

        if (rawSubsMap != null) {
            for (Map.Entry<TupleDe<String, String>, ?> entry : rawSubsMap.entrySet()) {
                if (entry.getValue() instanceof List<?>) {
                    topicMap.put(entry.getKey(), (List<TupleDe<Integer, Integer>>) entry.getValue());
                } else {
                    System.err.println("Invalid entry in the subscription map for key: " + entry.getKey());
                }
            }
        }

        if (topicMap.isEmpty()) {
            System.err.println("No topics found in the message for subscription.");
            return false;
        }

        // Validate topic map contents
        for (Map.Entry<TupleDe<String, String>, List<TupleDe<Integer, Integer>>> entry : topicMap.entrySet()) {
            List<TupleDe<Integer, Integer>> topicList = entry.getValue();
            if (topicList == null || topicList.isEmpty()) {
                System.err.println("Invalid topic list for " + entry.getKey());
                return false;
            }
        }

        boolean brokerRegistered = false;

        for (DTNHost host : allHosts) {
            if (host.isBroker() && host.getRouter() instanceof ContentRouter) {
                ContentRouter brokerRouter = (ContentRouter) host.getRouter();
                brokerRouter.addToMessages(m, false);
                brokerRegistered = true;

                // If this broker is also the KDC, forward topics to it
                if (host.isKDC() && host.getRouter() instanceof ContentRouter) {
                    ContentRouter kdcRouter = (ContentRouter) host.getRouter();

                    for (Map.Entry<TupleDe<String, String>, List<TupleDe<Integer, Integer>>> entry : topicMap.entrySet()) {
                        if (subscribedTopics == null) {
                            subscribedTopics = new HashMap<>();
                        }
                        if (!subscribedTopics.containsKey(entry.getKey())) {
                            subscribedTopics.put(entry.getKey(), new ArrayList<>());
                        }

                        subscribedTopics.get(entry.getKey()).addAll(entry.getValue());
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

        if (isTransferring() || !canStartTransfer()) {
            return; // transferring, don't try other connections yet
        }

        // Try first the messages that can be delivered to the final recipient
        if (exchangeDeliverableMessages() != null) {
            return; // started a transfer, don't try others (yet)
        }

        tryOtherMessages();
    }

    private Tuple<Message, Connection> tryOtherMessages() {
        List<Tuple<Message, Connection>> messages = new ArrayList<>();

        Collection<Message> msgCollection = getMessageCollection();
        if (msgCollection == null || msgCollection.isEmpty()) {
            System.err.println("Warning: No messages available for transfer.");
            return null;
        }

        Collection<Connection> connections = getConnections();
        if (connections == null) {
            System.err.println("Error: getConnections() is null!");
            return null;
        }

        DTNHost host = getHost();
        if (host == null) {
            System.err.println("Error: getHost() is null!");
            return null;
        }

        for (Connection con : connections) {
            DTNHost other = con.getOtherNode(host);
            if (other == null) {
                continue;
            }

            if (other.isSubscriber()) {
                System.out.printf("Get Host try: %s (Subscriber)\n", other);

                for (Message msg : msgCollection) {
                    if (msg == null || other == null) {
                        continue;
                    }

                    if (isSameInterest(msg, other)) {
                        messages.add(new Tuple<>(msg, con));
                    }
                }
            }
        }

        if (messages.isEmpty()) {
            return null;
        }

        Collections.sort(messages, new InterestSimilarityComparator());
        return tryMessagesForConnected(messages);
    }


    // Method to replicate the router
    @Override
    public MessageRouter replicate() {
        return new PublishAndSubscriberRouting(this);
    }
}
