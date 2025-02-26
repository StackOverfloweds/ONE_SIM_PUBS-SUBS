package input;

import core.DTNHost;
import core.Message;
import core.World;

public class MessageCreateEventNAKT extends MessageEvent {
    private int size;
    private int responseSize;
    private int keyEnc;
    private int keyDec;
    private int subs;
    private int regis;

    /**
     * Creates a message creation event with an optional response request
     * @param from The creator of the message
     * @param to Where the message is destined to
     * @param id ID of the message
     * @param size Size of the message
     * @param responseSize Size of the requested response message or 0 if
     * no response is requested
     * @param time Time, when the message is created
     */
    public MessageCreateEventNAKT(int from, int to, String id, int size,
                                  int responseSize, double time) {
        super(from, to, id, time);
        this.size = size;
        this.responseSize = responseSize;
    }

    /**
     * Creates the message this event represents.
     */
    @Override
    public void processEvent(World world) {
        DTNHost to = world.getNodeByAddress(this.toAddr);
        DTNHost from = world.getNodeByAddress(this.fromAddr);

        Message m = new Message(from, to, this.id, this.size);
        m.setResponseSize(this.responseSize);
        from.createNewMessage(m);
    }

    @Override
    public String toString() {
        return super.toString() + " [" + fromAddr + "->" + toAddr + "] " +
                "size:" + size + " CREATE";
    }
}
