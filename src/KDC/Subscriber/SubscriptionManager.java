package KDC.Subscriber;

import core.DTNHost;
import core.SimScenario;
import routing.util.TupleDe;

import java.util.*;

public class SubscriptionManager {
    private DTNHost host;
    private BrokerHandler brokerHandler;
    private List<Double> interest;
    private List<Boolean> ownInterest;
    private List<TupleDe<Integer, Integer>> numericAttribute;
    private List<Integer> numericAttribute2;

    /**
     * Constructor for SubscriptionManager.
     * Initializes the host and broker handler for managing subscriptions.
     *
     * @param host The DTNHost object representing the subscriber.
     */
    public SubscriptionManager(DTNHost host) {
        this.host = host;
        this.brokerHandler = new BrokerHandler();
    }

    /**
     * Checks and processes the interest of the subscriber.
     * It verifies the subscriber's interest attributes and generates a subscription request
     * that is sent to a broker for further processing.
     *
     * @param host The DTNHost object representing the subscriber.
     */
    public void interestCheck(DTNHost host) {
        List<DTNHost> allHosts = SimScenario.getInstance().getHosts();
        if (allHosts == null || allHosts.isEmpty()) {
            System.err.println("Error: No hosts found in the simulation.");
            return;
        }

        List<Double> interest = host.getInterest();
        List<Boolean> ownInterest = host.getOwnInterest();
        List<TupleDe<Integer, Integer>> numericAttribute = host.getNumericAtribute();
        List<Integer> numericAttribute2 = host.getNumericAtribute2();

        // Validate that all interest attributes are not null
        if (interest == null || ownInterest == null || numericAttribute == null || numericAttribute2 == null) {
            return;
        }

        // Determine the minimum size to ensure iteration does not exceed the smallest list
        int minSize = Math.min(Math.min(interest.size(), ownInterest.size()), Math.min(numericAttribute.size(), numericAttribute2.size()));
        if (minSize == 0) {
            return;
        }

        // Stores the subscription details for topics of interest
        Map<TupleDe<String, List<Boolean>>, List<TupleDe<Integer, Integer>>> subscriptions = new HashMap<>();

        // Iterate through the interest list and generate subscription attributes
        for (int i = 0; i < minSize; i++) {
            if (ownInterest.get(i)) { // Check if the topic is of interest
                int minValue, maxValue;

                // Determine the attribute range from numeric attributes
                if (numericAttribute2.get(i) != null) {
                    minValue = numericAttribute2.get(i);
                    maxValue = minValue + new Random().nextInt(5) + 1; // Randomize range within +5 units
                } else {
                    TupleDe<Integer, Integer> attr = numericAttribute.get(i);
                    if (attr == null) continue;
                    minValue = attr.getFirst();
                    maxValue = attr.getSecond();
                }

                // Store topic attribute range
                List<TupleDe<Integer, Integer>> topicAttributes = new ArrayList<>();
                topicAttributes.add(new TupleDe<>(minValue, maxValue));

                // Generate subscription entry with subscriber ID and interest list
                String subscriberId = String.valueOf(host.getRouter().getHost());
                TupleDe<String, List<Boolean>> topicKey = new TupleDe<>(subscriberId, ownInterest);
                subscriptions.put(topicKey, topicAttributes);
            }
        }

        // If no valid subscriptions were generated, exit the method
        if (subscriptions.isEmpty()) return;

        // Find an available broker to forward the subscription request
        DTNHost broker = findBroker(allHosts);
        if (broker != null) {
            brokerHandler.sendSubscriptionToBroker(subscriptions, broker);
        }
    }

    /**
     * Finds an available broker from the list of hosts.
     *
     * @param allHosts The list of all DTNHost instances in the network.
     * @return The DTNHost object representing the broker, or null if no broker is found.
     */
    private DTNHost findBroker(List<DTNHost> allHosts) {
        for (DTNHost h : allHosts) {
            if (h.isBroker()) return h; // Return the first broker found
        }
        return null; // No broker found
    }
}
