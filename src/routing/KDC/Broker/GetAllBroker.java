package routing.KDC.Broker;

import core.DTNHost;
import core.SimScenario;

import java.util.ArrayList;
import java.util.List;

public class GetAllBroker {

    public List<DTNHost> getAllBrokers() {
        List<DTNHost> brokerList = new ArrayList<>();
        for (DTNHost host : SimScenario.getInstance().getBroker()) { // Jika ada metode untuk mendapatkan semua host
            if (host.isBroker()) {
                brokerList.add(host);
            }
        }
        return brokerList;
    }
}