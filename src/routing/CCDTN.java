/*
 * @(#)ContentRouter.java
 *
 * Copyright 2025 by Bryan (HaiPigGi-StackOverfloweds)
 *
 */
package routing;

import KDC.Subscriber.BrokerHandler;
import KDC.Subscriber.DecryptUtil;
import com.sun.org.apache.xpath.internal.operations.Bool;
import core.*;

import java.util.*;

import routing.community.Duration;
import routing.util.TupleDe;

public class CCDTN extends ActiveRouter {

    // Create some initial variables
    public static final String MESSAGE_TOPICS_S = "topic";
    protected Map<DTNHost, Double> startTimestamps;
    protected Map<DTNHost, List<Duration>> connHistory;

    public CCDTN(Settings s) {
        super(s);
        this.startTimestamps = new HashMap<>();
        this.connHistory = new HashMap<>();
    }

    // Copy constructor
    protected CCDTN(CCDTN c) {
        super(c);
        startTimestamps = new HashMap<>(c.startTimestamps);  // Deep copy of maps
        connHistory = new HashMap<>(c.connHistory);           // Deep copy of maps

    }

    @Override
    public void changedConnection(Connection con) {
        DTNHost peer = con.getOtherNode(getHost());

        if (con.isUp()) {
            CCDTN othRouter = (CCDTN) peer.getRouter();
            this.startTimestamps.put(peer, SimClock.getTime());
            othRouter.startTimestamps.put(getHost(), SimClock.getTime());
        } else {
            if (startTimestamps.containsKey(peer)) {
                double time = startTimestamps.get(peer);
                double etime = SimClock.getTime();

                // Find or create the connection history list
                List<Duration> history;
                if (!connHistory.containsKey(peer)) {
                    history = new LinkedList<>();
                    connHistory.put(peer, history);
                } else history = connHistory.get(peer);

                // add this connection to the list
                if (etime - time > 0) history.add(new Duration(time, etime));

                startTimestamps.remove(peer);
            }
        }
    }


    protected boolean isFinalDest(Message m, DTNHost host,  Map<String, List<TupleDe<String, String>>> keyAuth) {
        // Cek apakah host sudah subscribe ke final destination
        Map<Boolean, TupleDe<Integer, String>> finalDestMap = getTopicMap(m);


        if (finalDestMap == null || finalDestMap.isEmpty()) {
            return false;
        }


        List<Boolean> hostTopicNode = host.getOwnInterest();
        List<Double> hostWeightNode = host.getInterest();

        if (hostTopicNode == null) hostTopicNode = new ArrayList<>();
        if (hostWeightNode == null) hostWeightNode = new ArrayList<>();

        if (hostTopicNode.isEmpty() || hostWeightNode.isEmpty()) {
            return false;
        }


        for (Map.Entry<Boolean, TupleDe<Integer, String>> entry : finalDestMap.entrySet()) {
            List<Boolean> topicMsg = new LinkedList<>();
            topicMsg.add(entry.getKey());
            TupleDe<Integer, String> topic = entry.getValue();
            String getMsg = topic.getSecond();


            for (Boolean top : topicMsg) {
                if (top == null) {
                    return false;
                }
                // Cek apakah topik ini ada di hostTopicNode
                if (hostTopicNode.contains(top)) {
                    double weight = hostWeightNode.get(hostTopicNode.indexOf(entry.getKey()));
                    if (weight > 0) { // Jika host sudah memiliki weight > 0 untuk topik ini
                        boolean isSubscriberMatched = authenticateSubscriber(host, getMsg, keyAuth);
                        if (isSubscriberMatched) {
                            return true;
                        }
                        else {
                            return false;
                        }

                    }
                }
            }
        }

        return false;
    }

    protected boolean authenticateSubscriber(DTNHost from, String topicName, Map<String, List<TupleDe<String, String>>> keyAuth) {
        for (Map.Entry<String, List<TupleDe<String, String>>> entry : keyAuth.entrySet()) {
            String subscriberId = entry.getKey();
            List<TupleDe<String, String>> keyList = entry.getValue();

            if (keyList == null || keyList.isEmpty()) {
                continue;
            }

            for (DTNHost getSub : SimScenario.getInstance().getHosts()) {
                String hostId = String.valueOf(getSub.getRouter().getHost());

                if (getSub.isSubscriber() && getHost().getRouter() instanceof CCDTN && subscriberId.contains(hostId)) {
                    String decryptedContent = DecryptUtil.decryptMessage(topicName, keyList);

                    if (decryptedContent != null) {
                        System.out.println("✅ Final Decryption Success: " + decryptedContent);
                        return true;
                    }
                }
            }
        }

        return false;
    }






    protected Map<Boolean, TupleDe<Integer, String>> getTopicMap(Message msg) {
        try {
            return (Map<Boolean, TupleDe<Integer, String>>) msg.getProperty(MESSAGE_TOPICS_S);
        } catch (ClassCastException e) {
            System.out.println("Error: MESSAGE_TOPICS_S property is not valid.");
            return null;
        }
    }

    protected boolean isSameInterest(Message m, DTNHost host) {
        // Get the topics from the message
        Map<Boolean, TupleDe<Integer, String>> topicMap = getTopicMap(m);

        if (topicMap == null || topicMap.isEmpty()) {
            return false;
        }

        // Get the host's interests
        List<Boolean> topicNode = host.getOwnInterest();

        if (topicNode == null) {
            return false;
        }

        // Iterate through the topic map and check for matches
        for (Map.Entry<Boolean, TupleDe<Integer, String>> entry : topicMap.entrySet()) {
                List<Boolean> topicMsgList = new LinkedList<>();
                topicMsgList.add(entry.getKey());

                for (Boolean top : topicMsgList) {
                    if (topicNode.contains(top)) {
                        return true;
                    }
                }
        }

        return false; // No match found
    }


    protected List<Double> countInterestTopic(Message m, DTNHost host) {
        Map<Boolean, TupleDe<Integer, String>> topicMap = getTopicMap(m);
        if (topicMap == null || topicMap.isEmpty()) {
            return null;
        }

        // Get the host's interests and weights
        List<Boolean> topicNode = host.getOwnInterest();
        List<Double> weightNode = host.getInterest();

        if (topicNode == null || weightNode == null || topicNode.size() != weightNode.size()) {
            return null;
        }

        // Initialize a list to store the matching weights
        List<Double> valInterest = new ArrayList<>();

        Iterator<Map.Entry<Boolean, TupleDe<Integer, String>>> iterator = topicMap.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<Boolean, TupleDe<Integer, String>> entry = iterator.next();

            // Check if the current interest exists in host's interests
            if (topicNode.contains(entry.getKey())) {
                valInterest.add(weightNode.get(topicNode.indexOf(entry.getKey())));
            }
        }

        return valInterest;
    }


    @Override
    public void update() {
        super.update();
//        System.out.println("update 1");

        if (isTransferring() || !canStartTransfer()) {
            return; // transferring, don't try other connections yet
        }

        // Try first the messages that can be delivered to final recipient
        if (exchangeDeliverableMessages() != null) {
            return; // started a transfer, don't try others (yet)
        }

        // then try any/all message to any/all connection
        this.tryAllMessagesToAllConnections();
    }

    @Override
    public MessageRouter replicate() {
        return new CCDTN(this);
    }


}
