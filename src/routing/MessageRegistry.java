package routing;

import core.Message;

import java.util.List;

public interface MessageRegistry {
    void registerMessage(Message msg); // Menyimpan pesan baru
    List<Message> getPendingMessages(); // Mengambil pesan yang belum dikirim
    List<Message> getAllMessages(); // Mengambil semua pesan tanpa terkecuali
    void addMessage(Message msg); //add all msg to list
}
