package KDC.NAKT;

import core.DTNHost;
import routing.util.TupleDe;
import java.util.*;

public class NAKTBuilder extends KeyManager {
    private final int lcnum;
    private final Map<DTNHost, TupleDe<String, String>> encryptedKeyMap;
    private final Map<DTNHost, List<TupleDe<String, String>>> subscriberKeyMap;

    /**
     * Constructor for NAKTBuilder.
     * Initializes the encryption and subscription key maps.
     *
     * @param lcnum The maximum binary depth for key tree generation.
     */
    public NAKTBuilder(int lcnum) {
        super(); // Call parent constructor of KeyManager
        this.lcnum = lcnum;
        this.encryptedKeyMap = new HashMap<>();
        this.subscriberKeyMap = new HashMap<>();
    }

    /**
     * Builds the NAKT key structure.
     * - Generates keys for publishers and subscribers.
     * - Assigns appropriate encryption keys to topics and attributes.
     *
     * @param subscriberTopicMap  Map containing subscriber topics and their attributes.
     * @param existingAttributes  List of numeric attributes used for key derivation.
     * @return true if the NAKT key structure is successfully built, false otherwise.
     */
    public boolean buildNAKT(Map<TupleDe<DTNHost, List<Boolean>>, List<TupleDe<TupleDe<Boolean, Integer>, DTNHost>>> subscriberTopicMap,
                             List<TupleDe<Integer, Integer>> existingAttributes) {
        if (subscriberTopicMap == null || subscriberTopicMap.isEmpty() || existingAttributes == null || existingAttributes.isEmpty()) {
            System.out.println("subscriberTopicMap is null or empty "+ subscriberTopicMap);
            return false;
        }

        for (Map.Entry<TupleDe<DTNHost, List<Boolean>>, List<TupleDe<TupleDe<Boolean, Integer>, DTNHost>>> entry : subscriberTopicMap.entrySet()) {
            TupleDe<DTNHost, List<Boolean>> subscriberInfo = entry.getKey();
            List<TupleDe<TupleDe<Boolean, Integer>, DTNHost>> publisherInfo = entry.getValue();

            if (publisherInfo == null || publisherInfo.isEmpty()) continue;

            TupleDe<TupleDe<Boolean, Integer>, DTNHost> firstEntry = publisherInfo.get(0);
            TupleDe<Boolean, Integer> innerTuple = firstEntry.getFirst();
            int num = innerTuple.getSecond();

            // 🔹 **Generate Publisher Key**
            if (!encryptedKeyMap.containsKey(firstEntry.getSecond())) {
                String rootKey = generateRootKey(num);
                List<TupleDe<String, String>> keyList = new ArrayList<>();
                encryptTreeNodes(0, getNearestPowerOfTwo(num) - 1, rootKey, "", 1, keyList);

                String binaryPathPubs = Integer.toBinaryString(num);
                TupleDe<String, String> selectedKey = keyList.stream()
                        .filter(tuple -> tuple.getFirst().equals(binaryPathPubs))
                        .findFirst()
                        .orElse(null);

                if (selectedKey != null) {
                    encryptedKeyMap.put(firstEntry.getSecond(), selectedKey);
                }
            }

            // 🔹 **Generate Subscriber Key**
            if (!subscriberKeyMap.containsKey(subscriberInfo.getFirst())) {
                List<TupleDe<String, String>> derivedKeys = new ArrayList<>();
                String closestBinaryPath = null;
                int maxDepth = -1;

                // **Calculate key tree once for all attribute ranges**
                List<TupleDe<String, String>> keyList = new ArrayList<>();
                String rootKey = generateRootKey(num);
                encryptTreeNodes(0, getNearestPowerOfTwo(num) - 1, rootKey, "", 1, keyList);

                for (TupleDe<Integer, Integer> range : existingAttributes) {
                    int minValue = range.getFirst();
                    int maxValue = range.getSecond();

                    // **Find the closest matching binary path**
                    for (int i = minValue; i <= maxValue; i++) {
                        String binaryPathSubs = Integer.toBinaryString(i);

                        for (TupleDe<String, String> keyTuple : keyList) {
                            String currentBinaryPath = keyTuple.getFirst();
                            String currentKey = keyTuple.getSecond();

                            if (binaryPathSubs.startsWith(currentBinaryPath)) {
                                int currentDepth = currentBinaryPath.length();

                                // **If a deeper key is found, reset shallower keys**
                                if (currentDepth > maxDepth) {
                                    maxDepth = currentDepth;
                                    derivedKeys.clear();
                                    closestBinaryPath = currentBinaryPath;
                                }

                                // **Add key if depth matches maxDepth and it's not duplicated**
                                boolean keyExists = derivedKeys.stream()
                                        .anyMatch(tuple -> tuple.getFirst().equals(currentBinaryPath) &&
                                                tuple.getSecond().equals(currentKey));

                                if (currentDepth == maxDepth && !keyExists) {
                                    derivedKeys.add(new TupleDe<>(currentBinaryPath, currentKey));
                                }
                            }
                        }
                    }
                }

                // 🔹 **Add all children of the selected binary path**
                if (closestBinaryPath != null) {
                    for (TupleDe<String, String> keyTuple : keyList) {
                        if (keyTuple.getFirst().startsWith(closestBinaryPath) && !derivedKeys.contains(keyTuple)) {
                            derivedKeys.add(keyTuple);
                        }
                    }
                }

                if (!derivedKeys.isEmpty()) {
                    subscriberKeyMap.put(subscriberInfo.getFirst(), derivedKeys);
                }
            }
        }
        return true;
    }

