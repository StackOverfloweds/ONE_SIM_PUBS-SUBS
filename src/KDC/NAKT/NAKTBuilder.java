package KDC.NAKT;

import routing.util.TupleDe;

import java.util.*;

public class NAKTBuilder {
    private final int lcnum;
    private final KeyManager keyManager;
    private final Map<String, List<TupleDe<String, String>>> encryptedKeyMap;
    private final Map<String, List<TupleDe<String, String>>> subscriberKeyMap; // Map for Subscriber

    public NAKTBuilder(int lcnum) {
        this.lcnum = lcnum;
        this.keyManager = new KeyManager();
        this.encryptedKeyMap = new HashMap<>();
        this.subscriberKeyMap = new HashMap<>();
    }

    public boolean buildNAKT(Map<TupleDe<String, List<Boolean>>, List<TupleDe<TupleDe<Boolean, Integer>, String>>> subscriberTopicMap,
                             List<TupleDe<Integer, Integer>> existingAttributes) {
//        System.out.println("get subscriber topics "+subscriberTopicMap);
        if (subscriberTopicMap == null || subscriberTopicMap.isEmpty() || existingAttributes == null || existingAttributes.isEmpty()) {
//            System.out.println("No subscriber topic or existing attributes");
            return false;
        }

        for (Map.Entry<TupleDe<String, List<Boolean>>, List<TupleDe<TupleDe<Boolean, Integer>, String>>> entry : subscriberTopicMap.entrySet()) {
            TupleDe<String, List<Boolean>> subscriberInfo = entry.getKey();
            List<TupleDe<TupleDe<Boolean, Integer>, String>> PublisherInfo = entry.getValue();

            if (PublisherInfo == null || PublisherInfo.isEmpty()) continue;

            TupleDe<TupleDe<Boolean, Integer>, String> firstEntry = PublisherInfo.get(0);
            TupleDe<Boolean, Integer> innerTuple = firstEntry.getFirst();
            int num = innerTuple.getSecond(); // num is the publisher's sub-topic
            String publisherID = firstEntry.getSecond(); // Publisher ID
//            System.out.println("num: " + num + " | Publisher ID: " + publisherID);

            for (TupleDe<Integer, Integer> range : existingAttributes) {
                int minValue = range.getFirst();
                int maxValue = range.getSecond();

                String rootKey = keyManager.generateRootKey(num);
//                System.out.println("🔐 Encrypted Root Key: " + rootKey);

                int adjustedMaxValue = getNearestPowerOfTwo(maxValue) - 1;
                List<TupleDe<String, String>> keyList = new ArrayList<>();
                encryptTreeNodes(0, adjustedMaxValue, rootKey, "", 1, keyList);

                // Convert num to binary to find the corresponding path
                String binaryPathPubs = Integer.toBinaryString(num);

                // Find the key that matches the binary path
                TupleDe<String, String> selectedKey = keyList.stream()
                        .filter(tuple -> tuple.getFirst().equals(binaryPathPubs))
                        .findFirst()
                        .orElse(null);

                if (selectedKey != null) {
                    encryptedKeyMap.put(publisherID, Collections.singletonList(selectedKey));
//                    System.out.println("🔑 Selected Key for num=" + num + ": " + selectedKey);
                }

                // Find the deepest key within the subscriber range
                TupleDe<String, String> deepestKey = null;
                int maxDepth = -1;

                for (int i = minValue; i <= maxValue; i++) {
                    String binaryPathSubs = Integer.toBinaryString(i);

                    // DFS to find the deepest key
                    for (TupleDe<String, String> keyTuple : keyList) {
                        if (keyTuple.getFirst().equals(binaryPathSubs)) {
                            int currentDepth = binaryPathSubs.length(); // Binary length = depth
                            if (currentDepth > maxDepth) {
                                maxDepth = currentDepth;
                                deepestKey = keyTuple;
                            }
                        }
                    }
                }

                // Store only the deepest key in subscriberKeyMap
                if (deepestKey != null) {
                    subscriberKeyMap.put(subscriberInfo.getFirst(), Collections.singletonList(deepestKey));
//                    System.out.println("🔑 Selected Deepest Key for subscriber range [" + minValue + " - " + maxValue + "]: " + deepestKey);
                }
            }
//            System.out.println("✅ NAKT Build Completed for: " + subscriberInfo.getFirst() + "\n");
        }
        return  true;
    }

    /**
     * Recursive method to build the NAKT tree and store keys
     */
    private void encryptTreeNodes(int min, int max, String parentKey, String binaryPath, int depth, List<TupleDe<String, String>> keyList) {
        if (depth > lcnum || min >= max) return;

        int mid = (min + max) / 2;
        String leftPath = binaryPath + "0";
        String rightPath = binaryPath + "1";

        String leftKey = keyManager.generateChildKey(parentKey, leftPath);
        String rightKey = keyManager.generateChildKey(parentKey, rightPath);

        keyList.add(new TupleDe<>(leftPath, leftKey));
        keyList.add(new TupleDe<>(rightPath, rightKey));

//        System.out.println("🔹 Level " + depth + " (" + binaryPath + ")");
//        System.out.println("  ├── Left Key (" + leftPath + "): " + leftKey);
//        System.out.println("  └── Right Key (" + rightPath + "): " + rightKey);

        encryptTreeNodes(min, mid, leftKey, leftPath, depth + 1, keyList);
        encryptTreeNodes(mid + 1, max, rightKey, rightPath, depth + 1, keyList);
    }

    /**
     * Retrieves the Map of keys for a publisher
     */
    public Map<String, List<TupleDe<String, String>>>  getKeysForPublisher() {
        return this.encryptedKeyMap;
    }

    /**
     * Retrieves the Map of keys for a subscriber based on its ID.
     */
    public Map<String, List<TupleDe<String, String>>>  getKeysForSubscriber() {
        return this.subscriberKeyMap;
    }

    /**
     * Function to find the nearest power of two greater than or equal to the given value.
     */
    private int getNearestPowerOfTwo(int value) {
        int power = 1;
        while (power < value) {
            power *= 2;
        }
        return power;
    }
}
