package routing.KDC.Publisher;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.SimScenario;
import routing.KDC.GetAllKDC;
import routing.PublishAndSubscriberRouting;
import routing.util.TupleDe;

import java.util.*;

public class Register {

    /**
     * Sends a registration message if the host is a publisher.
     * Registers the publisher with a set of unique topics and sends the data to KDC if brokers exist.
     *
     * @param host The DTNHost attempting to register.
     * @param m    The message to which registration data will be added.
     * @return true if registration is successful, false otherwise.
     */
    public boolean sendMsgForRegistration(DTNHost host, Message m) {
        if (!host.isPublisher()) {
            return false;
        }
        Collection<Connection> connections = host.getConnections();
        if (connections == null) {
            return false;
        }
        Set<TupleDe<Boolean, Integer>> uniqueTopics = generateUniqueTopics();
        Map<DTNHost, List<TupleDe<Boolean, Integer>>> setTop = new HashMap<>();
        setTop.put(host, new ArrayList<>(uniqueTopics));
        for (Connection con : connections) {
            DTNHost other = con.getOtherNode(host);
            if (other != null && other.isBroker()) {
                m.addProperty("KDC_Register_", setTop);
                addMessageToHosts(m, other);
                // Call the new method to handle sending to KDCs
                return sendToKDCs(m);
            }
        }
        return false;
    }

    /**
     * Sends a message to KDCs if the broker is a KDC.
     *
     * @param m      The message to send.
     * @return true if message sent to KDCs, false otherwise.
     */
    private boolean sendToKDCs(Message m) {
        for (DTNHost host : SimScenario.getInstance().getHosts()) {
            for (Connection con : host.getConnections()) {
                DTNHost other = con.getOtherNode(host);
                if (other != null && other.isKDC()) {
                    addMessageToHosts(m, other);
                    return true;
                }
            }
        }
        return false;
    }


    /**
     * Generates a set of unique topics, each represented by a boolean and an integer value.
     * The set contains exactly 5 unique elements.
     *
     * @return A set of unique topic tuples.
     */
    private Set<TupleDe<Boolean, Integer>> generateUniqueTopics() {
        Set<TupleDe<Boolean, Integer>> uniqueTopics = new HashSet<>();
        Random rand = new Random();

        while (uniqueTopics.size() < 5) {
            boolean topicValue = rand.nextBoolean();
            int subTopicValue = rand.nextInt(29) + 1;
            uniqueTopics.add(new TupleDe<>(topicValue, subTopicValue));
        }

        return uniqueTopics;
    }

    /**
     * Adds the given message to the buffers of the provided hosts.
     *
     * @param m     The message to be added.
     * @param hosts The list of hosts to which the message should be added.
     */
    private void addMessageToHosts(Message m, DTNHost hosts) {
        hosts.addBufferToHost(m);
    }
}
