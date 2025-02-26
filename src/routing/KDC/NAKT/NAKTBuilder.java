package routing.KDC.NAKT;

import routing.util.TupleDe;
import java.util.*;

public class NAKTBuilder extends KeyManager {
    private final int lcnum;
    public NAKTBuilder(int lcnum) {
        super(); // Call parent constructor of KeyManager
        this.lcnum = lcnum;
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

