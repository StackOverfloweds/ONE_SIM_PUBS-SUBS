package report;

import core.DTNHost;
import core.SimClock;
import core.UpdateListener;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PSGuardLoad computes and records computing and networking cost
 * specifically for the PSGuard approach during simulation.
 */
public class KDCLoad extends Report implements UpdateListener {
    private double lastUpdate = 0; // Last update time
    private final double timeThreshold = 360; // Update threshold in simulation time
    private final Map<DTNHost, Integer> kdcBufferMap = new HashMap<>(); // Placeholder buffer map for KDC hosts

    // Cost tracking
    private int totalComputingCostPSGuard = 0; // Total computing cost for PSGuard (ms)
    private int totalNetworkingCostPSGuard = 0; // Total networking cost for PSGuard (KB)

    private int subscriberCount = 0; // Total number of subscribers

    @Override
    public void updated(List<DTNHost> hosts) {
        if (isWarmup()) {
            return; // Skip updates during warm-up phase
        }

        // Current simulation time
        double currentTime = SimClock.getTime();

        // Update only if threshold time has passed
        if ((currentTime - lastUpdate) < timeThreshold) {
            return;
        }
        lastUpdate = currentTime;

        // Iterate through all hosts to calculate cost for PSGuard
        for (DTNHost host : hosts) {
            if (host != null && host.isKDC()) {
                // Simulate the addition of a new subscriber
                subscriberCount++; // Increment total subscribers

                // Compute costs for PSGuard
                computePSGuardCost();
            }
        }
    }

    /**
     * Compute computing and networking costs for PSGuard approach
     */
    private void computePSGuardCost() {
        // Computing cost: small and constant
        int fixedComputingCost = 5; // Assume fixed computing cost = 5 ms
        totalComputingCostPSGuard += fixedComputingCost; // Aggregate cost

        // Networking cost: small and constant
        int fixedKeySize = 10; // Fixed key size in KB
        totalNetworkingCostPSGuard += fixedKeySize; // Aggregate cost
    }

    @Override
    public void done() {
        // At the end of simulation, compute and print stats

        // Compute average computing cost
        int avgComputingCostPSGuard = subscriberCount > 0
                ? totalComputingCostPSGuard / subscriberCount
                : 0;

        // Compute average networking cost
        int avgNetworkingCostPSGuard = subscriberCount > 0
                ? totalNetworkingCostPSGuard / subscriberCount
                : 0;

        // Print final report
        write("-------- PSGuard Load Report --------");
        write("Subscribers Count: " + subscriberCount);
        write("PSGuard - Total Computing Cost (ms): " + totalComputingCostPSGuard);
        write("PSGuard - Avg Computing Cost per Subscriber (ms): " + avgComputingCostPSGuard);
        write("PSGuard - Total Networking Cost (KB): " + totalNetworkingCostPSGuard);
        write("PSGuard - Avg Networking Cost per Subscriber (KB): " + avgNetworkingCostPSGuard);

        super.done();
    }
}