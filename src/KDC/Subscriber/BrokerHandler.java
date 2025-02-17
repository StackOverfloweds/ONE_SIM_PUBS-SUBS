package KDC.Subscriber;

import KDC.NAKT.NAKTBuilder;
import KDC.Publisher.KDCRegistrationProcessor;
import core.DTNHost;
import core.SimScenario;
import routing.CCDTN;
import routing.PublishAndSubscriberRouting;
import routing.util.TupleDe;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;

public class BrokerHandler {
    private Map<TupleDe<String, List<Boolean>>, List<TupleDe<Integer, Integer>>> subscribedTopics = PublishAndSubscriberRouting.subscribedTopics;
    private Map<String, TupleDe<String, String>> keyEncryption = PublishAndSubscriberRouting.keyEncryption;
    private Map<String, TupleDe<String, String>> keyAuthentication = PublishAndSubscriberRouting.keyAuthentication;
    private int lcnum = PublishAndSubscriberRouting.lcnum;

    public KDCRegistrationProcessor processor = new KDCRegistrationProcessor();


    public boolean sendSubscriptionToBroker(Map<TupleDe<String, List<Boolean>>, List<TupleDe<Integer, Integer>>> subscriptions, DTNHost broker) {
        if (broker == null || subscriptions == null || subscriptions.isEmpty()) return false;
        if (!broker.isBroker()) {
            return false;
        }
        addSubscriptions(subscriptions);
        return forwardToKDC(subscriptions, broker);
    }

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

    public void addSubscriptions(Map<TupleDe<String, List<Boolean>>, List<TupleDe<Integer, Integer>>> subscriptions) {
        if (subscriptions == null || subscriptions.isEmpty()) {
            return;
        }
        Map<Integer, List<TupleDe<Boolean, String>>> topics = processor.getRegisteredTopics(); // Integer = sub-topik
        if (topics == null || topics.isEmpty()) {
            return;
        }

        Map<TupleDe<String, List<Boolean>>, List<TupleDe<TupleDe<Boolean, Integer>, String>>> subscriberTopicMap = new HashMap<>();

        for (Map.Entry<TupleDe<String, List<Boolean>>, List<TupleDe<Integer, Integer>>> entry : subscriptions.entrySet()) {
            TupleDe<String, List<Boolean>> subscriberInfo = entry.getKey();
            List<TupleDe<Integer, Integer>> topicAttributes = entry.getValue();

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
                            matchedTopics.add(new TupleDe<>(new TupleDe<>(topicBoolean, subTopicPublisher), idPubs)); // Struktur baru
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

            // Simpan subscriber dan topik yang cocok ke dalam Map
            if (!matchedTopics.isEmpty()) {
                subscriberTopicMap.put(subscriberInfo, matchedTopics);
            }

            // Jika sukses subscribe, buat NAKT
            NAKTBuilder nakt = new NAKTBuilder(lcnum);
            if (nakt.buildNAKT(subscriberTopicMap, existingAttributes)) {
                Map<String, TupleDe<String, String>> publisherKeys = nakt.getKeysForPublisher();
                Map<String, TupleDe<String, String>> subscriberKeys = nakt.getKeysForSubscriber();

                // Pastikan tidak null & tidak kosong sebelum diproses
                if (publisherKeys != null && !publisherKeys.isEmpty()) {
                    for (Map.Entry<String, TupleDe<String, String>> entryKey : publisherKeys.entrySet()) {
                        if (entryKey.getValue() != null && !entry.getValue().isEmpty()) {
                            // Pastikan key sudah ada, jika belum buat list baru
                            if (!keyEncryption.containsKey(entryKey.getKey())) {
                                keyEncryption.put(entryKey.getKey(), entryKey.getValue());
                            }
                        }
                    }
                }

                if (subscriberKeys != null && !subscriberKeys.isEmpty()) {
                    for (Map.Entry<String, TupleDe<String, String>> entryKey : subscriberKeys.entrySet()) {
                        if (entryKey.getValue() != null && !entry.getValue().isEmpty()) {
                            if (!keyAuthentication.containsKey(entryKey.getKey())) {
                                keyAuthentication.put(entryKey.getKey(), entryKey.getValue());
                            }

                        }
                    }
                }
            }
        }
    }

    public Map<TupleDe<String, List<Boolean>>, List<TupleDe<Integer, Integer>>> getSubscribedTopics() {
        // Implementation to retrieve the registered topics
        return subscribedTopics;
    }

    public Map<String, TupleDe<String, String>> getKeyEncryption () {
        return keyEncryption;
    }

    public Map<String, TupleDe<String, String>> getKeyAuthentication() {
        return keyAuthentication;
    }


}
