package KDC.Publisher;

import core.DTNHost;
import core.SimScenario;
import routing.PublishAndSubscriberRouting;
import routing.util.TupleDe;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BrokerRegistrationHandler {

    private DTNHost host; // The publisher host that initiates the registration

    /**
     * Constructor for BrokerRegistrationHandler.
     * Initializes the broker registration handler with a specific publisher host.
     *
     * @param host The DTNHost representing the publisher.
     */
    public BrokerRegistrationHandler(DTNHost host) {
        this.host = host;
    }

    /**
     * Sends a topic registration request to a broker for further processing.
     * Ensures that only valid publishers with a valid sub-topic can proceed.
     *
     * @param topic    The topic boolean flag indicating the topic category.
     * @param subTopic The sub-topic numeric identifier.
     * @return True if the registration request was successfully processed, otherwise False.
     */
    public boolean sendToBrokerForRegistration(boolean topic, int subTopic) {
        if (!host.isPublisher() || subTopic <= 0) {
            return false;  // Ensure subTopic is valid before proceeding
        }

        List<DTNHost> allHosts = SimScenario.getInstance().getHosts(); // Get all available hosts
        Map<Integer, TupleDe<Boolean, String>> topicMap = new HashMap<>();

        String publisherId = String.valueOf(host.getRouter().getHost());
        topicMap.put(subTopic, new TupleDe<>(topic, publisherId));

        // Validate that the topic map contains no null values
        for (Map.Entry<Integer, TupleDe<Boolean, String>> entry : topicMap.entrySet()) {
            TupleDe<Boolean, String> tuple = entry.getValue();
            if (tuple == null || tuple.getFirst() == null || tuple.getSecond() == null) {
                System.err.println("Invalid tuple for topic " + entry.getKey());
                return false;
            }
        }

        boolean brokerRegistered = false;

        // Search for an available broker to register the topic
        for (DTNHost brokerHost : allHosts) {
            if (brokerHost.isBroker() && brokerHost.getRouter() instanceof PublishAndSubscriberRouting) {
                PublishAndSubscriberRouting brokerRouter = (PublishAndSubscriberRouting) brokerHost.getRouter();

                if (brokerRouter.getHost().equals(brokerHost)) { // Validate that the correct broker is handling the request
                    brokerRegistered = true;
                    if (forwardToKDC(topicMap, brokerHost)) {
                        return false;
                    }
                }
            }
        }

        // If no broker was found, registration fails
        if (!brokerRegistered) {
            System.err.println("No available broker to handle the registration.");
            return false;
        }

        return true; // Registration was successful
    }

    /**
     * Forwards the provided topic registration data to the Key Distribution Center (KDC).
     * This method searches for an available KDC in the network and attempts to register
     * the given data at the KDC.
     *
     * @param data   The data to be forwarded, typically containing topic registration details.
     * @param broker The broker responsible for forwarding the data.
     * @return True if the data is successfully registered at the KDC, otherwise False.
     */
    public boolean forwardToKDC(Map<?, ?> data, DTNHost broker) {
        List<DTNHost> allHosts = SimScenario.getInstance().getHosts(); // Retrieve all network hosts

        // Validate that hosts are available in the network
        if (allHosts == null || allHosts.isEmpty()) {
            return false;
        }

        // Iterate through available hosts to find a valid KDC
        for (DTNHost host : allHosts) {
            if (host.isKDC()) { // Check if the host is a KDC
                Object router = host.getRouter();

                // Ensure the router is an instance of PublishAndSubscriberRouting
                if (router instanceof PublishAndSubscriberRouting) {
                    // Verify that the data map is not empty
                    if (!data.isEmpty()) {
                        Object firstValue = data.values().iterator().next();

                        // Ensure the first value in the data map is a TupleDe instance
                        if (firstValue instanceof TupleDe<?, ?>) {
                            // Process topic registration at KDC
                            KDCRegistrationProcessor kdcProcessor = new KDCRegistrationProcessor();
                            Map<Integer, List<TupleDe<Boolean, String>>> updatedTopics =
                                    kdcProcessor.processTopicRegistrationAtKDC((Map<Integer, TupleDe<Boolean, String>>) data);

                            // Return true if topics were successfully registered
                            return updatedTopics != null && !updatedTopics.isEmpty();
                        }
                    }
                }
            }
        }
        return false; // No valid KDC found or registration failed
    }
}
