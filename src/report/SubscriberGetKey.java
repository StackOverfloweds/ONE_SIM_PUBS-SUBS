package report;

import core.DTNHost;
import core.SimClock;
import core.UpdateListener;
import routing.CCDTN;
import routing.PublishAndSubscriberRouting;

import java.util.*;

public class SubscriberGetKey extends Report implements UpdateListener {
    private final List<int[]> subscriberKeyTimeline = new ArrayList<>();
    private double lastUpdate = 0;
    private final double threshold = 900; //30 minutes

    @Override
    public void updated(List<DTNHost> hosts) {
        if (isWarmup()) {
            return;
        }

        double currentTime = SimClock.getTime();
        if ((currentTime - lastUpdate) < threshold) {
            return;
        }
            lastUpdate = currentTime;

            Map<DTNHost, Integer> maxKeyPerSubscriber = new HashMap<>();

            for (DTNHost host : hosts) {
                if (host.getRouter() instanceof CCDTN) {
                    CCDTN router = (CCDTN) host.getRouter();
                    if (router instanceof PublishAndSubscriberRouting) {
                        PublishAndSubscriberRouting routing = (PublishAndSubscriberRouting) router;
                        Map<DTNHost, Integer> localKeys = routing.getKeys();

                        if (localKeys != null && !localKeys.isEmpty()) {
                            for (Map.Entry<DTNHost, Integer> entry : localKeys.entrySet()) {
                                DTNHost subscriber = entry.getKey();
                                int count = entry.getValue();
                                maxKeyPerSubscriber.merge(subscriber, count, Math::max);
                            }
                        }
                    }
                }
            }

            int totalSubscribers = maxKeyPerSubscriber.size();
            int totalKeys = maxKeyPerSubscriber.values().stream().mapToInt(Integer::intValue).sum();

            subscriberKeyTimeline.add(new int[]{totalSubscribers, totalKeys});
    }

    @Override
    public void done() {
        if (subscriberKeyTimeline.isEmpty()) {
            return;
        }

        StringBuilder status = new StringBuilder();
        status.append("Total Subscribers\tKeys\n");

        for (int i = 0; i < subscriberKeyTimeline.size(); i++) {
            int[] data = subscriberKeyTimeline.get(i);
            status.append("\t").append(data[0]).append("\t").append(data[1]).append("\n");
        }

        write(status.toString());
        super.done();
    }
}
