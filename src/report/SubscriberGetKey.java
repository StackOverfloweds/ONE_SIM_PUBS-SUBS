package report;

import core.DTNHost;
import core.SimScenario;
import routing.CCDTN;
import routing.PublishAndSubscriberRouting;

import java.util.*;

public class SubscriberGetKey extends Report  {
    private Map<String, List<Integer>> subscribers;

    protected void init() {
        super.init();
        this.subscribers = new HashMap<>();
    }

    @Override
    public void done() {
        List<DTNHost> hosts = SimScenario.getInstance().getHosts();
        Map<DTNHost, List<Integer>> groupedSubscribers = new HashMap<>();

        for (DTNHost host : hosts) {
            if (host.getRouter() instanceof CCDTN) {
                CCDTN router = (CCDTN) host.getRouter();
                if (router instanceof PublishAndSubscriberRouting) {
                    PublishAndSubscriberRouting routing = (PublishAndSubscriberRouting) router;
                    Map<DTNHost, Integer> localSubscribers = routing.getKeys();

                    if (localSubscribers != null) {
                        for (Map.Entry<DTNHost, Integer> entry : localSubscribers.entrySet()) {
                            groupedSubscribers
                                    .computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                                    .add(entry.getValue());
                        }
                    }
                }
            }
        }

        // Konversi data menjadi string untuk ditulis
        StringBuilder text = new StringBuilder();
        for (Map.Entry<DTNHost, List<Integer>> entry : groupedSubscribers.entrySet()) {
            text.append(entry.getKey()).append("\t").append(entry.getValue().size()).append("\n");
        }

        write(text.toString());
        super.done();
    }
}
