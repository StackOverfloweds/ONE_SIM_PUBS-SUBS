package KDC.NAKT;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

public class KeyManager {
    private static final String HMAC_ALGO = "HmacSHA1";
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
    public String generateChildKey(String parentKey, String binaryPath) {
        return generateHMAC(parentKey, binaryPath);
    }

    // Generate HMAC
    private String generateHMAC(String key, String message) {
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
}
