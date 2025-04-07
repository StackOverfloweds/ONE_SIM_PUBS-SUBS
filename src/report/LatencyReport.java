package report;

import core.*;

import java.util.*;

/**
 * LatencyReport - Report untuk mengukur latency dari publisher ke subscriber.
 * Author: Adapted by ChatGPT
 */
public class LatencyReport extends Report implements MessageListener, ConnectionListener {

    private Map<String, Double> creationTimes; // waktu saat pesan dibuat
    private List<Double> latencies; // daftar latency
    private int totalMessages;
    private List<Double> receivedTimes;

    public LatencyReport() {
        init();
    }

    @Override
    protected void init() {
        super.init();
        this.creationTimes = new HashMap<String, Double>();
        this.latencies = new ArrayList<>();
        this.totalMessages = 0;
        this.receivedTimes = new ArrayList<>();
    }

    @Override
    public void newMessage(Message m) {
        // Inisialisasi jika belum dilakukan
//        if (this.creationTimes == null) {
//            this.creationTimes = new HashMap<>();
//        }
//        if (this.latencies == null) {
//            this.latencies = new ArrayList<>();
//        }

        // simpan waktu saat pesan dibuat
        this.creationTimes.put(m.getId(), getSimTime());
    }


    @Override
    public void messageTransferred(Message m, DTNHost from, DTNHost to, boolean finalTarget) {
        if (finalTarget) {
            double createdTime = creationTimes.get(m.getId());
            double latency = getSimTime() - createdTime;
            latencies.add(latency);
            double currentTimeMs = SimClock.getTime() * 1000; //convert to ms
            receivedTimes.add(currentTimeMs);
            totalMessages++;
        }
    }

    @Override
    public void done() {
        double sum = 0, min = Double.MAX_VALUE, max = Double.MIN_VALUE;

        for (double l : latencies) {
            sum += l;
            if (l < min) min = l;
            if (l > max) max = l;
        }

        double avg = latencies.isEmpty() ? 0 : sum / latencies.size();

        StringBuilder sb = new StringBuilder();
//        sb.append("Total Messages Received: ").append(totalMessages).append("\n");
//        sb.append("Average Latency (ms): ").append(String.format("%.3f", avg * 1000)).append("\n");
//        sb.append("Min Latency (ms): ").append(String.format("%.3f", min * 1000)).append("\n");
//        sb.append("Max Latency (ms): ").append(String.format("%.3f", max * 1000)).append("\n\n");

        sb.append("Receive Time (ms)\tLatency (ms)\n");
        for (int i = 0; i < latencies.size(); i++) {
            sb.append(String.format("%.3f", receivedTimes.get(i))).append("\t")
                    .append(String.format("%.3f", latencies.get(i) * 1000)).append("\n");
        }

        write(sb.toString());
        super.done();
    }

    @Override
    public void messageDeleted(Message m, DTNHost where, boolean dropped) {
    }

    @Override
    public void messageTransferAborted(Message m, DTNHost from, DTNHost to) {
    }

    @Override
    public void messageTransferStarted(Message m, DTNHost from, DTNHost to) {
    }

    @Override
    public void hostsConnected(DTNHost host1, DTNHost host2) {
    }

    @Override
    public void hostsDisconnected(DTNHost host1, DTNHost host2) {
    }
}
