package KDC.Subscriber;

import core.DTNHost;
import core.SimScenario;
import routing.util.TupleDe;

import java.util.*;

public class SubscriptionManager {
    private DTNHost host;
    private BrokerHandler brokerHandler;
    private List<Double> interest;
    private List<Boolean> ownInterest;
    private List<TupleDe<Integer, Integer>> numericAttribute;
    private List<Integer> numericAttribute2;

    public SubscriptionManager(DTNHost host) {
        this.host = host;
        this.brokerHandler = new BrokerHandler();
    }

    public void interestCheck(DTNHost host) {
        List<DTNHost> allHosts = SimScenario.getInstance().getHosts();
        if (allHosts == null || allHosts.isEmpty()) {
            System.err.println("Error: No hosts found in the simulation.");
            return;
        }

        List<Double> interest = host.getInterest();
        List<Boolean> ownInterest = host.getOwnInterest();
        List<TupleDe<Integer, Integer>> numericAttribute = host.getNumericAtribute();
        List<Integer> numericAttribute2 = host.getNumericAtribute2();

        if (interest == null || ownInterest == null || numericAttribute == null || numericAttribute2 == null) {
            return;
        }

        int minSize = Math.min(Math.min(interest.size(), ownInterest.size()), Math.min(numericAttribute.size(), numericAttribute2.size()));
        if (minSize == 0) {
            return;
        }

        Map<TupleDe<String, List<Boolean>>, List<TupleDe<Integer, Integer>>> subscriptions = new HashMap<>();

        for (int i = 0; i < minSize; i++) {
            if (ownInterest.get(i)) {
                int minValue, maxValue;
                if (numericAttribute2.get(i) != null) {
                    minValue = numericAttribute2.get(i);
                    maxValue = minValue + new Random().nextInt(5) + 1;
                } else {
                    TupleDe<Integer, Integer> attr = numericAttribute.get(i);
                    if (attr == null) continue;
                    minValue = attr.getFirst();
                    maxValue = attr.getSecond();
                }
                List<TupleDe<Integer, Integer>> topicAttributes = new ArrayList<>();
                topicAttributes.add(new TupleDe<>(minValue, maxValue));
                String subscriberId = String.valueOf(host.getRouter().getHost());
                TupleDe<String, List<Boolean>> topicKey = new TupleDe<>(subscriberId, ownInterest);
                subscriptions.put(topicKey, topicAttributes);
            }
        }

        if (subscriptions.isEmpty()) return;

        DTNHost broker = findBroker(allHosts);
        if (broker != null) {
            brokerHandler.sendSubscriptionToBroker(subscriptions, broker);
        }
    }

    private DTNHost findBroker(List<DTNHost> allHosts) {
        for (DTNHost h : allHosts) {
            if (h.isBroker()) return h;
        }
        return null;
    }
}