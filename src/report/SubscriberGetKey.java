package report;

import core.DTNHost;
import core.SimClock;
import core.SimScenario;
import core.UpdateListener;
import routing.CCDTN;
import routing.PublishAndSubscriberRouting;

import java.util.*;

public class SubscriberGetKey extends Report implements UpdateListener {
    private final Map<Double, Integer> subscriberKeyCounts = new LinkedHashMap<>();
    private double lastUpdate = 0;
    private final double threshold = 300; // Interval 300 detik

    public void updated(List<DTNHost> hosts) {
        if (isWarmup()) {
            return;
        }
        double currentTime = SimClock.getTime();

        // Hanya mencatat jika sudah melewati threshold waktu
        if ((currentTime - lastUpdate) >= threshold) {
            lastUpdate = currentTime;
            int totalKeys = 0;

            for (DTNHost host : hosts) {
                if (host.getRouter() instanceof CCDTN) {
                    CCDTN router = (CCDTN) host.getRouter();
                    if (router instanceof PublishAndSubscriberRouting) {
                        PublishAndSubscriberRouting routing = (PublishAndSubscriberRouting) router;
                        Map<DTNHost, Integer> localSubscribers = routing.getKeys();

                        if (localSubscribers != null) {
                            totalKeys += localSubscribers.size(); // Jumlah subscriber keys dalam interval ini
                        }
                    }
                }
            }

            // Simpan jumlah keys dalam periode ini ke dalam LinkedHashMap (menjaga urutan waktu)
            subscriberKeyCounts.put(currentTime, totalKeys);
        }
    }

    @Override
    public void done() {
        if (subscriberKeyCounts.isEmpty()) {
            return; // Hindari menulis file kosong
        }

        StringBuilder status = new StringBuilder();
        status.append("Waktu\tJumlah Subscriber Keys\n"); // Tambahkan header agar lebih jelas

        for (Map.Entry<Double, Integer> entry : subscriberKeyCounts.entrySet()) {
            status.append(entry.getKey()).append("\t").append(entry.getValue()).append("\n");
        }

        write(status.toString());
        super.done();
    }
}
