package KDC.Publisher;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.InvalidKeyException;
import java.util.Random;

public class EncryptionUtil {

    private static final String ALGORITHM = "HmacSHA256";
    /**
     * Menghasilkan hash HMAC-SHA256 dari pesan yang diberikan dengan kunci enkripsi.
     *
     * @param message Pesan yang akan di-hash
     * @param key     Kunci enkripsi
     * @return Hasil hash dalam format heksadesimal
     */
    public static String hashWithHmacSHA256(String message, String key) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            mac.init(secretKeySpec);

            byte[] hashBytes = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            StringBuilder hashString = new StringBuilder();

            for (byte b : hashBytes) {
                hashString.append(String.format("%02x", b)); // Konversi ke format hex
            }

            return hashString.toString();
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