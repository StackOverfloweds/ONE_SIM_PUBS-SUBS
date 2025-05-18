package input;

import core.DTNHost;
import core.Message;
import core.MessageListener;
import core.World;
import routing.KDC.GetAllKDC;
import routing.KDC.NAKT.NAKTBuilder;
import routing.KDC.Publisher.Register;
import routing.KDC.Subscriber.Subscribe;

import java.util.HashMap;
import java.util.List;

public class MessageCreateEventNAKT extends MessageEvent {
    private int size;
    private int responseSize;
    private HashMap<String, Message> messages;
    protected List<MessageListener> mListeners;
    private NAKTBuilder naktBuilder;
    private Register register;
    private Subscribe subscribe;
    private GetAllKDC getAllKDC;

    /**
     * Creates a message creation event with an optional response request
     *
     * @param from         The creator of the message
     * @param to           Where the message is destined to
     * @param id           ID of the message
     * @param size         Size of the message
     * @param responseSize Size of the requested response message or 0 if
     *                     no response is requested
     * @param time         Time, when the message is created
     */
    public MessageCreateEventNAKT(int from, int to, String id, int size,
                                  int responseSize, double time, int lcnum) {
        super(from, to, id, time);
        this.size = size;
        this.responseSize = responseSize;
        this.naktBuilder = new NAKTBuilder(lcnum);
        this.register = new Register();
        this.subscribe = new Subscribe();
        this.getAllKDC = new GetAllKDC();
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
        boolean registrationData = register.sendMsgForRegistration(from, m);
        boolean eventCreated = false;
        if (registrationData) {
            boolean subscribeData = subscribe.sendMsgForSubscribe(m);
            if (subscribeData) {
                eventCreated = true;
            }
        }
        if (eventCreated) {
            naktBuilder.buildNAKT(getAllKDC.getAllKDCs(), m); // build nakt in host kdc
        }
        from.createNewMessage(m);
    }
    @Override
    public String toString() {
        return super.toString() + " [" + fromAddr + "->" + toAddr + "] " +
                "size:" + size + " CREATE";
    }

}
