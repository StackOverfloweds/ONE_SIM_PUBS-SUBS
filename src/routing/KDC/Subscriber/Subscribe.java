package routing.KDC.Subscriber;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.SimScenario;
import routing.KDC.Broker.GetAllBroker;
import routing.KDC.GetAllKDC;
import routing.util.TupleDe;

import java.util.*;

public class Subscribe {
    private Map<DTNHost, Integer> subscriptionCountMap = new HashMap<>();
    private static final int SUBSCRIPTION_THRESHOLD = 5;  // Limit the number of subscriptions to 20

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

        if (registeredTopics.isEmpty()) {
            return false;
        }
        // Create the new Map structure to store topicNode and subTopics
        Map<DTNHost, TupleDe<List<Boolean>, List<TupleDe<Integer, Integer>>>> topicSubTopicMap = new HashMap<>();
        topicSubTopicMap.put(other, new TupleDe<>(topicNode, subTopics));
        return processSubscription(m, registeredTopics, topicSubTopicMap);
    }

    /**
     * Processes the subscription request by matching topics between the subscriber
     * and registered topics. If a match is found, it registers the subscriber and
     * forwards the subscription to brokers and KDCs.
     *
     * @param m                The message object containing subscription data.
     * @param registeredTopics The list of registered topics.
     * @return true if subscription is successful, false otherwise.
     */
    private boolean processSubscription(Message m,
                                        Map<DTNHost, List<TupleDe<Boolean, Integer>>> registeredTopics,
                                        Map<DTNHost, TupleDe<List<Boolean>, List<TupleDe<Integer, Integer>>>> topicSubTopicMap) {

        for (DTNHost broker : SimScenario.getInstance().getHosts()) {
            for (Connection con : broker.getConnections()) {
                // send msg to broker
                DTNHost otherBroker = con.getOtherNode(broker);
                if (otherBroker != null && otherBroker.isBroker()) {
                    addMessageToHosts(m, otherBroker);
                    return sendMessageToKDCs(m, registeredTopics, topicSubTopicMap);
                }
            }
        }
        return false;
    }

    /**
     * Sends the subscription message to KDCs connected to the given DTNHost.
     *
     * @param m     The message to send.
     * @return true if the message is successfully sent to KDCs, false otherwise.
     */
    private boolean sendMessageToKDCs(Message m, Map<DTNHost, List<TupleDe<Boolean, Integer>>> registeredTopics, Map<DTNHost, TupleDe<List<Boolean>, List<TupleDe<Integer, Integer>>>> topicSubTopicMap) {


        // Iterate over the topic-subtopic map
        Map<DTNHost, List<TupleDe<List<Boolean>, List<TupleDe<Integer, Integer>>>>> hostDataMap = new HashMap<>();
        for (Map.Entry<DTNHost, TupleDe<List<Boolean>, List<TupleDe<Integer, Integer>>>> entry : topicSubTopicMap.entrySet()) {
            DTNHost subscriberID = entry.getKey();  // Retrieve the DTNHost
            TupleDe<List<Boolean>, List<TupleDe<Integer, Integer>>> tupleData = entry.getValue();
            List<Boolean> topicNode = tupleData.getFirst();
            List<TupleDe<Integer, Integer>> subTopics = tupleData.getSecond();

            Set<Boolean> topicSet = new HashSet<>(topicNode);  // Set of topics for this subscriber

            // Populate hostDataMap for the subscriber
            hostDataMap.put(subscriberID, Collections.singletonList(tupleData));

            // Process each connection to the KDC
            for (DTNHost kdc : SimScenario.getInstance().getHosts()) {
                // Check if the subscriber has reached the subscription threshold
                int currentSubscriptionCount = subscriptionCountMap.getOrDefault(kdc, 0);
                if (currentSubscriptionCount >= SUBSCRIPTION_THRESHOLD) {
                    return false;
                }
                for (Connection con : kdc.getConnections()) {
                    DTNHost getKdc = con.getOtherNode(kdc);
                    if (getKdc != null && getKdc.isKDC()) {
                        // For each registered topic, check if the topic matches
                        for (Map.Entry<DTNHost, List<TupleDe<Boolean, Integer>>> registeredEntry : registeredTopics.entrySet()) {
                            for (TupleDe<Boolean, Integer> tuple : registeredEntry.getValue()) {
                                if (topicSet.contains(tuple.getFirst())) {
                                    // Add the host data to the message property
                                    m.addProperty("KDC_Subscribe_", hostDataMap);
                                    addMessageToHosts(m, getKdc);
                                    // Increment the subscription count only once after successful subscription
                                    subscriptionCountMap.put(getKdc, currentSubscriptionCount + 1);
                                    return true; // Message successfully sent to KDC
                                }
                            }
                        }
                    }
                }
            }

        }

        return false; // No KDCs found or no matching topics
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
     * Adds the given message to the buffers of the provided hosts.
     *
     * @param m     The message to be added.
     * @param hosts The list of hosts to which the message should be added.
     */
    private void addMessageToHosts(Message m, DTNHost hosts) {
        hosts.addBufferToHost(m);
    }


}
