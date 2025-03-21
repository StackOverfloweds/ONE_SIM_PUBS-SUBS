package routing.KDC.NAKT;

import core.DTNHost;

import java.util.Map;

public interface KDCLoad {

    Map<DTNHost, Integer> getKDCLoad ();
}