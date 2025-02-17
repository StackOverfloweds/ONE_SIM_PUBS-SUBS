package KDC.Publisher;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.InvalidKeyException;
import java.util.Base64;
import java.util.Random;

public class EncryptionUtil {

    private static final String ALGORITHM = "HmacSHA256";

    /**
     * Menghasilkan hash HMAC-SHA256 dari pesan yang diberikan dengan kunci enkripsi.
     *
     * @param message Pesan yang akan di-hash
     * @param key     Kunci enkripsi
     * @return Hasil hash dalam format Base64
     */
    public static String hashWithHmacSHA256(String message, String key) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            mac.init(secretKeySpec);

            byte[] hashBytes = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            // Base64 encode the hash bytes
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Error hashing message with HMAC-SHA256", e);
        }
    }

    // **Fungsi untuk menghasilkan string acak**
    public static String generateRandomString(int length) {
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        Random random = new Random();
        StringBuilder sb = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            sb.append(characters.charAt(random.nextInt(characters.length())));
        }

        return sb.toString();
    }
}
