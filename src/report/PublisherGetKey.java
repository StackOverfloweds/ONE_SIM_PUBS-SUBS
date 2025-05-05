package report;

import core.DTNHost;
import core.SimScenario;
import routing.CCDTN;
import routing.PublishAndSubscriberRouting;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PublisherGetKey extends Report{
    private List<String> csvData = new ArrayList<>();

    @Override
    public void done() {
        List<DTNHost> hosts = SimScenario.getInstance().getHosts();
        Map<DTNHost, Integer> aggregatedKeys = new HashMap<>();

        write("Publisher\t TotalKeysPerPublisher\n");
        for (DTNHost host : hosts) {
            if (host.getRouter() instanceof CCDTN) {
                CCDTN router = (CCDTN) host.getRouter();
                if (router instanceof PublishAndSubscriberRouting) {
                    PublishAndSubscriberRouting routing = (PublishAndSubscriberRouting) router;
                    Map<DTNHost, Integer> localKeys = routing.getKeysPublisher();

                    if (localKeys != null) {
                        for (Map.Entry<DTNHost, Integer> entry : localKeys.entrySet()) {
                            DTNHost subscriber = entry.getKey();
                            Integer numberKey = entry.getValue();

                            if (aggregatedKeys.containsKey(subscriber)) {
                                aggregatedKeys.compute(subscriber, (k, currentTotal) -> currentTotal + numberKey);
                            } else {
                                aggregatedKeys.put(subscriber, numberKey);
                            }
                        }
                    }

                }
            }
        }

        List<Map.Entry<DTNHost, Integer>> sortedEntries = new ArrayList<>(aggregatedKeys.entrySet());
        sortedEntries = mergeSort(sortedEntries);

        for (Map.Entry<DTNHost, Integer> entry : sortedEntries) {
            DTNHost subscriber = entry.getKey();
            Integer totalKeys = entry.getValue();

            write(subscriber + "\t" + totalKeys + "\n");
            csvData.add(subscriber + "," + totalKeys);
        }

        exportToCSV();
        super.done();
    }

    private List<Map.Entry<DTNHost, Integer>> mergeSort(List<Map.Entry<DTNHost, Integer>> list) {
        if (list.size() <= 1) return list;

        int mid = list.size() / 2;
        List<Map.Entry<DTNHost, Integer>> left = mergeSort(list.subList(0, mid));
        List<Map.Entry<DTNHost, Integer>> right = mergeSort(list.subList(mid, list.size()));

        return merge(left, right);
    }

    private List<Map.Entry<DTNHost, Integer>> merge(List<Map.Entry<DTNHost, Integer>> left,
                                                    List<Map.Entry<DTNHost, Integer>> right) {
        List<Map.Entry<DTNHost, Integer>> merged = new ArrayList<>();
        int i = 0, j = 0;

        while (i < left.size() && j < right.size()) {
            Map.Entry<DTNHost, Integer> l = left.get(i);
            Map.Entry<DTNHost, Integer> r = right.get(j);

            int cmp = l.getValue().compareTo(r.getValue());
            if (cmp == 0) {
                // Jika jumlah key sama, bandingkan nama subscriber (misalnya S63, S64)
                cmp = l.getKey().toString().compareTo(r.getKey().toString());
            }

            if (cmp <= 0) {
                merged.add(l);
                i++;
            } else {
                merged.add(r);
                j++;
            }
        }

        while (i < left.size()) merged.add(left.get(i++));
        while (j < right.size()) merged.add(right.get(j++));
        return merged;
    }

    private void exportToCSV() {
        String csvFilename = "PublisherGetKey.csv";

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(csvFilename))) {
            writer.write("Publisher,NumberKeysPerPublisher\n");
            for (String line : csvData) {
                writer.write(line);
                writer.newLine();
            }
            System.out.println("CSV file created: " + csvFilename);
        } catch (IOException e) {
            System.err.println("Error writing CSV file: " + e.getMessage());
        }
    }
}
