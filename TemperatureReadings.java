import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ThreadLocalRandom;

public class TemperatureReadings {
    private static final int NUM_SENSORS = 8;

    public static void main(String[] args) {
        List<ConcurrentLinkedDeque<Integer>> tempReadings;
        SensorThread[] sensors;

        tempReadings = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            tempReadings.add(new ConcurrentLinkedDeque<>());
        }

        sensors = new SensorThread[60];

        for (int i = 0; i < NUM_SENSORS; i++) {
            sensors[i] = new SensorThread(tempReadings);
        }

        for (int i = 0; i < NUM_SENSORS; i++) {
            sensors[i].start();
        }

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

        minTemps = new PriorityQueue<>();
        maxTemps = new PriorityQueue<>(Comparator.reverseOrder());

        for (int i = 0; i < tempReadings.size(); i++) {
            for (Integer tempReading : tempReadings.get(i)) {
                minTemps.add(tempReading);
                maxTemps.add(tempReading);
            }
        }

        int startTempAns = -1, endTempAns = -1, largestTempDiff = -1;
        largestTempDiff = 0;

        for (int i = 10; i <= tempReadings.size(); i++) {
            int maxStartTemp = Integer.MIN_VALUE;
            int minStartTemp = Integer.MAX_VALUE;

            for (Integer startTemp : tempReadings.get(i - 10)) {
                maxStartTemp = Math.max(maxStartTemp, startTemp);
                minStartTemp = Math.min(minStartTemp, startTemp);
            }
            
            int maxEndTemp = Integer.MIN_VALUE;
            int minEndTemp = Integer.MAX_VALUE;

            for (Integer endTemp : tempReadings.get(i - 1)) {
                maxEndTemp = Math.max(maxEndTemp, endTemp);
                minEndTemp = Math.min(minEndTemp, endTemp);
            }

            if (Math.abs(maxEndTemp - minStartTemp) > Math.abs(largestTempDiff)) {
                largestTempDiff = maxEndTemp - minStartTemp;
                startTempAns = minStartTemp;
                endTempAns = maxEndTemp;
            }
            if (Math.abs(minEndTemp - maxStartTemp) > Math.abs(largestTempDiff)) {
                largestTempDiff = minEndTemp - maxStartTemp;
                startTempAns = maxStartTemp;
                endTempAns = minEndTemp;
            }
        }

        System.out.print("Top 5 highest temperatures recorded this hour: ");
        for (int i = 0; i < 5; i++) {
            System.out.print(maxTemps.poll() + ((i == 4) ? "\n" : ", "));
        }

        System.out.print("Top 5 lowest temperatures recorded this hour: ");
        for (int i = 0; i < 5; i++) {
            System.out.print(minTemps.poll() + ((i == 4) ? "\n" : ", "));
        }

        System.out.println("The largest temperature difference observed over a 10 minute interval was " + largestTempDiff + ", starting at " + startTempAns + " and ending at " + endTempAns);
    }
}

class SensorThread extends Thread {
    private static final int MAX_TEMP = 70;
    private static final int MIN_TEMP = -100;

    private List<ConcurrentLinkedDeque<Integer>> tempReadings;

    public SensorThread(List<ConcurrentLinkedDeque<Integer>> tempReadings) {
        this.tempReadings = tempReadings;
    }
    @Override
    public void run() {
        int currReading = 0;

        while (currReading < 60) {
            int currTemp = recordTemp();
            this.tempReadings.get(currReading++).add(currTemp);

            try {
                Thread.sleep(1000);
            }
            catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private int recordTemp() {
        return ThreadLocalRandom.current().nextInt(MAX_TEMP - MIN_TEMP + 1) + MIN_TEMP;
    }
}