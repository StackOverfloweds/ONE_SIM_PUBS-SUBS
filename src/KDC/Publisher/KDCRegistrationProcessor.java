package KDC.Publisher;

import routing.util.TupleDe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import routing.PublishAndSubscriberRouting;

public class KDCRegistrationProcessor {
    private Map<Integer, List<TupleDe<Boolean, String>>> registeredTopics;

    public KDCRegistrationProcessor() {
        // Initialize the registered topics from PublishAndSubscriberRouting
        this.registeredTopics = PublishAndSubscriberRouting.registeredTopics;
    }

    /**
     * Processes the topic registration at the KDC and returns the updated map of registered topics.
     *
     * @param topicMap A map containing the topics to be registered.
     * @return The updated map of registered topics.
     */
    public Map<Integer, List<TupleDe<Boolean, String>>> processTopicRegistrationAtKDC(Map<Integer, TupleDe<Boolean, String>> topicMap) {
        // Process each topic and add it to the registered topics map if it's not already registered.
        for (Map.Entry<Integer, TupleDe<Boolean, String>> entry : topicMap.entrySet()) {
            int subTopic = entry.getKey();
            TupleDe<Boolean, String> tuple = entry.getValue();

            // Ensure the subTopic exists in the registeredTopics map
            registeredTopics.computeIfAbsent(subTopic, k -> new ArrayList<>());

            // Check if the topic is already registered for this publisher
            boolean isRegistered = registeredTopics.get(subTopic).stream()
                    .anyMatch(existingTuple -> existingTuple.getSecond().equals(tuple.getSecond()));

            if (!isRegistered) {
                // If not registered, add the tuple to the map
                registeredTopics.get(subTopic).add(tuple);
            }
        }

        // Return the updated map of registered topics
        return registeredTopics;
    }

    /**
     * Retrieves the map of registered topics.
     *
     * @return A map containing all registered topics and their associated data.
     */
    public Map<Integer, List<TupleDe<Boolean, String>>> getRegisteredTopics() {
        return registeredTopics;
    }
}
