package report;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import core.DTNHost;
import core.Message;
import core.MessageListener;
import core.UpdateListener;

public class PublishSubsReport extends Report implements MessageListener, UpdateListener {
    private Map<String, Double> creationTimes;
    private List<Double> latencies;
    private List<Integer> hopCounts;
    private List<Double> msgBufferTime;
    private List<Double> rtt; // round trip times

    private int nrofDropped;
    private int nrofRemoved;
    private int nrofStarted;
    private int nrofAborted;
    private int nrofRelayed;
    private int nrofCreated;
    private int nrofResponseReqCreated;
    private int nrofResponseDelivered;
    private int nrofDelivered;

    @Override
    protected void init() {
        super.init();
        this.creationTimes = new HashMap<String, Double>();
        this.latencies = new ArrayList<Double>();
        this.msgBufferTime = new ArrayList<Double>();
        this.hopCounts = new ArrayList<Integer>();
        this.rtt = new ArrayList<Double>();

        this.nrofDropped = 0;
        this.nrofRemoved = 0;
        this.nrofStarted = 0;
        this.nrofAborted = 0;
        this.nrofRelayed = 0;
        this.nrofCreated = 0;
        this.nrofResponseReqCreated = 0;
        this.nrofResponseDelivered = 0;
        this.nrofDelivered = 0;
    }

    public PublishSubsReport() {
        super();
        init();
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

    public void messageDeleted(Message m, DTNHost where, boolean dropped) {
        if (isWarmupID(m.getId())) {
            return;
        }

        if (dropped) {
            this.nrofDropped++;
        }
        else {
            this.nrofRemoved++;
        }

        this.msgBufferTime.add(getSimTime() - m.getReceiveTime());
    }

    public void messageTransferStarted(Message m, DTNHost from, DTNHost to) {
        if (isWarmupID(m.getId())) {
            return;
        }

        this.nrofStarted++;
    }

    public void messageTransferAborted(Message m, DTNHost from, DTNHost to) {
        if (isWarmupID(m.getId())) {
            return;
        }

        this.nrofAborted++;
    }

    public void updated(List<DTNHost> hosts) {}


    @Override
    public void done() {
        // Header untuk laporan
        write("=====================================================");
        write("Message Delivery Report for Scenario: " + getScenarioName());
        write("Simulation Time: " + format(getSimTime()));
        write("=====================================================");

        // Hitung metrik utama
        double deliveryProb = (this.nrofCreated > 0) ?
                (1.0 * this.nrofDelivered) / this.nrofCreated : 0.0;
        double responseProb = (this.nrofResponseReqCreated > 0) ?
                (1.0 * this.nrofResponseDelivered) / this.nrofResponseReqCreated : 0.0;
        double overHead = (this.nrofDelivered > 0) ?
                (1.0 * (this.nrofRelayed - this.nrofDelivered)) / this.nrofDelivered : Double.NaN;

        // Tampilkan statistik dasar
        write("\n=== Basic Message Statistics ===");
        write("Messages Created: " + this.nrofCreated);
        write("Messages Started: " + this.nrofStarted);
        write("Messages Relayed: " + this.nrofRelayed);
        write("Messages Aborted: " + this.nrofAborted);
        write("Messages Dropped: " + this.nrofDropped);
        write("Messages Removed: " + this.nrofRemoved);
        write("Messages Delivered: " + this.nrofDelivered);

        // Tampilkan probabilitas dan overhead
        write("\n=== Delivery Metrics ===");
        write("Delivery Probability: " + format(deliveryProb));
        write("Overhead Ratio: " + format(overHead));

        // Footer untuk laporan
        write("=====================================================");
        write("End of Report");
        write("=====================================================");

        // Panggil metode done() dari superclass
        super.done();
    }




}
