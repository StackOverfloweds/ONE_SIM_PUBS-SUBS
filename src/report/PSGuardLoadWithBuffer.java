package report;

import core.DTNHost;
import core.SimClock;
import core.UpdateListener;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * PSGuardLoadWithBuffer calculates computing and networking
 * costs using remaining buffer size for the PSGuard approach.
 */
public class PSGuardLoadWithBuffer extends Report implements UpdateListener {
    private double lastUpdate = 0; // Last update time
    private final double timeThreshold = 360; // Update threshold in simulation time

    // Cost tracking
    private int totalComputingCostPSGuard = 0; // Total computing cost for PSGuard (ms)
    private int totalNetworkingCostPSGuard = 0; // Total networking cost for PSGuard (KB)

    private int subscriberCount = 0; // Total number of unique subscribers
    private final Set<DTNHost> countedSubscribers = new HashSet<>(); // Track unique subscribers

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
            if (host != null) {
                // Simulate the addition of a new subscriber
                if (host.isSubscriber() && !countedSubscribers.contains(host)) {
                    countedSubscribers.add(host); // Mark this host as counted
                    subscriberCount++; // Increment total unique subscribers
                }
                if (host.isKDC()) {
                    // Compute costs for PSGuard using buffer and free space
                    computePSGuardCostWithBuffer(host);
                }
            }
        }
    }

    /**
     * Computes PSGuard costs using remaining buffer size dynamically.
     */
    private void computePSGuardCostWithBuffer(DTNHost host) {
        // Compute the dynamically calculated computing cost
        int usedBuffer = host.getRemainingBuffer(); // Used buffer size in bytes
        int dynamicComputingCost = calculateDynamicComputingCost(usedBuffer); // Determine cost based on buffer usage
        totalComputingCostPSGuard += dynamicComputingCost;

        // Networking cost: based on buffer usage
        totalNetworkingCostPSGuard += usedBuffer / 1024; // Convert buffer size from bytes to KB
    }

    /**
     * Calculates dynamic computing cost based on buffer usage.
     *
     * @param usedBuffer The size of the currently used buffer (in bytes).
     * @return The dynamic computing cost (in ms).
     */
    private int calculateDynamicComputingCost(int usedBuffer) {
        // Example: Computing cost increases logarithmically with buffer usage
        int baseCost = 1; // Base cost for computation in ms
        int calculatedCost = (int) (baseCost + Math.log(1 + usedBuffer / 1024.0) * 10);
        return Math.max(calculatedCost, baseCost); // Ensure cost is at least base cost
    }

    @Override
    public void done() {
        // At the end of simulation, compute and print stats

        // Computing average costs
        int avgComputingCostPSGuard = subscriberCount > 0
                ? totalComputingCostPSGuard / subscriberCount
                : 0;

        // Print the final report
        write("-------- PSGuard Load Report with Remaining Buffer --------");
        write("Subscribers Count: " + subscriberCount);
        write("PSGuard - Total Computing Cost (ms): " + totalComputingCostPSGuard);
        write("PSGuard - Avg Computing Cost per Subscriber (ms): " + avgComputingCostPSGuard);

        super.done();
    }
}