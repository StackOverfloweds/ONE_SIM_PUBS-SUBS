package routing;

import core.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class PublishAndSubscriberRouting extends ContentRouter {

    /** namespace settings ({@value}) */
    private static final String PNS_NS = "PublishAndSubscriberRouting";
    /** nilai update interval atur di settings */

    public PublishAndSubscriberRouting(Settings s) {
        // Call the superclass constructor to initialize inherited fields
        super(s);
        Settings pns = new Settings(PNS_NS); 

    }

    protected PublishAndSubscriberRouting(PublishAndSubscriberRouting r) {
        // Call the superclass copy constructor
        super(r);


    }

    /**
     * Comparator untuk sorting message berdasarkan
     * Interest Similarity tertinggi
     * Sort DESC
     */
    private class InterestSimilarityComparator implements Comparator<Tuple<Message, Connection>> {
        @Override
        public int compare(Tuple<Message, Connection> tuple1, Tuple<Message, Connection> tuple2) {
            double d1 = sumList(countInterestTopic(tuple1.getKey(), tuple1.getValue().getOtherNode(getHost())));
            double d2 = sumList(countInterestTopic(tuple2.getKey(), tuple2.getValue().getOtherNode(getHost())));

            return Double.compare(d2, d1);
        }
    }

    // Helper method to sum list of interests (probabilities or matching scores)
    private double sumList(List<Double> lists) {
        double total = 0.0;
        for (double lst : lists) {
            total += lst;
        }
        return total;
    }


    @Override
    public void update() {
        super.update();

        if (isTransferring() || !canStartTransfer()) {
            return; // transferring, don't try other connections yet
        }

        // Try first the messages that can be delivered to the final recipient
        if (exchangeDeliverableMessages() != null) {
            return; // started a transfer, don't try others (yet)
        }

        tryOtherMessages();
    }

    private Tuple<Message, Connection> tryOtherMessages() {
        List<Tuple<Message, Connection>> messages = new ArrayList<>();

        Collection<Message> msgCollection = getMessageCollection();

        // Try to collect message-connection pairs that are eligible for transfer
        for (Connection con : getConnections()) {
            DTNHost other = con.getOtherNode(getHost());

            // Collect the messages that can be transferred based on interest
            for (Message msg : msgCollection) {
                if (isSameInterest(msg, other)) {
                    messages.add(new Tuple<>(msg, con));
                }
            }
        }

        if (messages.size() == 0) {
            return null;
        }

        // Sort the message-connection tuples based on interest similarity
        Collections.sort(messages, new InterestSimilarityComparator());
        return tryMessagesForConnected(messages); // Try to send messages
    }

    // Method to replicate the router
    @Override
    public MessageRouter replicate() {
        return new PublishAndSubscriberRouting(this);
    }
}
