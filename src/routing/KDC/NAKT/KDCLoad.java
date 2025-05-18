package routing.KDC.NAKT;

import core.DTNHost;
import core.Message;

import java.util.List;
import java.util.Map;

public interface KDCLoad {

    Map<DTNHost, Integer> getKDCLoad ();

    Map<DTNHost, Message> loadMsKDC();

}