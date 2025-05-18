package routing.KDC.Publisher;

import core.DTNHost;

import java.util.Map;

public interface KeyPublisher {

    Map<DTNHost, Integer> getKeysPublisher();
}