package KDC.NAKT;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class KeyManager {
    private static final String HMAC_ALGO = "HmacSHA256";
    private final String kdcSecret;

    public KeyManager() {
        this.kdcSecret = generateRandomSecret();
    }

    // Menghasilkan authorization key K(w) = HMAC(KDC_secret, topic)
    public String generateAuthorizationKey(int num) {
        return generateHMAC(kdcSecret, String.valueOf(num));
    }

    // Menghasilkan root key Ø = HMAC(K(w), num)
    public String generateRootKey(int num) {
        String authorizationKey = generateAuthorizationKey(num);
        return generateHMAC(authorizationKey, String.valueOf(num));
    }

    // Menghasilkan child key berdasarkan parentKey dan binaryPath
    public static String generateChildKey(String parentKey, String binaryPath) {
        return generateHMAC(parentKey, binaryPath);
    }

    // Generate HMAC
    private static String generateHMAC(String key, String message) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(), HMAC_ALGO);
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(message.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Error generating HMAC", e);
        }
    }

    // Menghasilkan secret key secara acak untuk KDC
    private String generateRandomSecret() {
        byte[] secretBytes = new byte[16];
        new SecureRandom().nextBytes(secretBytes);
        return Base64.getEncoder().encodeToString(secretBytes);
    }

    public static String deriveSharedKey(String subscriberKey, String binaryPath) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            SecretKeySpec keySpec = new SecretKeySpec(subscriberKey.getBytes(), HMAC_ALGO);
            mac.init(keySpec);
            byte[] derivedBytes = mac.doFinal(binaryPath.getBytes());

            byte[] fixedKey = fixKeyLength(derivedBytes);
            String derivedKeyBase64 = Base64.getEncoder().encodeToString(fixedKey);

            System.out.println("🔹 Derived Shared Key: " + derivedKeyBase64 + " | Path: " + binaryPath);
            return derivedKeyBase64;
        } catch (Exception e) {
            throw new RuntimeException("Error deriving key", e);
        }
    }

    private static byte[] fixKeyLength(byte[] keyBytes) {
        int[] validLengths = {16, 24, 32};
        for (int len : validLengths) {
            if (keyBytes.length == len) {
                return keyBytes;
            }
        }
        byte[] fixedKey = new byte[16]; // Default AES-128
        System.arraycopy(keyBytes, 0, fixedKey, 0, Math.min(keyBytes.length, fixedKey.length));
        return fixedKey;
    }

    // 🔹 **Menghasilkan Semua Key Turunan**
    public static List<String> getAllDerivedKeys(String subscriberKey, String binaryPath) {
        List<String> keys = new ArrayList<>();

        for (int i = 0; i <= binaryPath.length(); i++) {
            String subPath = binaryPath.substring(0, i);
            keys.add(deriveSharedKey(subscriberKey, subPath));
        }

        return keys;
    }

    // 🔹 **Generate Key untuk Publisher Secara Dinamis**
    public static String generatePublisherKey(String rootKey, String binaryPath) {
        return generateHMAC(rootKey, binaryPath);
    }
}
