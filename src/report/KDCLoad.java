package report;

import core.DTNHost;
import core.SimClock;
import core.UpdateListener;
import routing.CCDTN;
import routing.PublishAndSubscriberRouting;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class KDCLoad extends Report implements UpdateListener {
    private final Map<Double, Integer> KDCLoad = new LinkedHashMap<>();
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
            int loadKDC = 0;

            for (DTNHost host : hosts) {
                if (host.getRouter() instanceof CCDTN) {
                    CCDTN router = (CCDTN) host.getRouter();
                    if (router instanceof PublishAndSubscriberRouting) {
                        PublishAndSubscriberRouting routing = (PublishAndSubscriberRouting) router;
                        Map<DTNHost, Integer> kdcLoads = routing.getKDCLoad();

                        if (kdcLoads != null) {
                            loadKDC += kdcLoads.size(); // Jumlah subscriber keys dalam interval ini
                        }
                    }
                }
            }

            // Simpan jumlah keys dalam periode ini ke dalam LinkedHashMap (menjaga urutan waktu)
            KDCLoad.put(currentTime, loadKDC);
        }
    }

    @Override
    public void done() {
        if (KDCLoad.isEmpty()) {
            return; // Hindari menulis file kosong
        }

        StringBuilder status = new StringBuilder();
        status.append("Waktu\tJumlah load kdc\n"); // Tambahkan header agar lebih jelas

        for (Map.Entry<Double, Integer> entry : KDCLoad.entrySet()) {
            status.append(entry.getKey()).append("\t").append(entry.getValue()).append("\n");
        }

        write(status.toString());
        super.done();
    }
}