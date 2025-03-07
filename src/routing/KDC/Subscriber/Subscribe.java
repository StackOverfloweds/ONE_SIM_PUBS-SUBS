package routing.KDC.Subscriber;

import core.DTNHost;
import core.Message;
import routing.KDC.Broker.GetAllBroker;
import routing.KDC.GetAllKDC;
import routing.util.TupleDe;
import java.util.*;

public class Subscribe {

    public boolean sendMsgForSubscribe(Message m, DTNHost other) {
        // Ambil daftar topik yang sudah terdaftar
        Map<DTNHost, List<TupleDe<Boolean, Integer>>> registeredTopics =
                (Map<DTNHost, List<TupleDe<Boolean, Integer>>>) m.getProperty("KDC_Register_");

        if (registeredTopics == null || registeredTopics.isEmpty()) {
            return false;
        }

        List<Boolean> topicNode = other.getSocialProfileOI();
        List<TupleDe<Integer, Integer>> subTopics = other.getNumericAtribute();

        if (topicNode == null || subTopics == null || subTopics.isEmpty()) {
            return false;
        }

        TupleDe<List<Boolean>, List<TupleDe<Integer, Integer>>> tupleData =
                new TupleDe<>(topicNode, subTopics);

        List<DTNHost> brokers = new GetAllBroker().getAllBrokers();
        List<DTNHost> kdcs = new GetAllKDC().getAllKDCs();

        if (brokers.isEmpty()) {
            return false;
        }

        Set<Boolean> topicSet = new HashSet<>(topicNode);
        Map<DTNHost, List<TupleDe<List<Boolean>, List<TupleDe<Integer, Integer>>>>> hostDataMap = new HashMap<>();

        // Iterasi melalui daftar topik yang terdaftar
        for (Map.Entry<DTNHost, List<TupleDe<Boolean, Integer>>> entry : registeredTopics.entrySet()) {
            for (TupleDe<Boolean, Integer> tuple : entry.getValue()) {
                if (topicSet.contains(tuple.getFirst())) { // Jika topik cocok

                    // Masukkan data ke broker**
                    hostDataMap.put(other, Collections.singletonList(tupleData));

                    // Jika KDC ada, tambahkan data ke pesan**
                    if (!kdcs.isEmpty()) {
                        m.addProperty("KDC_Subscribe_", hostDataMap);
                    }

                    return true;
                }
            }
        }
        return false;
    }

}