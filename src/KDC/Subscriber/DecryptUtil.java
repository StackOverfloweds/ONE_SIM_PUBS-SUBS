package KDC.Subscriber;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public class DecryptUtil {

    /**
     * Fungsi untuk mendekripsi pesan menggunakan HMAC-SHA256
     */
    public static String decryptHMAC5(String encryptedContent, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256"); // Use HmacSHA256 here
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(), "HmacSHA256");
            mac.init(secretKey);
            byte[] decodedBytes = mac.doFinal(Base64.getDecoder().decode(encryptedContent));
            return new String(decodedBytes);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
