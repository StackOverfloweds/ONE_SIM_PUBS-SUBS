package KDC.Subscriber;

import KDC.NAKT.NAKTBuilder;
import KDC.Publisher.KDCRegistrationProcessor;
import core.DTNHost;
import core.SimScenario;
import routing.PublishAndSubscriberRouting;
import routing.util.TupleDe;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;

public class BrokerHandler {
    private Map<TupleDe<String, List<Boolean>>, List<TupleDe<Integer, Integer>>> subscribedTopics = PublishAndSubscriberRouting.subscribedTopics;
    private Map<String, TupleDe<String, String>> keyEncryption = PublishAndSubscriberRouting.keyEncryption;
    private Map<String, List<TupleDe<String, String>>> keyAuthentication = PublishAndSubscriberRouting.keyAuthentication;
    private int lcnum = PublishAndSubscriberRouting.lcnum;

    public KDCRegistrationProcessor processor = new KDCRegistrationProcessor();

    /**
     * Sends a subscription request to a broker.
     * This method validates the broker and the subscription request before processing.
     *
     * @param subscriptions The subscription details of the subscriber.
     * @param broker The broker responsible for managing subscriptions.
     * @return True if the subscription was successfully processed, otherwise false.
     */
    public boolean sendSubscriptionToBroker(Map<TupleDe<String, List<Boolean>>, List<TupleDe<Integer, Integer>>> subscriptions, DTNHost broker) {
        if (broker == null || subscriptions == null || subscriptions.isEmpty()) return false;
        if (!broker.isBroker()) {
            return false;
        }
        addSubscriptions(subscriptions);
        return forwardToKDC(subscriptions, broker);
    }

