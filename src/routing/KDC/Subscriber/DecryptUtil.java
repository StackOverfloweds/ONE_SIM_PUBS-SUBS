package routing.KDC.Subscriber;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.List;
import routing.util.TupleDe;

public class DecryptUtil {
    private static final String AES_ALGO = "AES/GCM/NoPadding";
    private static final int IV_SIZE = 12;
    private static final int TAG_LENGTH = 128;

    public static TupleDe<String, String> decryptMessage(String encryptedMessage, List<TupleDe<String, String>> keyList) {
        TupleDe<String, String> lastAttempt = null;

        for (TupleDe<String, String> keyTuple : keyList) {
            String binaryPath = keyTuple.getFirst();
            String keyBase64 = keyTuple.getSecond();
            String decryptedText = "";

            try {
                byte[] keyBytes = Base64.getDecoder().decode(keyBase64);
                SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, "AES");

                byte[] combined = Base64.getDecoder().decode(encryptedMessage);
                byte[] iv = new byte[IV_SIZE];
                System.arraycopy(combined, 0, iv, 0, iv.length);
                GCMParameterSpec ivSpec = new GCMParameterSpec(TAG_LENGTH, iv);

                byte[] encryptedBytes = new byte[combined.length - iv.length];
                System.arraycopy(combined, iv.length, encryptedBytes, 0, encryptedBytes.length);

                Cipher cipher = Cipher.getInstance(AES_ALGO);
                cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivSpec);
                byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
                decryptedText = new String(decryptedBytes);
                return new TupleDe<>(binaryPath, decryptedText);
            } catch (Exception e) {
                lastAttempt = new TupleDe<>(binaryPath, decryptedText); //attempt last hash
            }
        }

        //
        return (lastAttempt != null) ? lastAttempt : new TupleDe<>("", "");
    }
}
