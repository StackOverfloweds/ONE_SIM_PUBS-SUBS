package KDC.Publisher;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Random;

public class EncryptionUtil {

    private static final String AES_ALGO = "AES/CBC/PKCS5Padding";

    public static String encrypt(String msg, String encryptionKey) {
        try {
            if (encryptionKey == null || encryptionKey.isEmpty()) {
                throw new RuntimeException("❌ Encryption key is NULL or EMPTY!");
            }

            byte[] decodedKey = Base64.getDecoder().decode(encryptionKey);
            decodedKey = fixKeyLength(decodedKey);

            System.out.println("🔹 Decoded Key Length: " + decodedKey.length);

            Cipher cipher = Cipher.getInstance(AES_ALGO);
            SecretKeySpec secretKeySpec = new SecretKeySpec(decodedKey, "AES");

            byte[] iv = new byte[16];
            new SecureRandom().nextBytes(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivSpec);
            byte[] encrypted = cipher.doFinal(msg.getBytes());

            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error encrypting message", e);
        }
    }


    private static byte[] fixKeyLength(byte[] key) {
        int[] validLengths = {16, 24, 32};
        for (int len : validLengths) {
            if (key.length == len) {
                return key;
            }
        }
        byte[] fixedKey = new byte[16]; // Default AES-128
        System.arraycopy(key, 0, fixedKey, 0, Math.min(key.length, fixedKey.length));
        return fixedKey;
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
