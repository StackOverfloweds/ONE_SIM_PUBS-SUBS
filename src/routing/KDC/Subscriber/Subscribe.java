package routing.KDC.Subscriber;

import core.DTNHost;
import core.Message;
import routing.KDC.Broker.GetAllBroker;
import routing.KDC.GetAllKDC;
import routing.util.TupleDe;
import java.util.*;

public class Subscribe {

    /**
     * Handles the subscription process for a given DTNHost.
     * Checks if there are registered topics and brokers available.
     * If conditions are met, processes the subscription.
     *
     * @param m     The message object containing subscription data.
     * @param other The host that wants to subscribe.
     * @return true if subscription is successful, false otherwise.
     */
    public boolean sendMsgForSubscribe(Message m, DTNHost other) {
        if (m == null || other == null) {
            return false;
        }

        Map<DTNHost, List<TupleDe<Boolean, Integer>>> registeredTopics = getRegisteredTopics(m);
        if (registeredTopics == null || registeredTopics.isEmpty()) {
            return false;
        }

        List<Boolean> topicNode = other.getSocialProfileOI();
        List<TupleDe<Integer, Integer>> subTopics = other.getNumericAtribute();
        if (topicNode == null || subTopics == null || subTopics.isEmpty()) {
            return false;
        }

        List<DTNHost> brokers = getAllBrokers();
        List<DTNHost> kdcs = getAllKDCs();

        if (brokers.isEmpty()) {
            return false;
        }

        return processSubscription(m, other, registeredTopics, topicNode, subTopics, brokers, kdcs);
    }

    /**
     * Retrieves registered topics from the given message.
     *
     * @param m The message object.
     * @return A map containing registered topics associated with DTNHosts.
     */
    private Map<DTNHost, List<TupleDe<Boolean, Integer>>> getRegisteredTopics(Message m) {
        return (Map<DTNHost, List<TupleDe<Boolean, Integer>>>) m.getProperty("KDC_Register_");
    }

    /**
     * Retrieves all available brokers in the network.
     *
     * @return A list of DTNHost objects representing brokers.
     */
    private List<DTNHost> getAllBrokers() {
        return new GetAllBroker().getAllBrokers();
    }

    /**
     * Retrieves all available KDCs in the network.
     *
     * @return A list of DTNHost objects representing KDCs.
     */
    private List<DTNHost> getAllKDCs() {
        return new GetAllKDC().getAllKDCs();
    }

    /**
     * Processes the subscription request by matching topics between the subscriber
     * and registered topics. If a match is found, it registers the subscriber and
     * forwards the subscription to brokers and KDCs.
     *
     * @param m              The message object containing subscription data.
     * @param other          The subscribing DTNHost.
     * @param registeredTopics The list of registered topics.
     * @param topicNode      The topic profile of the subscriber.
     * @param subTopics      The numeric attributes associated with the subscriber.
     * @param brokers        The list of available brokers.
     * @param kdcs           The list of available KDCs.
     * @return true if subscription is successful, false otherwise.
     */
    private boolean processSubscription(Message m, DTNHost other,
                                        Map<DTNHost, List<TupleDe<Boolean, Integer>>> registeredTopics,
                                        List<Boolean> topicNode,
                                        List<TupleDe<Integer, Integer>> subTopics,
                                        List<DTNHost> brokers,
                                        List<DTNHost> kdcs) {
        Set<Boolean> topicSet = new HashSet<>(topicNode);
        Map<DTNHost, List<TupleDe<List<Boolean>, List<TupleDe<Integer, Integer>>>>> hostDataMap = new HashMap<>();
        TupleDe<List<Boolean>, List<TupleDe<Integer, Integer>>> tupleData = new TupleDe<>(topicNode, subTopics);

        for (Map.Entry<DTNHost, List<TupleDe<Boolean, Integer>>> entry : registeredTopics.entrySet()) {
            for (TupleDe<Boolean, Integer> tuple : entry.getValue()) {
                if (topicSet.contains(tuple.getFirst())) {
                    hostDataMap.put(other, Collections.singletonList(tupleData));
                    m.addProperty("KDC_Subscribe_", hostDataMap);

                    // **Subscriber bertemu broker, lalu addBufferToHost**
                    for (DTNHost broker : brokers) {
                        broker.addBufferToHost(m);
                    }

                    // **Broker meneruskan ke KDC jika ada**
                    if (!kdcs.isEmpty()) {
                        for (DTNHost kdc : kdcs) {
                            kdc.addBufferToHost(m);
                        }
                    }
                    return true;
                }
            }
        }

        return false;
    }
}
