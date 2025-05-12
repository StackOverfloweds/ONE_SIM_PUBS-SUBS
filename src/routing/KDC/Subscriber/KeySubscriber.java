package routing.KDC.Subscriber;

import core.DTNHost;

import java.util.Map;

public interface KeySubscriber {

    // get number of key of subscriber
    Map<DTNHost, Integer> getKeys();

}