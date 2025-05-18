package routing.KDC.NAKT;

import core.*;
import routing.CCDTN;
import routing.util.TupleDe;

import java.util.*;

public class NAKTBuilder extends KeyManager {
    private Map<DTNHost, TupleDe<String, String>> keyEncryption;
    private Map<DTNHost, List<TupleDe<String, String>>> keyAuthentication;
    private final int lcnum;
    Map<DTNHost, Integer> kdcLoad;
    Map<DTNHost, Integer> numberKeyLoad;
    Map<DTNHost, Integer> numKeyLoadPublisher;
    private final int bufferThreshold = 10;
    public NAKTBuilder(int lcnum) {
        super(); // Call parent constructor of KeyManager
        this.lcnum = lcnum;
        this.keyEncryption = new HashMap<>();
        this.keyAuthentication = new HashMap<>();
        this.kdcLoad = CCDTN.kdcLoad != null ? CCDTN.kdcLoad : new HashMap<>();
        this.numberKeyLoad = CCDTN.numberKeyLoad != null ? CCDTN.numberKeyLoad : new HashMap<>();
        this.numKeyLoadPublisher = CCDTN.numberKeyLoadPublisher !=null ? CCDTN.numberKeyLoadPublisher : new HashMap<>();
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
            int processCount = 0; // Track the number of key derivation & distribution operations
            for (Map.Entry<DTNHost, List<TupleDe<Boolean, Integer>>> entry : registerData.entrySet()) {
                DTNHost publisher = entry.getKey();
                List<TupleDe<Boolean, Integer>> subscriberInfo = entry.getValue();
                if (subscriberInfo == null || subscriberInfo.isEmpty()) continue;

                boolean topicVal = subscriberInfo.get(0).getFirst();

                for (Map.Entry<DTNHost, List<TupleDe<List<Boolean>, List<TupleDe<Integer, Integer>>>>> entrySubs : getUnSubs.entrySet()) {
                    DTNHost subscriber = entrySubs.getKey();
                    List<TupleDe<List<Boolean>, List<TupleDe<Integer, Integer>>>> tuplesList = entrySubs.getValue();

                    for (TupleDe<List<Boolean>, List<TupleDe<Integer, Integer>>> tuple : tuplesList) {
                        List<TupleDe<Integer, Integer>> intPairsList = tuple.getSecond();

                        for (TupleDe<Integer, Integer> intPair : intPairsList) {
                            int secondValue = intPair.getSecond();

                            // 🔹 Ensure publisher only gets a key once
                            if (!keyEncryption.containsKey(publisher)) {
                                handleEncryption(publisher, topicVal, secondValue, msg);
                            }
                            // 🔹 Ensure subscriber only gets a key once
                            if (!keyAuthentication.containsKey(subscriber)) {
                                handleAuthentication(subscriber, topicVal, secondValue, getUnSubs, msg);

                            }
                        }
                    }
                }
            }
            success = true;
        }

