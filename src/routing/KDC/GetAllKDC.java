package routing.KDC;

import core.DTNHost;
import core.SimScenario;

import java.util.ArrayList;
import java.util.List;

public class GetAllKDC {

    public List<DTNHost> getAllKDCs() {
        List<DTNHost> kdcList = new ArrayList<>();
        for (DTNHost host : SimScenario.getInstance().getKdc()) { // Jika ada metode untuk mendapatkan semua host
            if (host.isKDC()) {
                kdcList.add(host);
            }
        }
        return kdcList;
    }
}