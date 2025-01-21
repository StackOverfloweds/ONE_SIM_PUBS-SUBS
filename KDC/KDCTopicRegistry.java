public class KDCTopicRegistry {
    private static KDCTopicRegistry instance;
    private Map<Integer, List<TupleDe<List<Boolean>, String>>> registeredTopics = new HashMap<>();

    private KDCTopicRegistry() {}

    public static KDCTopicRegistry getInstance() {
        if (instance == null) {
            instance = new KDCTopicRegistry();
        }
        return instance;
    }

    public void registerTopic(Integer subTopic, List<Boolean> topicList, String publisherId) {
        // Add the topic to the registry
        registeredTopics.put(subTopic, new ArrayList<>(List.of(new TupleDe<>(topicList, publisherId))));
        System.out.println("Topic registered: " + subTopic + " from Publisher: " + publisherId);
    }

    public Map<Integer, List<TupleDe<List<Boolean>, String>>> getRegisteredTopics() {
        return registeredTopics;
    }
}
