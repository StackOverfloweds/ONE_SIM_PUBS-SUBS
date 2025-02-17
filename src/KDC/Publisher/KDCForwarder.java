package KDC.Publisher;

import core.DTNHost;
import core.SimScenario;
import routing.PublishAndSubscriberRouting;
import routing.util.TupleDe;

import java.util.List;
import java.util.Map;

public class KDCForwarder {

    public boolean forwardToKDC(Map<?, ?> data, DTNHost broker) {
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
                        if (firstValue instanceof TupleDe<?, ?>) {
                            // Process the topic registration at KDC
                            KDCRegistrationProcessor kdcProcessor = new KDCRegistrationProcessor();
                            Map<Integer, List<TupleDe<Boolean, String>>> updatedTopics =
                                    kdcProcessor.processTopicRegistrationAtKDC((Map<Integer, TupleDe<Boolean, String>>) data);

                            // Return true if topics were successfully registered (the map is updated)
                            return updatedTopics != null && !updatedTopics.isEmpty();
                        }
                    }
                }
            }
        }
        return false;
    }
}
