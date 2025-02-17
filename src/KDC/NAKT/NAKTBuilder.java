package KDC.NAKT;

import routing.util.TupleDe;

import java.util.*;

public class NAKTBuilder {
    private final int lcnum;
    private final KeyManager keyManager;
    private final Map<String, TupleDe<String, String>> encryptedKeyMap;
    private final Map<String, TupleDe<String, String>> subscriberKeyMap;

    public NAKTBuilder(int lcnum) {
        this.lcnum = lcnum;
        this.keyManager = new KeyManager();
        this.encryptedKeyMap = new HashMap<>();
        this.subscriberKeyMap = new HashMap<>();
    }

    public boolean buildNAKT(Map<TupleDe<String, List<Boolean>>, List<TupleDe<TupleDe<Boolean, Integer>, String>>> subscriberTopicMap,
                             List<TupleDe<Integer, Integer>> existingAttributes) {
        if (subscriberTopicMap == null || subscriberTopicMap.isEmpty() || existingAttributes == null || existingAttributes.isEmpty()) {
            return false;
        }

        for (Map.Entry<TupleDe<String, List<Boolean>>, List<TupleDe<TupleDe<Boolean, Integer>, String>>> entry : subscriberTopicMap.entrySet()) {
            TupleDe<String, List<Boolean>> subscriberInfo = entry.getKey();
            List<TupleDe<TupleDe<Boolean, Integer>, String>> publisherInfo = entry.getValue();
//            System.out.println("subscriberInfo: " + subscriberInfo);
            if (publisherInfo == null || publisherInfo.isEmpty()) continue;

            TupleDe<TupleDe<Boolean, Integer>, String> firstEntry = publisherInfo.get(0);
            TupleDe<Boolean, Integer> innerTuple = firstEntry.getFirst();
            int num = innerTuple.getSecond();
            String publisherID = firstEntry.getSecond();

            // Check if the publisher key already exists
            if (!encryptedKeyMap.containsKey(publisherID)) {
                String rootKey = keyManager.generateRootKey(num);
                List<TupleDe<String, String>> keyList = new ArrayList<>();
                encryptTreeNodes(0, getNearestPowerOfTwo(num) - 1, rootKey, "", 1, keyList);

                String binaryPathPubs = Integer.toBinaryString(num);
                TupleDe<String, String> selectedKey = keyList.stream()
                        .filter(tuple -> tuple.getFirst().equals(binaryPathPubs))
                        .findFirst()
                        .orElse(null);

                if (selectedKey != null) {
                    encryptedKeyMap.put(publisherID, selectedKey);
                }
            }

            // Process subscriber key
            if (!subscriberKeyMap.containsKey(subscriberInfo.getFirst())) {
                TupleDe<String, String> deepestKey = null;
                int maxDepth = -1;
                for (TupleDe<Integer, Integer> range : existingAttributes) {
                    int minValue = range.getFirst();
                    int maxValue = range.getSecond();
                    List<TupleDe<String, String>> keyList = new ArrayList<>();
                    encryptTreeNodes(0, getNearestPowerOfTwo(maxValue) - 1, keyManager.generateRootKey(num), "", 1, keyList);

                    for (int i = minValue; i <= maxValue; i++) {
                        String binaryPathSubs = Integer.toBinaryString(i);
                        for (TupleDe<String, String> keyTuple : keyList) {
                            if (keyTuple.getFirst().equals(binaryPathSubs)) {
                                int currentDepth = binaryPathSubs.length();
                                if (currentDepth > maxDepth) {
                                    maxDepth = currentDepth;
                                    deepestKey = keyTuple;
                                }
                            }
                        }
                    }
                }
                if (deepestKey != null) {
                    subscriberKeyMap.put(subscriberInfo.getFirst(), deepestKey);
                }
            }
        }
        return true;
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
    public Map<String, TupleDe<String, String>> getKeysForPublisher() {
        return this.encryptedKeyMap;
    }

    /**
     * Retrieves the Map of keys for a subscriber based on its ID.
     */
    public Map<String, TupleDe<String, String>> getKeysForSubscriber() {
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
