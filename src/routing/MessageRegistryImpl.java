package routing;

import core.Message;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MessageRegistryImpl implements MessageRegistry {
    private final List<Message> messageList = new ArrayList<>();

    // Property yang diperhitungkan untuk pending messages
    private static final List<String> PENDING_PROPERTIES = Arrays.asList(
            "topic",
            "KDC_Get_Register_",
            "KDC_Register_",
            "KDC_Get_Subscribe_",
            "KDC_Subscribe_",
            "KDC_Key_Encryption_",
            "KDC_Key_Authentication_"
    );

    @Override
    public void registerMessage(Message msg) {
        messageList.add(msg);
    }
    @Override
    public void addMessage(Message msg) {
        // Langsung tambahkan pesan ke dalam daftar utama
        if (!messageList.contains(msg)) {
            messageList.add(msg);
        }
    }

    @Override
    public List<Message> getPendingMessages() {
        List<Message> pending = new ArrayList<>();
        for (Message msg : messageList) {
            for (String property : PENDING_PROPERTIES) {
                if (msg.getProperty(property) != null) {
                    pending.add(msg);
                    break; // Hanya tambahkan sekali jika salah satu property cocok
                }
            }
        }
        return pending;
    }

    @Override
    public List<Message> getAllMessages() {
        return new ArrayList<>(messageList); // Return semua pesan tanpa terkecuali
    }
}
