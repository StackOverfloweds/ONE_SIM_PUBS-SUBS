package routing.KDC.Publisher;

import core.DTNHost;
import core.Message;
import routing.KDC.Broker.GetAllBroker;
import routing.KDC.GetAllKDC;
import routing.util.TupleDe;

import java.util.*;

public class Register {

    /**
     * Sends a registration message if the host is a publisher.
     * Registers the publisher with a set of unique topics and sends the data to KDC if brokers exist.
     *
     * @param from The DTNHost attempting to register.
     * @param m    The message to which registration data will be added.
     * @return true if registration is successful, false otherwise.
     */
    public boolean sendMsgForRegistration(DTNHost from, Message m) {
        if (!from.isPublisher()) {
            return false;
        }

        List<DTNHost> brokers = getAllBrokers();
        if (brokers.isEmpty()) {
            return false;
        }

        List<DTNHost> kdcs = getAllKDCs();
        Set<TupleDe<Boolean, Integer>> uniqueTopics = generateUniqueTopics();
        Map<DTNHost, List<TupleDe<Boolean, Integer>>> setTop = new HashMap<>();
        setTop.put(from, new ArrayList<>(uniqueTopics));

        m.addProperty("KDC_Register_", setTop);
        addMessageToHosts(m, brokers);
        addMessageToHosts(m, kdcs);

        return !kdcs.isEmpty();
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
     * Retrieves a list of all available brokers.
     *
     * @return A list of DTNHost instances representing brokers.
     */
    private List<DTNHost> getAllBrokers() {
        return new GetAllBroker().getAllBrokers();
    }

    /**
     * Retrieves a list of all available KDCs (Key Distribution Centers).
     *
     * @return A list of DTNHost instances representing KDCs.
     */
    private List<DTNHost> getAllKDCs() {
        return new GetAllKDC().getAllKDCs();
    }

    /**
     * Adds the given message to the buffers of the provided hosts.
     *
     * @param m     The message to be added.
     * @param hosts The list of hosts to which the message should be added.
     */
    private void addMessageToHosts(Message m, List<DTNHost> hosts) {
        for (DTNHost host : hosts) {
            host.addBufferToHost(m);
        }
    }
}