    /**
     * Encrypts tree nodes recursively to generate key hierarchies.
     * - Each node creates left and right children in a binary tree format.
     * - Stops at the defined depth (lcnum).
     *
     * @param min        The minimum range for encryption.
     * @param max        The maximum range for encryption.
     * @param parentKey  The key from the parent node.
     * @param binaryPath The binary path of the node.
     * @param depth      The current tree depth.
     * @param keyList    The list to store generated keys.
     */
    private void encryptTreeNodes(int min, int max, String parentKey, String binaryPath, int depth, List<TupleDe<String, String>> keyList) {
        if (depth > lcnum || min >= max) return;

        int mid = (min + max) / 2;
        String leftPath = binaryPath + "0";
        String rightPath = binaryPath + "1";

        String leftKey = generateChildKey(parentKey, leftPath);
        String rightKey = generateChildKey(parentKey, rightPath);

        // 🔹 **Ensure only relevant paths are taken**
        if (leftPath.length() == lcnum) keyList.add(new TupleDe<>(leftPath, leftKey));
        if (rightPath.length() == lcnum) keyList.add(new TupleDe<>(rightPath, rightKey));



// 🔹 **Logging Debugging**
        System.out.println("🔹 Level " + depth + " (" + binaryPath + ")");
        System.out.println("  ├── Left Key (" + leftPath + "): " + leftKey);
        System.out.println("  └── Right Key (" + rightPath + "): " + rightKey);

        // 🔹 **Recursive call to generate deeper key nodes**
        encryptTreeNodes(min, mid, leftKey, leftPath, depth + 1, keyList);
        encryptTreeNodes(mid + 1, max, rightKey, rightPath, depth + 1, keyList);
    }

    /**
     * Retrieves the generated encryption keys for publishers.
     *
     * @return A map containing publisher IDs and their encryption keys.
     */
    public Map<DTNHost, TupleDe<String, String>> getKeysForPublisher() {
        return this.encryptedKeyMap;
    }

    /**
     * Retrieves the generated authentication keys for subscribers.
     *
     * @return A map containing subscriber IDs and their list of authentication keys.
     */
    public Map<DTNHost, List<TupleDe<String, String>>> getKeysForSubscriber() {
        return this.subscriberKeyMap;
    }

    /**
     * Finds the nearest power of two that is greater than or equal to the given value.
     *
     * @param value The input integer.
     * @return The nearest power of two.
     */
    private int getNearestPowerOfTwo(int value) {
        int power = 1;
        while (power < value) {
            power *= 2;
        }
        return power;
    }
}

