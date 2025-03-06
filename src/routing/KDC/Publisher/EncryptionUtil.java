package routing.KDC.Publisher;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Random;

public class EncryptionUtil {
    private static final String AES_ALGO = "AES/GCM/NoPadding";
    private static final int IV_SIZE = 12; // **Ukuran IV untuk AES-GCM**
    private static final int TAG_LENGTH = 128; // **Tag Authentication**

    public static String encryptMessage(String plainText, String keyBase64) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(keyBase64);
            SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, "AES");

            SecureRandom secureRandom = new SecureRandom();
            byte[] iv = new byte[IV_SIZE];
            secureRandom.nextBytes(iv); // **Generate IV secara acak**
            GCMParameterSpec ivSpec = new GCMParameterSpec(TAG_LENGTH, iv);

            Cipher cipher = Cipher.getInstance(AES_ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivSpec);
            byte[] encryptedBytes = cipher.doFinal(plainText.getBytes());

            // 🔹 **Gabungkan IV + Encrypted Data**
            byte[] combined = new byte[iv.length + encryptedBytes.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encryptedBytes, 0, combined, iv.length, encryptedBytes.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Error encrypting message", e);
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
