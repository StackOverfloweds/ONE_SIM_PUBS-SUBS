package KDC.NAKT;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

public class KeyManager {
    private static final String HMAC_ALGO = "HmacSHA256"; // Algorithm used for key generation
    private final String kdcSecret; // Secret key for the Key Distribution Center (KDC)

    /**
     * Constructor for KeyManager.
     * Generates a random secret key for the Key Distribution Center (KDC).
     */
    public KeyManager() {
        this.kdcSecret = generateRandomSecret();
    }

    /**
     * Generates an authorization key K(w).
     * K(w) = HMAC(KDC_secret, topic)
     *
     * @param num The numeric topic identifier.
     * @return The generated authorization key as a Base64 encoded string.
     */
    public String generateAuthorizationKey(int num) {
        return generateHMAC(kdcSecret, String.valueOf(num));
    }

    /**
     * Generates a root key Ø.
     * Ø = HMAC(K(w), num)
     *
     * @param num The numeric topic identifier.
     * @return The generated root key as a Base64 encoded string.
     */
    public String generateRootKey(int num) {
        String authorizationKey = generateAuthorizationKey(num);
        return generateHMAC(authorizationKey, String.valueOf(num));
    }

    /**
     * Generates a child key using a parent key and binary path.
     * ChildKey = HMAC(ParentKey, BinaryPath)
     *
     * @param parentKey  The parent key used for derivation.
     * @param binaryPath The binary path of the node.
     * @return The derived child key as a Base64 encoded string.
     */
    public static String generateChildKey(String parentKey, String binaryPath) {
        return generateHMAC(parentKey, binaryPath);
    }

    /**
     * Generates an HMAC-based key.
     * HMAC(Key, Message) -> Hash
     *
     * @param key     The secret key used for HMAC computation.
     * @param message The message to be hashed.
     * @return The generated HMAC as a Base64 encoded string.
     */
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

    /**
     * Generates a random secret key for the Key Distribution Center (KDC).
     *
     * @return A random secret key as a Base64 encoded string.
     */
    private String generateRandomSecret() {
        byte[] secretBytes = new byte[16];
        new SecureRandom().nextBytes(secretBytes);
        return Base64.getEncoder().encodeToString(secretBytes);
    }
}
