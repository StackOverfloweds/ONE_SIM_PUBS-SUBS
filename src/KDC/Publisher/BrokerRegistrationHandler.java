package KDC.Publisher;


import core.DTNHost;
import core.SimScenario;
import routing.PublishAndSubscriberRouting;
import routing.util.TupleDe;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BrokerRegistrationHandler {

    private DTNHost host;

    public BrokerRegistrationHandler(DTNHost host) {
        this.host = host;
    }

    public boolean sendToBrokerForRegistration(boolean topic, int subTopic) {
        if (!host.isPublisher() || subTopic <= 0) {
            return false;  // Ensure subTopic is valid
        }

        List<DTNHost> allHosts = SimScenario.getInstance().getHosts();
        Map<Integer, TupleDe<Boolean, String>> topicMap = new HashMap<>();

        String publisherId = String.valueOf(host.getRouter().getHost());
        topicMap.put(subTopic, new TupleDe<>(topic, publisherId));

        // Ensure no null values in topicMap
        for (Map.Entry<Integer, TupleDe<Boolean, String>> entry : topicMap.entrySet()) {
            TupleDe<Boolean, String> tuple = entry.getValue();
            if (tuple == null || tuple.getFirst() == null || tuple.getSecond() == null) {
                System.err.println("Invalid tuple for topic " + entry.getKey());
                return false;
            }
        }

        boolean brokerRegistered = false;

        // Find a broker to register the topic
        for (DTNHost brokerHost : allHosts) {
            if (brokerHost.isBroker() && brokerHost.getRouter() instanceof PublishAndSubscriberRouting) {
                PublishAndSubscriberRouting brokerRouter = (PublishAndSubscriberRouting) brokerHost.getRouter();

                if (brokerRouter.getHost().equals(brokerHost)) { // Validate the correct broker
                    brokerRegistered = true;

                    // Forward the topic to KDC through the broker
                    KDCForwarder kdcForwarder = new KDCForwarder();
                    if (!kdcForwarder.forwardToKDC(topicMap, brokerHost)) {
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
}