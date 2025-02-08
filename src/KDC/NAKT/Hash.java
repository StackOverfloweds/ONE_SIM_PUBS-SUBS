package KDC.NAKT;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class Hash {
    public static String hash(String input, String algorithm) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] hashBytes = digest.digest(input.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static String hmacSha1(String key, byte[] messageBytes) throws Exception {
        // Create a SecretKeySpec for the HMAC key
        SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(), "HmacSHA1");

        // Initialize the Mac instance with HMAC-SHA1
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(secretKeySpec);

        // Compute the HMAC
        byte[] hmac = mac.doFinal(messageBytes);

        // Return the HMAC as a Base64-encoded string
        return Base64.getEncoder().encodeToString(hmac);
    }
}
