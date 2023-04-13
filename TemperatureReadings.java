import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class TemperatureReadings {
    // Defines the number of sensors that will be recording the temperature concurrently
    private static final int NUM_SENSORS = 8;

    // Boolean flag that decides whether or not to record 60 temperatures for an hour or minute period of time
    // (Including this option since we can simulate an hour's worth of recording in a minute w/o wasting all that time)
    public static final boolean RECORD_HOUR = false;

    public static void main(String[] args) {
        List<ConcurrentLinkedDeque<Integer>> tempReadings; // Shared memory space that all sensors will record temperatures in
        SensorThread[] sensors;

        // Create a list of 60 concurrent deques with each ith deque storing the temperatures
        // recorded by all sensors at minute/second 'i' during execution
        tempReadings = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            tempReadings.add(new ConcurrentLinkedDeque<>());
        }

        sensors = new SensorThread[NUM_SENSORS];

        // Initialize all sensor threads
        for (int i = 0; i < NUM_SENSORS; i++) {
            sensors[i] = new SensorThread(tempReadings);
        }

        // Start each sensor thread
        for (int i = 0; i < NUM_SENSORS; i++) {
            sensors[i].start();
        }

        // Join all sensors threads, so that main thread waits until all sensors finish recording all the required temperatures
        try {
            for (int i = 0; i < NUM_SENSORS; i++) {
                sensors[i].join();
            }
        }
        catch (InterruptedException e) {
            System.out.println("Error joining thread: " + e.toString());
            return;
        }

        PriorityQueue<Integer> minTemps, maxTemps;

        // Create two minHeaps that will keep track of the highest and lowest temperatures recorded
        minTemps = new PriorityQueue<>();
        maxTemps = new PriorityQueue<>(Comparator.reverseOrder());

        // Go through all temperatures recorded by sensors and add each to the min/max-Heaps
        for (int i = 0; i < tempReadings.size(); i++) {
            for (Integer tempReading : tempReadings.get(i)) {
                minTemps.add(tempReading);
                maxTemps.add(tempReading);
            }
        }

        int startTempAns = -1, endTempAns = -1, largestTempDiff = -1;
        largestTempDiff = 0;

        // Iterate through all possible 10 min/sec intervals that temperatures were recorded
        for (int i = 10; i <= tempReadings.size(); i++) {
            int maxStartTemp = Integer.MIN_VALUE;
            int minStartTemp = Integer.MAX_VALUE;

            // Find the min and max temperature recorded by all sensors at the start of this interval
            for (Integer startTemp : tempReadings.get(i - 10)) {
                maxStartTemp = Math.max(maxStartTemp, startTemp);
                minStartTemp = Math.min(minStartTemp, startTemp);
            }
            
            int maxEndTemp = Integer.MIN_VALUE;
            int minEndTemp = Integer.MAX_VALUE;

            // Find the min and max temperature recorded by all sensors at the end of the interval
            for (Integer endTemp : tempReadings.get(i - 1)) {
                maxEndTemp = Math.max(maxEndTemp, endTemp);
                minEndTemp = Math.min(minEndTemp, endTemp);
            }

            // If the largest temperature increase across this interval is the greatest interval change found so far,
            // then record this difference
            if (Math.abs(maxEndTemp - minStartTemp) > Math.abs(largestTempDiff)) {
                largestTempDiff = maxEndTemp - minStartTemp;
                startTempAns = minStartTemp;
                endTempAns = maxEndTemp;
            }

            // If the largest temperature decrease across this interval is the greatest interval change found so far,
            // then record this difference
            if (Math.abs(minEndTemp - maxStartTemp) > Math.abs(largestTempDiff)) {
                largestTempDiff = minEndTemp - maxStartTemp;
                startTempAns = maxStartTemp;
                endTempAns = minEndTemp;
            }
        }

        System.out.print("Top 5 highest temperatures recorded this hour: ");
        // Get the top 5 highest temperatures stored by the maxHeap
        for (int i = 0; i < 5; i++) {
            System.out.print(maxTemps.poll() + ((i == 4) ? "\n" : ", "));
        }

        System.out.print("Top 5 lowest temperatures recorded this hour: ");
        // Get the top 5 lowest temperatures stored by the minHeap
        for (int i = 0; i < 5; i++) {
            System.out.print(minTemps.poll() + ((i == 4) ? "\n" : ", "));
        }

        System.out.println("The largest temperature difference observed over a 10 minute interval was " + largestTempDiff + ", starting at " + startTempAns + " and ending at " + endTempAns);
    }
}

class SensorThread extends Thread {
    // Defines the possible range of temperatures that the sensor can record
    private static final int MAX_TEMP = 70;
    private static final int MIN_TEMP = -100;

    // Shared memory space that the sensor will add its recorded temperatures to
    private List<ConcurrentLinkedDeque<Integer>> tempReadings;

    public SensorThread(List<ConcurrentLinkedDeque<Integer>> tempReadings) {
        this.tempReadings = tempReadings;
    }

    @Override
    public void run() {
        int currReading = 0;

        // Iterate through each min/sec of the hour/min
        while (currReading < 60) {
            // Record sensor's current temperature at this minute/second
            int currTemp = recordTemp();

            // Add this sensor's recorded temperature to the respective concurrent deque
            // for this min/sec in the shared memory space
            this.tempReadings.get(currReading++).add(currTemp);

            // Wait for a minute/second until sensor is ready to take another reading
            try {
                Thread.sleep((TemperatureReadings.RECORD_HOUR) ? TimeUnit.MINUTES.toMillis(1) : TimeUnit.SECONDS.toMillis(1));
            }
            catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    // Generates a unique random temperature for the inclusive range of temperatures specified
    private int recordTemp() {
        return ThreadLocalRandom.current().nextInt(MAX_TEMP - MIN_TEMP + 1) + MIN_TEMP;
    }
}