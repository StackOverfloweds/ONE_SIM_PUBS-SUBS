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

    /**
     * Membangun NAKT berdasarkan subscriberTopicMap dan atribut yang ada.
     *
     * @param subscriberTopicMap    Map dari subscriber ke daftar topik yang cocok
     * @param existingAttributes    Daftar atribut eksisting yang akan digunakan dalam pembentukan NAKT
     */
    public void buildNAKT(Map<TupleDe<String, List<Boolean>>, List<TupleDe<TupleDe<Boolean, Integer>, String>>> subscriberTopicMap,
                          List<TupleDe<Integer, Integer>> existingAttributes) {
        if (subscriberTopicMap == null || subscriberTopicMap.isEmpty() || existingAttributes == null || existingAttributes.isEmpty()) {
            return;
        }

        for (Map.Entry<TupleDe<String, List<Boolean>>, List<TupleDe<TupleDe<Boolean, Integer>, String>>> entry : subscriberTopicMap.entrySet()) {
            TupleDe<String, List<Boolean>> subscriberInfo = entry.getKey();
            List<TupleDe<TupleDe<Boolean, Integer>, String>> topicList = entry.getValue();

            if (topicList == null || topicList.isEmpty()) continue;

            System.out.println("▶ Building NAKT for Subscriber: " + subscriberInfo.getFirst());

            for (TupleDe<TupleDe<Boolean, Integer>, String> topic : topicList) {
                TupleDe<Boolean, Integer> topicData = topic.getFirst();
                int topicId = topicData.getSecond();

                System.out.println("\uD83C\uDF32 Root of Tree for Topic [" + topicId + "]");

                String rootKey = keyManager.generateRootKey();
                System.out.println("\uD83D\uDD10 Encrypted Root Key: " + rootKey);

                int adjustedMaxValue = getNearestPowerOfTwo(topicId) - 1;
                encryptTreeNodes(0, adjustedMaxValue, rootKey, "", 1);
            }
            System.out.println("✅ NAKT Build Completed for: " + subscriberInfo.getFirst() + "\n");
        }
    }

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
