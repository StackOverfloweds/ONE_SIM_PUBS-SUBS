package KDC.Subscriber;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public class DecryptUtil {

    private static final String AES_ALGO = "AES/CBC/PKCS5Padding";

    public static String decrypt(String encryptedMsg, String decryptionKey) {
        try {
            if (decryptionKey == null || decryptionKey.isEmpty()) {
                throw new RuntimeException("❌ Decryption key is NULL or EMPTY!");
            }

            byte[] decodedKey = Base64.getDecoder().decode(decryptionKey);
            decodedKey = fixKeyLength(decodedKey);  // Pastikan key memiliki panjang yang valid

            System.out.println("🔹 Decoded Key Length: " + decodedKey.length);

            Cipher cipher = Cipher.getInstance(AES_ALGO);
            SecretKeySpec secretKeySpec = new SecretKeySpec(decodedKey, "AES");

            byte[] combined = Base64.getDecoder().decode(encryptedMsg);

            // Ambil IV dari pesan terenkripsi
            byte[] iv = new byte[16];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            // Ambil data terenkripsi
            byte[] encryptedBytes = new byte[combined.length - iv.length];
            System.arraycopy(combined, iv.length, encryptedBytes, 0, encryptedBytes.length);

            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivSpec);
            byte[] decrypted = cipher.doFinal(encryptedBytes);

            return new String(decrypted);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error decrypting message", e);
        }
    }

    private static byte[] fixKeyLength(byte[] key) {
        int[] validLengths = {16, 24, 32};
        for (int len : validLengths) {
            if (key.length == len) {
                return key;
            }
        }
        byte[] fixedKey = new byte[16]; // Default ke AES-128
        System.arraycopy(key, 0, fixedKey, 0, Math.min(key.length, fixedKey.length));
        return fixedKey;
    }
}
