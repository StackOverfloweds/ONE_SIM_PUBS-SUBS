package routing;

import core.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import routing.community.Duration;

public class ContentRouter extends ActiveRouter {

    // create some initial variable 
    public static final String MESSAGE_TOPIC_S = "topic";

    private  Set<String> subscription;
    private List <Message> PublishMessage;
    private Map<String,List<DTNHost>> topicSubscriber;

    protected  Map<DTNHost, Double> startTimestamps;
    protected Map <DTNHost, List<Duration>> connHistory;

    public ContentRouter (Settings s) {
        super(s);
        this.startTimestamps = new HashMap<DTNHost, Double>();
        this.connHistory = new HashMap<DTNHost, List<Duration>>();
        this.subscription = new HashSet<>();
        this.PublishMessage = new ArrayList<>();
        this.topicSubscriber = new HashMap<>();
    }

    // Copy constructor 
    protected ContentRouter (ContentRouter c) {
        super (c);
        startTimestamps = c.startTimestamps;
        connHistory = c.connHistory;
        subscription = c.subscription;
        PublishMessage = c.PublishMessage;
        topicSubscriber = c.topicSubscriber;
    }

    
    
    @Override
    public MessageRouter replicate() {
        return new ContentRouter(this);
    }
}