        return success;
    }

    private void handleEncryption(DTNHost publisher, boolean topicVal, int secondValue, Message msg) {
        String rootKey = generateRootKey(topicVal);
        List<TupleDe<String, String>> keyList = new ArrayList<>();
        int maxRange = getNearestPowerOfTwo(secondValue) - 1;

        encryptTreeNodes(0, maxRange, rootKey, "", 1, keyList);

        String binaryPathPubs = Integer.toBinaryString(secondValue);
        TupleDe<String, String> selectedKey = keyList.stream()
                .filter(tuple -> tuple.getFirst().equals(binaryPathPubs))
                .findFirst()
                .orElse(null);
        //  **Cek duplikasi sebelum memasukkan key**
        if (selectedKey != null) {
            if (!keyEncryption.containsKey(publisher) || !keyEncryption.get(publisher).equals(selectedKey)) {
                keyEncryption.put(publisher, selectedKey);
                numKeyLoadPublisher.put(publisher, 1);
                msg.addProperty("KDC_Key_Encryption_", keyEncryption);
                addMessageToHostsAndForward(msg);
            }
        }
    }



    private void handleAuthentication(DTNHost subscriber, boolean topicVal, int secondValue,
                                      Map<DTNHost, List<TupleDe<List<Boolean>, List<TupleDe<Integer, Integer>>>>> getUnSubs,
                                      Message msg) {

        // A set to store derived keys (ensures no duplicates in key list at this stage)
        Set<TupleDe<String, String>> derivedKeysSet = new HashSet<>();

        // Aggregate existing attributes for this subscriber
        List<TupleDe<Integer, Integer>> existingAttributes = new ArrayList<>();
        List<TupleDe<List<Boolean>, List<TupleDe<Integer, Integer>>>> unsubData = getUnSubs.get(subscriber);

        if (unsubData != null) {
            for (TupleDe<List<Boolean>, List<TupleDe<Integer, Integer>>> unsubEntry : unsubData) {
                existingAttributes.addAll(unsubEntry.getSecond());
            }
        }

        // Only process if there are existing attributes
        if (!existingAttributes.isEmpty()) {
            String rootKey = generateRootKey(topicVal); // Generate root encryption key
            List<TupleDe<String, String>> keyList = new ArrayList<>();
            int maxRange = getNearestPowerOfTwo(secondValue) - 1;

            // Generate the list of keys hierarchically using binary paths
            encryptTreeNodes(0, maxRange, rootKey, "", 1, keyList);

            // Determine the needed key for each range in existingAttributes
            for (TupleDe<Integer, Integer> existingRange : existingAttributes) {
                for (int i = existingRange.getFirst(); i <= existingRange.getSecond(); i++) {
                    for (TupleDe<String, String> keyTuple : keyList) {
                        TupleDe<Integer, Integer> nodeRange = getRangeFromBinaryPath(keyTuple.getFirst(), maxRange);

                        // Check if 'i' falls within the range of the binary path
                        if (i >= nodeRange.getFirst() && i <= nodeRange.getSecond()) {
                            String binaryPath = keyTuple.getFirst();
                            String binaryI = String.format("%" + lcnum + "s", Integer.toBinaryString(i)).replace(' ', '0');

                            // Add the key if the exact binary path matches
                            if (binaryI.equals(binaryPath)) {
                                derivedKeysSet.add(keyTuple);
                                break; // No need to keep checking other keys for the same value
                            }
                        }
                    }
                }
            }

            // Convert the derived keys set to a list (should contain only unique keys at this point)
            List<TupleDe<String, String>> derivedKeys = new ArrayList<>(derivedKeysSet);

            // Allow only one key to be assigned to the subscriber
            // This ensures the subscriber only gets one key even if they match multiple ranges.
            if (!derivedKeys.isEmpty()) {
                TupleDe<String, String> selectedKey = derivedKeys.get(0); // Pick the first key (or any suitable criterion can be applied)

                // Check if the subscriber already has the key to avoid duplicates
                if (keyAuthentication.containsKey(subscriber)) {
                    List<TupleDe<String, String>> existingKeys = keyAuthentication.get(subscriber);
                    if (existingKeys != null && existingKeys.contains(selectedKey)) {
                        return; // Subscriber already has the key; exit to avoid redundant processing
                    }
                }

                // Update key mappings for the subscriber
                keyAuthentication.put(subscriber, Collections.singletonList(selectedKey));
                numberKeyLoad.put(subscriber, 1);

                // Add the key authentication data to the message and forward
                msg.addProperty("KDC_Key_Authentication_", keyAuthentication);
                addMessageToHostsAndForward(msg);
            }
        }
    }



    private TupleDe<Integer, Integer> getRangeFromBinaryPath(String path, int maxRange) {
        int min = 0;
        int max = maxRange;

        for (char c : path.toCharArray()) {
            int mid = (min + max) / 2;
            if (c == '0') {
                max = mid;
            } else {
                min = mid + 1;
            }
        }
        return new TupleDe<>(min, max);
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


//        // 🔹 **Logging Debugging**
//        System.out.println("🔹 Level " + depth + " (" + binaryPath + ")");
//        System.out.println("  ├── Left Key (" + leftPath + "): " + leftKey);
//        System.out.println("  └── Right Key (" + rightPath + "): " + rightKey);

        // 🔹 **Recursive call to generate deeper key nodes**
        encryptTreeNodes(min, mid, leftKey, leftPath, depth + 1, keyList);
        encryptTreeNodes(mid + 1, max, rightKey, rightPath, depth + 1, keyList);
    }


    /**
     * Sends the message to brokers first, then forwards it to relevant hosts in keyEncryption and keyAuthentication.
     *
     * @param msg     The message to be sent.
     */
    private void addMessageToHostsAndForward(Message msg) {
        // Send to all brokers first
        for (DTNHost host : SimScenario.getInstance().getHosts()) {
            for (Connection con : host.getConnections()) {
                DTNHost other = con.getOtherNode(host);
                if (other != null && other.isBroker()) {
                    if (msg.getProperty("KDC_Register_") != null) {
                        other.addBufferToHost(msg);
                        ForwardMSG(msg);
                    }
                    if (msg.getProperty("KDC_Subscribe_") != null) {
                        other.addBufferToHost(msg);
                        ForwardMSG(msg);
                    }
                }
            }
        }
    }

    private void ForwardMSG(Message msg) {
        for (DTNHost host : SimScenario.getInstance().getHosts()) {
            for (Connection con : host.getConnections()) {
                DTNHost other = con.getOtherNode(host);
                if (other != null) {
                    if (msg.getProperty("KDC_Register_") != null && other.isPublisher()) {
                        Map<DTNHost, TupleDe<String, String>> register =
                                (Map<DTNHost, TupleDe<String, String>>) msg.getProperty("KDC_Register_");
                        if (register.containsKey(other)) {
                            other.addBufferToHost(msg);
                        }
                    }
                    if (msg.getProperty("KDC_Subscribe_") != null) {
                        Map<DTNHost, List<TupleDe<String, String>>> subscriber =
                                (Map<DTNHost, List<TupleDe<String, String>>>) msg.getProperty("KDC_Subscribe_");
                        if (subscriber.containsKey(other)) {
                            other.addBufferToHost(msg);
                        }
                    }
                }
            }
        }
    }

}

