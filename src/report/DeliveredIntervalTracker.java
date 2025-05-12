package report;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import core.DTNHost;
import core.Message;
import core.UpdateListener;
import core.SimClock;

public class DeliveredIntervalTracker extends Report implements UpdateListener {
    private Map<String, Double> creationTimes;
    private List<Double> latencies;
    private int nrofResponseReqCreated;

    private double lastRecordedTime;
    private int deliveredAtInterval;
    private int createdAtInterval;

    private int nrofCreated;
    private int nrofDelivered;
    private int nrofRelayed;
    private List<Integer> hopCounts;
    private List<Double> rtt; // round trip times
    private int nrofResponseDelivered;

    private List<String> deliveredPerInterval;
    private static final double INTERVAL = 100.0;

    public DeliveredIntervalTracker() {
        init();
    }

    @Override
    protected void init() {
        super.init();
        this.lastRecordedTime = 0.0;
        this.deliveredAtInterval = 0;
        this.createdAtInterval = 0;
        this.nrofCreated = 0;
        this.nrofDelivered = 0;
        this.nrofResponseReqCreated = 0;
        this.nrofRelayed = 0;
        this.nrofResponseDelivered = 0;

        this.deliveredPerInterval = new ArrayList<String>();
        this.creationTimes = new HashMap<String, Double>();
        this.latencies = new ArrayList<Double>();
        this.hopCounts = new ArrayList<Integer>();

    }

    public void newMessage(Message m) {
        if (isWarmup()) {
            addWarmupID(m.getId());
            return;
        }

        this.creationTimes.put(m.getId(), getSimTime());
        this.nrofCreated++;
        if (m.getResponseSize() > 0) {
            this.nrofResponseReqCreated++;
        }
    }
    public void messageTransferred(Message m, DTNHost from, DTNHost to,
                                   boolean finalTarget) {
        if (isWarmupID(m.getId())) {
            return;
        }

        this.nrofRelayed++;
        if (finalTarget) {
            this.latencies.add(getSimTime() - this.creationTimes.get(m.getId()));
            this.nrofDelivered++;
            this.hopCounts.add(m.getHops().size() - 1);

            if (m.isResponse()) {
                this.rtt.add(getSimTime() - m.getRequest().getCreationTime());
                this.nrofResponseDelivered++;
            }
        }
    }
    @Override
    public void updated(List<DTNHost> hosts) {
        double now = SimClock.getTime();
        if (now - lastRecordedTime >= INTERVAL) {
            lastRecordedTime = now;
            // Hitung deliveryProb untuk interval ini
            double intervalProb = (nrofCreated > 0) ?
                    (1.0 * nrofDelivered) / nrofCreated : 0.0;

            String log = String.format(
                    "DeliveryProb %.4f",intervalProb);

            deliveredPerInterval.add(log);
        }
    }

    @Override
    public void done() {
        write("\n=== Delivery Report Per Interval ===");
        for (String entry : deliveredPerInterval) {
            write(entry);
        }
        super.done();
    }
}
