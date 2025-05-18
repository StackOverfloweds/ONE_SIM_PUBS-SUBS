package report;

import core.DTNHost;
import core.SimScenario;
import routing.CCDTN;
import routing.PublishAndSubscriberRouting;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PublisherGetKey extends Report {

    @Override
    public void done() {
        List<DTNHost> hosts = SimScenario.getInstance().getHosts();
        Map<DTNHost, Integer> aggregatedKeys = new HashMap<>();
        int totalKeys = 0;

        write("Rata-Rata Total Key Per Publisher\n");

        for (DTNHost host : hosts) {
            if (host.getRouter() instanceof CCDTN) {
                CCDTN router = (CCDTN) host.getRouter();
                if (router instanceof PublishAndSubscriberRouting) {
                    PublishAndSubscriberRouting routing = (PublishAndSubscriberRouting) router;
                    Map<DTNHost, Integer> localKeys = routing.getKeysPublisher();

                    if (localKeys != null) {
                        for (Map.Entry<DTNHost, Integer> entry : localKeys.entrySet()) {
                            DTNHost publisher = entry.getKey();
                            Integer numberKey = entry.getValue();

                            // Aggregate keys per publisher
                            aggregatedKeys.merge(publisher, numberKey, Integer::sum);

                            // Add to totalKeys
                            totalKeys += numberKey;
                        }
                    }
                }
            }
        }
        int totalPublisher = aggregatedKeys.size();
        int averageKeysPerSubscriber = totalPublisher > 0
                ? totalKeys / totalPublisher
                : 0;
        write("Total Publisher: " + totalPublisher + "\n");
        write("Total Keys: " + totalKeys + "\n");
        write("Average Keys per Subscriber: " + averageKeysPerSubscriber + "\n");

        super.done();
    }
}