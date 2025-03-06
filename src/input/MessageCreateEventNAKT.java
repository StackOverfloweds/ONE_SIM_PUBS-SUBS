package input;

import core.*;
import routing.KDC.NAKT.NAKTBuilder;
import routing.util.TupleDe;

import java.util.*;

public class MessageCreateEventNAKT extends MessageEvent {
    private int size;
    private int responseSize;
    private HashMap<String, Message> messages;
    protected List<MessageListener> mListeners;
    private int lcnum;
    private NAKTBuilder naktBuilder;
    private final Map<DTNHost, Set<TupleDe<Boolean, Integer>>> publisherHistory;

    /**
     * Creates a message creation event with an optional response request
     *
     * @param from         The creator of the message
     * @param to           Where the message is destined to
     * @param id           ID of the message
     * @param size         Size of the message
     * @param responseSize Size of the requested response message or 0 if
     *                     no response is requested
     * @param time         Time, when the message is created
     */
    public MessageCreateEventNAKT(int from, int to, String id, int size,
                                  int responseSize, double time, int lcnum) {
        super(from, to, id, time);
        this.size = size;
        this.responseSize = responseSize;
        this.lcnum = lcnum;
        this.naktBuilder = new NAKTBuilder(lcnum);
        this.publisherHistory = new HashMap<>();
    }

    /**
     * Creates the message this event represents.
     */
    @Override
    public void processEvent(World world) {
        DTNHost to = world.getNodeByAddress(this.toAddr);
        DTNHost from = world.getNodeByAddress(this.fromAddr);
        Message m = new Message(from, to, this.id, this.size);
        m.setResponseSize(this.responseSize);
        boolean registrationData = sendMsgForRegistration(from, m);
        boolean eventCreated = false;
        if (registrationData) {
            boolean subscribeData = sendMsgForSubscribe(m, to);
            if (subscribeData) {
                eventCreated = true;
            }
        }
        if (eventCreated) {
            naktBuilder.buildNAKT(getAllKDCs(), m); // build nakt in host kdc
        }
        from.createNewMessage(m);
    }

    private boolean sendMsgForSubscribe(Message m, DTNHost other) {
        // Retrieve registered topics and store them in a dummy variable
        Map<DTNHost, List<TupleDe<Boolean, Integer>>> getRegisteredTopic =
                (Map<DTNHost, List<TupleDe<Boolean, Integer>>>) m.getProperty("KDC_Register_");

        Map<DTNHost, List<TupleDe<Boolean, Integer>>> dummyTopic = new HashMap<>();
        if (getRegisteredTopic == null || getRegisteredTopic.isEmpty()) {
            return false; // Return empty map instead of null
        }
        dummyTopic.putAll(getRegisteredTopic); // Store registered topics in dummyTopic

        // Store social profile and numeric attributes in dummy variables
        List<Boolean> dummyTopicNode = other.getSocialProfileOI();
        List<TupleDe<Integer, Integer>> dummyGetSubTopic = other.getNumericAtribute();

        if (dummyTopicNode == null || dummyGetSubTopic == null || dummyGetSubTopic.isEmpty()) {
            return false; // Return empty map instead of null
        }

        TupleDe<List<Boolean>, List<TupleDe<Integer, Integer>>> tupleData =
                new TupleDe<>(dummyTopicNode, dummyGetSubTopic);

        Map<DTNHost, List<TupleDe<List<Boolean>, List<TupleDe<Integer, Integer>>>>> hostDataMap = new HashMap<>();

        // Iterate through dummyTopic to check if subscription topics match registered topics
        Set<Boolean> topicNodeSet = new HashSet<>(dummyTopicNode);

        for (Map.Entry<DTNHost, List<TupleDe<Boolean, Integer>>> entry : dummyTopic.entrySet()) {
            for (TupleDe<Boolean, Integer> tuple : entry.getValue()) {
                if (topicNodeSet.contains(tuple.getFirst())) { // Optimasi lookup
                    hostDataMap.put(other, Collections.singletonList(tupleData));
                    break;
                }
            }
        }
        // Get the list of KDC nodes
        List<DTNHost> getContactWithKDC = getAllKDCs();
        if (!getContactWithKDC.isEmpty()) {
            m.addProperty("KDC_Subscribe_", hostDataMap);
            return true;
        }
        return false;
    }


    private boolean sendMsgForRegistration(DTNHost from, Message m) {
        if (!from.isPublisher()) {
            return false;
        }

        Set<TupleDe<Boolean, Integer>> uniqueTopics = new HashSet<>();
        Map<DTNHost, List<TupleDe<Boolean, Integer>>> setTop = new HashMap<>();
        Random rand = new Random();

        int i=0;
        while (i < 5) { // Hanya menambahkan 5 nilai unik
            boolean topicValue = rand.nextBoolean();
            int subTopicValue = rand.nextInt(29) + 1;

            TupleDe<Boolean, Integer> newTopic = new TupleDe<>(topicValue, subTopicValue);
            uniqueTopics.add(newTopic);
            i++;
        }

        setTop.put(from, new ArrayList<>(uniqueTopics));

        // Get all KDCs
        List<DTNHost> getContactWithKDC = getAllKDCs();
        if (!getContactWithKDC.isEmpty()) {
            m.addProperty("KDC_Register_", setTop);
            return true;
        }
        return false;
    }


    private List<DTNHost> getAllKDCs() {
        List<DTNHost> kdcList = new ArrayList<>();
        for (DTNHost host : SimScenario.getInstance().getHosts()) { // Jika ada metode untuk mendapatkan semua host
            if (host.isKDC()) {
                kdcList.add(host);
            }
        }
        return kdcList;
    }


    @Override
    public String toString() {
        return super.toString() + " [" + fromAddr + "->" + toAddr + "] " +
                "size:" + size + " CREATE";
    }

}
