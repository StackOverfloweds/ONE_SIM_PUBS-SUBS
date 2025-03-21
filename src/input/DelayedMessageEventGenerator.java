package input;

import java.util.PriorityQueue;
import java.util.Queue;

import core.Settings;

/**
 * Delayed Message Event Generator - menunda eksekusi pesan hingga waktu di tengah interval.
 */
public class DelayedMessageEventGenerator extends MessageEventGenerator {
    /** Antrian prioritas untuk menyimpan event berdasarkan waktu eksekusi */
    private Queue<ExternalEvent> eventQueue;

    private Queue<MessageCreateEvent> pendingEvents = new PriorityQueue<>(
            (e1, e2) -> Double.compare(e1.getTime(), e2.getTime())
    );


    /**
     * Konstruktor untuk generator event dengan delay eksekusi.
     * @param s Settings untuk event generator.
     */
    public DelayedMessageEventGenerator(Settings s) {
        super(s);
        this.eventQueue = new PriorityQueue<>((e1, e2) -> Double.compare(e1.getTime(), e2.getTime()));
    }

    /**
     * Membuat pesan baru dan menambahkannya ke antrian dengan waktu eksekusi di tengah interval.
     */
    public void createNewMessage() {
        int responseSize = 0;
        int from = drawHostAddress(this.hostRange);
        int to = drawToAddress(hostRange, from);
        int msgSize = drawMessageSize();

        double firstEventsTime = (this.msgTime != null ? this.msgTime[0] : 0)
                + msgInterval[0]
                + (msgInterval[0] == msgInterval[1] ? 0 : rng.nextInt(msgInterval[1] - msgInterval[0]));

        double secondEventsTime = firstEventsTime
                + (msgInterval[1] - msgInterval[0]) / 2.0
                + (msgInterval[0] == msgInterval[1] ? 0 : rng.nextInt((msgInterval[1] - msgInterval[0]) / 4));

        if (this.msgTime != null && secondEventsTime > this.msgTime[1]) {
            secondEventsTime = this.msgTime[1] - 0.1;
        }

        double scheduledTime = secondEventsTime + Math.random() * 0.05;

        // Tambahkan event ke pendingEvents terlebih dahulu
        MessageCreateEvent mce = new MessageCreateEvent(from, to, this.getID(), msgSize, responseSize, scheduledTime);
        pendingEvents.add(mce);

        // Jadwalkan eksekusi event dengan delay tertentu
        new Thread(() -> {
            try {
                Thread.sleep(2000); // Delay eksekusi selama 2 detik (bisa disesuaikan)
                eventQueue.add(mce); // Masukkan event ke eventQueue setelah delay
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();

        this.nextEventsTime = scheduledTime + drawNextEventTimeDiff();
    }


    /**
     * Mengembalikan event berikutnya dalam antrian.
     * @return Event berikutnya atau ExternalEvent dengan waktu MAX jika kosong.
     */
    @Override
    public ExternalEvent nextEvent() {
        ExternalEvent event = eventQueue.isEmpty() ? new ExternalEvent(Double.MAX_VALUE) : eventQueue.poll();
        return event;
    }


    /**
     * Mengembalikan waktu event berikutnya dalam antrian.
     * @return Waktu event berikutnya atau MAX jika tidak ada event.
     */
    @Override
    public double nextEventsTime() {
        return eventQueue.isEmpty() ? Double.MAX_VALUE : eventQueue.peek().getTime();
    }
}
