package report;

import core.DTNHost;
import core.SimClock;
import core.UpdateListener;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * KDCLoad Report calculates computing and networking
 * costs for PSGuard and SubscriberGroup approaches based on subscriber count.
 */
public class KDCLoadReport extends Report implements UpdateListener {
    private double lastUpdate = 0; // Time of the last update
    private final double timeThreshold = 360; // Threshold for updates in simulation time

    // Cost tracking for PSGuard
    private double totalComputingCostPSGuard = 0; // Total computing cost (ms)
    private double totalNetworkingCostPSGuard = 0; // Total networking cost (KB)

    private int subscriberCount = 0; // Total unique subscriber count
    private final Set<DTNHost> countedSubscribers = new HashSet<>(); // Set to track unique subscribers

    @Override
    public void updated(List<DTNHost> hosts) {
        if (isWarmup()) {
            return; // Skip updates during warm-up phase
        }

        // Update only if the threshold time has passed
        double currentTime = SimClock.getTime();
        if ((currentTime - lastUpdate) < timeThreshold) {
            return;
        }
        lastUpdate = currentTime;

        // Iterate through the list of hosts
        for (DTNHost host : hosts) {
            if (host != null) {
                // Count unique subscribers
                if (host.isSubscriber() && !countedSubscribers.contains(host)) {
                    countedSubscribers.add(host); // Add this subscriber to the set
                    subscriberCount++; // Increment the total number of subscribers
                }

                // Compute costs for PSGuard using buffer
                if (host.isKDC()) {
                    computePSGuardCostWithBuffer(host);
                }
            }
        }
    }

    /**
     * Computes PSGuard costs using remaining buffer size dynamically.
     */
    private void computePSGuardCostWithBuffer(DTNHost host) {
        // Get the used buffer size (in bytes)
        int usedBuffer = host.getTotalBuffer() - host.getRemainingBuffer(); // Used buffer in bytes

        // Dynamically calculate the computing cost
        int dynamicComputingCost = calculateDynamicComputingCost(usedBuffer);
        totalComputingCostPSGuard += dynamicComputingCost;

        // Dynamically calculate the networking cost using buffer size
        totalNetworkingCostPSGuard += usedBuffer / 1024.0; // Convert used buffer from bytes to KB
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
        return Math.max(calculatedCost, baseCost); // Ensure the cost is at least the base cost
    }

    @Override
    public void done() {
        // At the end of simulation, compute and print stats

        // Computing average costs
        double avgComputingCostPSGuard = subscriberCount > 0
                ? totalComputingCostPSGuard / subscriberCount
                : 0;

        // Print the final report
        write("-------- PSGuard Load Report with Buffer --------");
        write("Number of Subscribers: " + subscriberCount);
        write("PSGuard - Total Computing Cost (ms): " + totalComputingCostPSGuard);
        write("PSGuard - Average Computing Cost per Subscriber (ms): " + avgComputingCostPSGuard);
        write("PSGuard - Total Networking Cost (KB): " + totalNetworkingCostPSGuard);

        super.done();
    }
}

