package report;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import core.DTNHost;
import core.Message;
import core.MessageListener;
import core.SimScenario;
import core.UpdateListener;
import routing.util.TupleDe;

public class PublishSubsReport extends Report implements MessageListener, UpdateListener {
    private Map<String, Double> creationTimes;
    private int totalMessagesPublished;
    private int totalMessagesSubscribed;
    private int totalMessagesDelivered;
    private Map<DTNHost, Integer> messagesPublishedPerTopic;
    private Map<String, Integer> messagesDeliveredPerTopic = new HashMap<>();
    private Map<Integer, Integer> msgTopicsDelivered;

    public PublishSubsReport() {
        super();
        init();
    }

    @Override
    protected void init() {
        super.init();
        this.totalMessagesPublished = 0;
        this.totalMessagesSubscribed = 0;
        this.totalMessagesDelivered = 0;
        this.messagesPublishedPerTopic = new HashMap<>();
        this.messagesDeliveredPerTopic = new HashMap<>();
        this.creationTimes = new HashMap<>();
    }

    @Override
    public void newMessage(Message m) {
        if (isWarmup()) {
            addWarmupID(m.getId());
            return;
        }

        if (m.getProperty("topic") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<Boolean, TupleDe<Integer, String>> topics = (Map<Boolean, TupleDe<Integer, String>>) m.getProperty("topic");

            for (Map.Entry<Boolean, TupleDe<Integer, String>> entry : topics.entrySet()) {
                if (entry.getKey() != null ) {
                    DTNHost host = m.getFrom();
                    messagesPublishedPerTopic.put(host, messagesPublishedPerTopic.getOrDefault(host, 0) + 1);
                }
            }
        }
    }

    @Override
    public void messageTransferred(Message m, DTNHost from, DTNHost to, boolean finalTarget) {
        if (isWarmupID(m.getId())) {
            return;
        }
        System.out.println("test + "+finalTarget);

        if (finalTarget) {
            countMsgDelivered(to);
        }
    }

    @Override
    public void messageDeleted(Message m, DTNHost where, boolean dropped) {}

    @Override
    public void messageTransferStarted(Message m, DTNHost from, DTNHost to) {}

    @Override
    public void messageTransferAborted(Message m, DTNHost from, DTNHost to) {}

    @Override
    public void updated(List<DTNHost> hosts) {}

    private void countMsgDelivered(DTNHost host) {
        int i = 0;
        for (Boolean topic : host.getOwnInterest()) {
            System.out.println("count msg");
            if (topic) {
                int value = this.msgTopicsDelivered.getOrDefault(i, 0) + 1;
                this.msgTopicsDelivered.put(i, value);
            }
            i++;
        }
    }

    @Override
    public void done() {
        String msg = "Publish/Subscribe Report\n";
        msg += "========================\n";

        int totalPublished = 0;
        int totalDelivered = 0;

        // Menghitung total pesan yang dipublikasikan dan dikirim di semua topik
        for (Map.Entry<DTNHost, Integer> entry : messagesPublishedPerTopic.entrySet()) {
            totalPublished += entry.getValue();
        }

        for (Map.Entry<String, Integer> entry : messagesDeliveredPerTopic.entrySet()) {
            totalDelivered += entry.getValue();
        }

        // Menambahkan total di akhir laporan
        msg += "\nTotal Messages Published: " + totalPublished + "\n";
        msg += "Total Messages Delivered: " + totalDelivered + "\n";

        write(msg);
        super.done();
    }




}
