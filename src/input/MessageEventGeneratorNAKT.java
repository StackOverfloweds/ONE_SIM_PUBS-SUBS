package input;

import java.util.Random;
import java.util.UUID;

import core.Settings;
import core.SettingsError;

public class MessageEventGeneratorNAKT implements EventQueue {
    public static final String MESSAGE_SIZE_S = "size";
    public static final String MESSAGE_INTERVAL_S = "interval";
    public static final String HOST_RANGE_S = "hosts";
    public static final String TO_HOST_RANGE_S = "tohosts";
    public static final String MESSAGE_TIME_S = "time";

    protected double nextEventsTime = 0;
    protected int[] hostRange = {0, 0};
    protected int[] toHostRange = null;
    private int id = 0;
    private int kdcRegisterId = 0;
    private int kdcSubscribeId = 0;
    protected String idPrefix;
    private int[] sizeRange;
    private int[] msgInterval;
    protected double[] msgTime;
    protected Random rng;

    public MessageEventGeneratorNAKT(Settings s){
        this.sizeRange = s.getCsvInts(MESSAGE_SIZE_S);
        this.msgInterval = s.getCsvInts(MESSAGE_INTERVAL_S);
        this.hostRange = s.getCsvInts(HOST_RANGE_S, 2);
        this.idPrefix = "M" + UUID.randomUUID().toString().substring(0, 3);

        if (s.contains(MESSAGE_TIME_S)) {
            this.msgTime = s.getCsvDoubles(MESSAGE_TIME_S, 2);
        } else {
            this.msgTime = null;
        }
        if (s.contains(TO_HOST_RANGE_S)) {
            this.toHostRange = s.getCsvInts(TO_HOST_RANGE_S, 2);
        } else {
            this.toHostRange = null;
        }

        this.rng = new Random();

        if (this.sizeRange.length == 1) {
            this.sizeRange = new int[] {this.sizeRange[0], this.sizeRange[0]};
        } else {
            s.assertValidRange(this.sizeRange, MESSAGE_SIZE_S);
        }
        if (this.msgInterval.length == 1) {
            this.msgInterval = new int[] {this.msgInterval[0], this.msgInterval[0]};
        } else {
            s.assertValidRange(this.msgInterval, MESSAGE_INTERVAL_S);
        }
        s.assertValidRange(this.hostRange, HOST_RANGE_S);

        this.nextEventsTime = (this.msgTime != null ? this.msgTime[0] : 0) + msgInterval[0] +
                (msgInterval[0] == msgInterval[1] ? 0 : rng.nextInt(msgInterval[1] - msgInterval[0]));
    }

    protected int drawHostAddress(int hostRange[]) {
        return (hostRange[1] == hostRange[0]) ? hostRange[0] : hostRange[0] + rng.nextInt(hostRange[1] - hostRange[0]);
    }

    protected int drawMessageSize() {
        return sizeRange[0] + (sizeRange[0] == sizeRange[1] ? 0 : rng.nextInt(sizeRange[1] - sizeRange[0]));
    }

    protected int drawNextEventTimeDiff() {
        return msgInterval[0] + (msgInterval[0] == msgInterval[1] ? 0 : rng.nextInt(msgInterval[1] - msgInterval[0]));
    }

    protected int drawToAddress(int hostRange[], int from) {
        int to;
        do {
            to = (this.toHostRange != null) ? drawHostAddress(this.toHostRange) : drawHostAddress(this.hostRange);
        } while (from == to);
        return to;
    }

    public ExternalEvent nextEvent() {
        int from = drawHostAddress(this.hostRange);
        int to = drawToAddress(hostRange, from);
        int msgSize = drawMessageSize();
        int interval = drawNextEventTimeDiff();

        String messageType = determineMessageType();
        String messageId = generateMessageID(messageType);

        MessageCreateEvent mce = new MessageCreateEvent(from, to, messageId, msgSize, 0, this.nextEventsTime);
        this.nextEventsTime += interval;

        if (this.msgTime != null && this.nextEventsTime > this.msgTime[1]) {
            this.nextEventsTime = Double.MAX_VALUE;
        }
        return mce;
    }

    public double nextEventsTime() {
        return this.nextEventsTime;
    }

    protected String generateMessageID(String type) {
        if ("KDC_REGISTER_".equals(type)) {
            return "KDC_REG-" + (++kdcRegisterId);
        } else if ("KDC_SUBSCRIBE_".equals(type)) {
            return "KDC_SUB-" + (++kdcSubscribeId);
        } else {
            return idPrefix + "-" + (++id);
        }
    }

    protected String determineMessageType() {
        double randValue = rng.nextDouble();
        if (randValue < 0.3) {
            return "KDC_REGISTER_";
        } else if (randValue < 0.6) {
            return "KDC_SUBSCRIBE_";
        } else {
            return "NORMAL";
        }
    }
}
