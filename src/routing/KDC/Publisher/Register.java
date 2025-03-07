package routing.KDC.Publisher;

import core.DTNHost;
import core.Message;
import routing.KDC.Broker.GetAllBroker;
import routing.KDC.GetAllKDC;
import routing.util.TupleDe;

import java.util.*;

public class Register {

    public boolean sendMsgForRegistration(DTNHost from, Message m) {
        if (!from.isPublisher()) {
            return false;
        }

        Set<TupleDe<Boolean, Integer>> uniqueTopics = new HashSet<>();
        Map<DTNHost, List<TupleDe<Boolean, Integer>>> setTop = new HashMap<>();
        Random rand = new Random();

        int i = 0;
        while (i < 5) { // Hanya menambahkan 5 nilai unik
            boolean topicValue = rand.nextBoolean();
            int subTopicValue = rand.nextInt(29) + 1;

            TupleDe<Boolean, Integer> newTopic = new TupleDe<>(topicValue, subTopicValue);
            uniqueTopics.add(newTopic);
            i++;
        }

        GetAllKDC getAllKDCs = new GetAllKDC();
        GetAllBroker getAllBroker = new GetAllBroker();
        // get all broker
        List<DTNHost> getContactWithBroker = getAllBroker.getAllBrokers();
        // Get all KDCs
        List<DTNHost> getContactWithKDC = getAllKDCs.getAllKDCs();

        if (!getContactWithBroker.isEmpty()) {
            // if true
            setTop.put(from, new ArrayList<>(uniqueTopics));
            if (!getContactWithKDC.isEmpty()) {
                m.addProperty("KDC_Register_", setTop);
                return true;
            }
        }
        return false;
    }
}