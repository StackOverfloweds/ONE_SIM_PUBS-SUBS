package report;

import core.DTNHost;
import core.SimScenario;
import routing.CCDTN;
import routing.PublishAndSubscriberRouting;

import java.util.*;
import java.io.*;

public class SubscriberGetKey extends Report {

    @Override
    public void done() {
        List<DTNHost> hosts = SimScenario.getInstance().getHosts();
        Map<DTNHost, Integer> aggregatedKeys = new HashMap<>();
        int totalKeys = 0;

        write("Rata-Rata Total Key Per Subscriber\n");

        for (DTNHost host : hosts) {
            if (host.getRouter() instanceof CCDTN) {
                CCDTN router = (CCDTN) host.getRouter();
                if (router instanceof PublishAndSubscriberRouting) {
                    PublishAndSubscriberRouting routing = (PublishAndSubscriberRouting) router;
                    Map<DTNHost, Integer> localKeys = routing.getKeys();

                    if (localKeys != null) {
                        for (Map.Entry<DTNHost, Integer> entry : localKeys.entrySet()) {
                            DTNHost subscriber = entry.getKey();
                            Integer numberKey = entry.getValue();

                            // Aggregate keys per subscriber
                            aggregatedKeys.merge(subscriber, numberKey, Integer::sum);

                            // Add to totalKeys
                            totalKeys += numberKey;
                        }
                    }
                }
            }
        }
        int totalSubscribers = aggregatedKeys.size();
        int averageKeysPerSubscriber = totalSubscribers > 0
                ? totalKeys / totalSubscribers
                : 0;
        write("Average Keys per Subscriber: " + averageKeysPerSubscriber + "\n");

        super.done();
    }
}