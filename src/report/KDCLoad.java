package report;

import core.DTNHost;
import core.SimClock;
import core.UpdateListener;
import routing.CCDTN;
import routing.PublishAndSubscriberRouting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class KDCLoad extends Report implements UpdateListener {
    private final List<int[]> kdcLoadTimeline = new ArrayList<>();
    private double lastUpdate = 0;
    private final double threshold = 900;

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

            Map<DTNHost, Integer> combinedKDCLoad = new java.util.HashMap<>();

            for (DTNHost host : hosts) {
                if (host.getRouter() instanceof CCDTN) {
                    CCDTN router = (CCDTN) host.getRouter();
                    if (router instanceof PublishAndSubscriberRouting) {
                        PublishAndSubscriberRouting routing = (PublishAndSubscriberRouting) router;
                        Map<DTNHost, Integer> kdcLoads = routing.getKDCLoad();

                        if (kdcLoads != null) {
                            for (Map.Entry<DTNHost, Integer> entry : kdcLoads.entrySet()) {
                                combinedKDCLoad.merge(
                                        entry.getKey(),
                                        entry.getValue() != null ? entry.getValue() : 0,
                                        Integer::sum
                                );
                            }
                        }
                    }
                }
            }

            // Hitung total load dari seluruh KDC
            int totalLoad = combinedKDCLoad.values().stream().mapToInt(Integer::intValue).sum();

            // Simpan waktu (dalam detik dibulatkan) dan total load
            kdcLoadTimeline.add(new int[]{(int) currentTime, totalLoad});
    }

    @Override
    public void done() {
        if (kdcLoadTimeline.isEmpty()) {
            return;
        }

        StringBuilder status = new StringBuilder();
        status.append("Waktu\tJumlah Load KDC\n");

        for (int[] data : kdcLoadTimeline) {
            status.append(data[0]).append("\t").append(data[1]).append("\n");
        }

        write(status.toString());
        super.done();
    }
}
