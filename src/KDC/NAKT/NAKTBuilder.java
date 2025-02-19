package KDC.NAKT;

import routing.util.TupleDe;

import java.util.*;

public class NAKTBuilder {
    private final int lcnum;
    private final KeyManager keyManager;
    private final Map<String, TupleDe<String, String>> encryptedKeyMap;
    private final Map<String, List<TupleDe<String, String>>> subscriberKeyMap;

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

            if (publisherInfo == null || publisherInfo.isEmpty()) continue;

            TupleDe<TupleDe<Boolean, Integer>, String> firstEntry = publisherInfo.get(0);
            TupleDe<Boolean, Integer> innerTuple = firstEntry.getFirst();
            int num = innerTuple.getSecond();
            String publisherID = firstEntry.getSecond();

            // 🔹 **Generate Publisher Key**
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

            // 🔹 **Generate Subscriber Key**
            if (!subscriberKeyMap.containsKey(subscriberInfo.getFirst())) {
                List<TupleDe<String, String>> derivedKeys = new ArrayList<>();
                String closestBinaryPath = null;
                int maxDepth = -1;

                // **Hitung key tree sekali saja untuk semua range**
                List<TupleDe<String, String>> keyList = new ArrayList<>();
                String rootKey = keyManager.generateRootKey(num);
                encryptTreeNodes(0, getNearestPowerOfTwo(num) - 1, rootKey, "", 1, keyList);

                for (TupleDe<Integer, Integer> range : existingAttributes) {
                    int minValue = range.getFirst();
                    int maxValue = range.getSecond();

                    // **Cari key dengan binary path yang paling mendekati dari bawah**
                    for (int i = minValue; i <= maxValue; i++) {
                        String binaryPathSubs = Integer.toBinaryString(i);

                        for (TupleDe<String, String> keyTuple : keyList) {
                            String currentBinaryPath = keyTuple.getFirst(); // 🔹 Binary Path
                            String currentKey = keyTuple.getSecond(); // 🔹 Key-nya
//                            System.out.println("get binary path: " + currentBinaryPath);
                            // **Pastikan binary path tidak selalu "1111"**
                            if (binaryPathSubs.startsWith(currentBinaryPath)) {
                                int currentDepth = currentBinaryPath.length();

                                // **Jika menemukan kedalaman lebih dalam, reset key yang lebih dangkal**
                                if (currentDepth > maxDepth) {
                                    maxDepth = currentDepth;
                                    derivedKeys.clear();
                                    closestBinaryPath = currentBinaryPath;
                                }

                                // **Tambahkan key jika kedalaman cocok & belum ada dalam daftar**
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

                // 🔹 **Ambil semua turunan dari key yang ditemukan**
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


    private void encryptTreeNodes(int min, int max, String parentKey, String binaryPath, int depth, List<TupleDe<String, String>> keyList) {
        if (depth > lcnum || min >= max) return;

        int mid = (min + max) / 2;
        String leftPath = binaryPath + "0";
        String rightPath = binaryPath + "1";

        String leftKey = keyManager.generateChildKey(parentKey, leftPath);
        String rightKey = keyManager.generateChildKey(parentKey, rightPath);

        // 🔹 **Pastikan hanya path yang sesuai dengan publisher/subscriber yang diambil**
        if (leftPath.length() == lcnum) keyList.add(new TupleDe<>(leftPath, leftKey));
        if (rightPath.length() == lcnum) keyList.add(new TupleDe<>(rightPath, rightKey));

        // 🔹 **Logging Debugging**
//        System.out.println("🔹 Level " + depth + " (" + binaryPath + ")");
//        System.out.println("  ├── Left Key (" + leftPath + "): " + leftKey);
//        System.out.println("  └── Right Key (" + rightPath + "): " + rightKey);

        // 🔹 **Rekursif untuk menelusuri semua kemungkinan path**
        encryptTreeNodes(min, mid, leftKey, leftPath, depth + 1, keyList);
        encryptTreeNodes(mid + 1, max, rightKey, rightPath, depth + 1, keyList);
    }





    public Map<String, TupleDe<String, String>> getKeysForPublisher() {
        return this.encryptedKeyMap;
    }

    public Map<String, List<TupleDe<String, String>>> getKeysForSubscriber() {
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