    /**
     * Forwards the subscription details to the KDC for processing.
     * It identifies the KDC and updates the subscription data accordingly.
     *
     * @param data The subscription data to be forwarded.
     * @param broker The broker forwarding the request.
     * @return True if successfully forwarded, otherwise false.
     */
    private boolean forwardToKDC(Map<?, ?> data, DTNHost broker) {
        List<DTNHost> allHosts = SimScenario.getInstance().getHosts();
        if (allHosts == null || allHosts.isEmpty()) return false;

        for (DTNHost host : allHosts) {
            if (host.isKDC()) {
                Object router = host.getRouter();
                if (router instanceof PublishAndSubscriberRouting) {
                    PublishAndSubscriberRouting psRouter = (PublishAndSubscriberRouting) router;
                    if (!data.isEmpty()) {
                        Object firstValue = data.values().iterator().next();
                        if (firstValue instanceof List<?>) {
                            addSubscriptions((Map<TupleDe<String, List<Boolean>>, List<TupleDe<Integer, Integer>>>) data);
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Adds new subscriptions to the subscribed topics.
     * This method ensures that duplicate subscriptions are not added and updates the encryption keys accordingly.
     *
     * @param subscriptions The subscription data to be added.
     */
    public void addSubscriptions(Map<TupleDe<String, List<Boolean>>, List<TupleDe<Integer, Integer>>> subscriptions) {
        if (subscriptions == null || subscriptions.isEmpty()) {
            return;
        }

        Map<Integer, List<TupleDe<Boolean, String>>> topics = processor.getRegisteredTopics();
        if (topics == null || topics.isEmpty()) {
            return;
        }

        Map<TupleDe<String, List<Boolean>>, List<TupleDe<TupleDe<Boolean, Integer>, String>>> subscriberTopicMap = new HashMap<>();

        for (Map.Entry<TupleDe<String, List<Boolean>>, List<TupleDe<Integer, Integer>>> entry : subscriptions.entrySet()) {
            TupleDe<String, List<Boolean>> subscriberInfo = entry.getKey();
            List<TupleDe<Integer, Integer>> topicAttributes = entry.getValue();

            // ✅ **Check if the subscriber is already registered with the same topic**
            if (subscribedTopics.containsKey(subscriberInfo)) {
                List<TupleDe<Integer, Integer>> existingAttributes = subscribedTopics.get(subscriberInfo);
                if (existingAttributes.containsAll(topicAttributes)) {
                    continue; // **Avoid duplicate subscriptions**
                }
            }

            boolean topicMatches = false;
            List<TupleDe<TupleDe<Boolean, Integer>, String>> matchedTopics = new ArrayList<>();

            if (topicAttributes == null || topicAttributes.isEmpty()) {
                return;
            }
            for (TupleDe<Integer, Integer> attr : topicAttributes) {
                int minValue = attr.getFirst();
                int maxValue = attr.getSecond();

                for (Map.Entry<Integer, List<TupleDe<Boolean, String>>> regEntry : topics.entrySet()) {
                    int subTopicPublisher = regEntry.getKey();
                    List<TupleDe<Boolean, String>> registeredValues = regEntry.getValue();

                    for (TupleDe<Boolean, String> registeredValue : registeredValues) {
                        Boolean topicBoolean = registeredValue.getFirst();
                        String idPubs = registeredValue.getSecond();

                        if (subTopicPublisher >= minValue && subTopicPublisher <= maxValue && subscriberInfo.getSecond().contains(topicBoolean)) {
                            topicMatches = true;
                            matchedTopics.add(new TupleDe<>(new TupleDe<>(topicBoolean, subTopicPublisher), idPubs));
                            break;
                        }
                    }
                    if (topicMatches) break;
                }
                if (!topicMatches) {
                    continue;
                }
            }

            if (!subscribedTopics.containsKey(subscriberInfo)) {
                subscribedTopics.put(subscriberInfo, new ArrayList<>());
            }
            List<TupleDe<Integer, Integer>> existingAttributes = subscribedTopics.get(subscriberInfo);
            for (TupleDe<Integer, Integer> attribute : topicAttributes) {
                if (!existingAttributes.contains(attribute)) {
                    existingAttributes.add(attribute);
                }
            }

            if (!matchedTopics.isEmpty()) {
                subscriberTopicMap.put(subscriberInfo, matchedTopics);
            }

            // ✅ **If the subscription is successful, generate NAKT**
            NAKTBuilder nakt = new NAKTBuilder(lcnum);
            if (nakt.buildNAKT(subscriberTopicMap, existingAttributes)) {
                Map<String, TupleDe<String, String>> publisherKeys = nakt.getKeysForPublisher();
                Map<String, List<TupleDe<String, String>>> subscriberKeys = nakt.getKeysForSubscriber();

                if (publisherKeys != null && !publisherKeys.isEmpty()) {
                    for (Map.Entry<String, TupleDe<String, String>> entryKey : publisherKeys.entrySet()) {
                        if (entryKey.getValue() != null && !entryKey.getValue().isEmpty()) {
                            if (!keyEncryption.containsKey(entryKey.getKey())) {
                                keyEncryption.put(entryKey.getKey(), entryKey.getValue());
                            }
                        }
                    }
                }

                if (subscriberKeys != null && !subscriberKeys.isEmpty()) {
                    for (Map.Entry<String, List<TupleDe<String, String>>> entryKey : subscriberKeys.entrySet()) {
                        if (entryKey.getValue() != null && !entryKey.getValue().isEmpty()) {
                            if (!keyAuthentication.containsKey(entryKey.getKey())) {
                                keyAuthentication.put(entryKey.getKey(), entryKey.getValue());
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Retrieves the map of subscribed topics.
     *
     * @return A map containing subscribed topics and their attributes.
     */
    public Map<TupleDe<String, List<Boolean>>, List<TupleDe<Integer, Integer>>> getSubscribedTopics() {
        return subscribedTopics;
    }

    /**
     * Retrieves the encryption keys for publishers.
     *
     * @return A map containing publisher encryption keys.
     */
    public Map<String, TupleDe<String, String>> getKeyEncryption() {
        return keyEncryption;
    }

    /**
     * Retrieves the authentication keys for subscribers.
     *
     * @return A map containing subscriber authentication keys.
     */
    public Map<String, List<TupleDe<String, String>>> getKeyAuthentication() {
        return keyAuthentication;
    }
}
