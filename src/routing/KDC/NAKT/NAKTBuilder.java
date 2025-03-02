package routing.KDC.NAKT;

import core.DTNHost;
import core.Message;
import core.SimScenario;
import routing.util.TupleDe;
import java.util.*;

public class NAKTBuilder extends KeyManager {
    private Map<DTNHost, TupleDe<String, String>> keyEncryption;
    private Map<DTNHost, List<TupleDe<String, String>>> keyAuthentication;

    private final int lcnum;
    public NAKTBuilder(int lcnum) {
        super(); // Call parent constructor of KeyManager
        this.lcnum = lcnum;
        this.keyEncryption = new HashMap<>();
        this.keyAuthentication = new HashMap<>();
    }

    /**
     * Builds the NAKT key structure.
     * - Generates keys for publishers and subscribers.
     * - Assigns appropriate encryption keys to topics and attributes.
     *
     * @param kdcHosts host is the kdc to process the building
     * @param msg      get msg
     * @return true if the NAKT key structure is successfully built, false otherwise.
     */
    public boolean buildNAKT(List<DTNHost> kdcHosts, Message msg) {
        boolean success = false;

        for (DTNHost kdcHost : kdcHosts) {
            if (!kdcHost.isKDC()) continue;

            Map<DTNHost, List<TupleDe<Boolean, Integer>>> registerData =
                    (Map<DTNHost, List<TupleDe<Boolean, Integer>>>) msg.getProperty("KDC_Register_");
            Map<DTNHost, List<TupleDe<List<Boolean>, List<TupleDe<Integer, Integer>>>>> getUnSubs =
                    (Map<DTNHost, List<TupleDe<List<Boolean>, List<TupleDe<Integer, Integer>>>>>) msg.getProperty("KDC_Subscribe_");

            if (registerData == null || registerData.isEmpty() || getUnSubs == null || getUnSubs.isEmpty()) {
                continue;
            }

            for (Map.Entry<DTNHost, List<TupleDe<Boolean, Integer>>> entry : registerData.entrySet()) {
                DTNHost subscriber = entry.getKey();
                List<TupleDe<Boolean, Integer>> subscriberInfo = entry.getValue();
                if (subscriberInfo == null || subscriberInfo.isEmpty()) continue;

                TupleDe<Boolean, Integer> firstEntry = subscriberInfo.get(0);
                int attributeValue = firstEntry.getSecond();
                boolean topicVal = firstEntry.getFirst();

                if (!keyEncryption.containsKey(subscriber)) {
                    String rootKey = generateRootKey(topicVal);
                    List<TupleDe<String, String>> keyList = new ArrayList<>();
                    encryptTreeNodes(0, getNearestPowerOfTwo(attributeValue) - 1, rootKey, "", 1, keyList);

                    String binaryPathPubs = Integer.toBinaryString(attributeValue);
                    TupleDe<String, String> selectedKey = keyList.stream()
                            .filter(tuple -> tuple.getFirst().equals(binaryPathPubs))
                            .findFirst()
                            .orElse(null);

                    if (selectedKey != null) {
                        keyEncryption.put(subscriber, selectedKey);
                        msg.addProperty("KDC_Key_Encryption_", keyEncryption);
                    }
                }

                if (!keyAuthentication.containsKey(subscriber)) {
                    List<TupleDe<String, String>> derivedKeys = new ArrayList<>();
                    List<TupleDe<Integer, Integer>> existingAttributes = new ArrayList<>();

                    List<TupleDe<List<Boolean>, List<TupleDe<Integer, Integer>>>> unsubData = getUnSubs.get(subscriber);
                    if (unsubData != null) {
                        for (TupleDe<List<Boolean>, List<TupleDe<Integer, Integer>>> unsubEntry : unsubData) {
                            existingAttributes.addAll(unsubEntry.getSecond());
                        }
                    }

                    if (!existingAttributes.isEmpty()) {
                        String rootKey = generateRootKey(topicVal);
                        List<TupleDe<String, String>> keyList = new ArrayList<>();
                        encryptTreeNodes(0, getNearestPowerOfTwo(attributeValue) - 1, rootKey, "", 1, keyList);

                        for (TupleDe<Integer, Integer> range : existingAttributes) {
                            for (int i = range.getFirst(); i <= range.getSecond(); i++) {
                                String binaryPathSubs = Integer.toBinaryString(i);
                                for (TupleDe<String, String> keyTuple : keyList) {
                                    if (binaryPathSubs.startsWith(keyTuple.getFirst()) && !derivedKeys.contains(keyTuple)) {
                                        derivedKeys.add(keyTuple);
                                    }
                                }
                            }
                        }

                        if (!derivedKeys.isEmpty()) {
                            keyAuthentication.put(subscriber, derivedKeys);
                            msg.addProperty("KDC_Key_Authentication_", keyAuthentication);
                        }
                    }
                }
            }

            success = true;
        }
        return success;
    }

    /**
     * Finds the nearest power of two that is greater than or equal to the given value.
     *
     * @param value The input integer.
     * @return The nearest power of two.
     */
    public int getNearestPowerOfTwo(int value) {
        int power = 1;
        while (power < value) {
            power *= 2;
        }
        return power;
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
    public void encryptTreeNodes(int min, int max, String parentKey, String binaryPath, int depth, List<TupleDe<String, String>> keyList) {
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
//        System.out.println("🔹 Level " + depth + " (" + binaryPath + ")");
//        System.out.println("  ├── Left Key (" + leftPath + "): " + leftKey);
//        System.out.println("  └── Right Key (" + rightPath + "): " + rightKey);

        // 🔹 **Recursive call to generate deeper key nodes**
        encryptTreeNodes(min, mid, leftKey, leftPath, depth + 1, keyList);
        encryptTreeNodes(mid + 1, max, rightKey, rightPath, depth + 1, keyList);
    }


}

