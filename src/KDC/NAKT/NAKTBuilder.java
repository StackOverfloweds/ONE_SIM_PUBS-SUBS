package KDC.NAKT;

import routing.util.TupleDe;

import java.util.List;
import java.util.Map;

public class NAKTBuilder {
    private final int lcnum;
    private final KeyManager keyManager;

    public NAKTBuilder(int lcnum) {
        this.lcnum = lcnum;
        this.keyManager = new KeyManager();
    }

    public void buildNAKT(Map<TupleDe<String, List<Boolean>>, List<TupleDe<TupleDe<Boolean, Integer>, String>>> subscriberTopicMap,
                          List<TupleDe<Integer, Integer>> existingAttributes) {
        if (subscriberTopicMap == null || subscriberTopicMap.isEmpty() || existingAttributes == null || existingAttributes.isEmpty()) {
            return;
        }

        for (Map.Entry<TupleDe<String, List<Boolean>>, List<TupleDe<TupleDe<Boolean, Integer>, String>>> entry : subscriberTopicMap.entrySet()) {
            TupleDe<String, List<Boolean>> subscriberInfo = entry.getKey();
            List<TupleDe<TupleDe<Boolean, Integer>, String>> PublisherInfo = entry.getValue();

            if (PublisherInfo == null || PublisherInfo.isEmpty()) continue;

            TupleDe<TupleDe<Boolean, Integer>, String> firstEntry = PublisherInfo.get(0);
            TupleDe<Boolean, Integer> innerTuple = firstEntry.getFirst();
            int num = innerTuple.getSecond(); // num is sub-topic publisher
            System.out.println("num: " + num);

            for (TupleDe<Integer, Integer> range : existingAttributes) {
                int maxValue = range.getSecond();

                String rootKey = keyManager.generateRootKey(num);
                System.out.println("\uD83D\uDD10 Encrypted Root Key: " + rootKey);

                int adjustedMaxValue = getNearestPowerOfTwo(maxValue) - 1;
                encryptTreeNodes(0, adjustedMaxValue, rootKey, "", 1);
            }

            System.out.println("✅ NAKT Build Completed for: " + subscriberInfo.getFirst() + "\n");
        }
    }

    /**
     * recursif metode to generate Tree Node
     * @param min
     * @param max
     * @param parentKey
     * @param binaryPath
     * @param depth
     */
    private void encryptTreeNodes(int min, int max, String parentKey, String binaryPath, int depth) {
        if (depth > lcnum || min >= max) return;

        int mid = (min + max) / 2;
        String leftPath = binaryPath + "0";
        String rightPath = binaryPath + "1";

        String leftKey = keyManager.generateChildKey(parentKey, leftPath);
        String rightKey = keyManager.generateChildKey(parentKey, rightPath);

        System.out.println("\uD83D\uDD39 Level " + depth + " (" + binaryPath + ")");
        System.out.println("  ├── Left Key (" + leftPath + "): " + leftKey);
        System.out.println("  └── Right Key (" + rightPath + "): " + rightKey);

        encryptTreeNodes(min, mid, leftKey, leftPath, depth + 1);
        encryptTreeNodes(mid + 1, max, rightKey, rightPath, depth + 1);
    }

    private int getNearestPowerOfTwo(int value) {
        int power = 1;
        while (power < value) {
            power *= 2;
        }
        return power;
    }
}
