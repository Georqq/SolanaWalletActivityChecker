import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class Schedule {
    String walletAddress;
    List<TimeInterval> timeIntervals;

    public Schedule(String walletAddress) {
        this.walletAddress = walletAddress;
        timeIntervals = new ArrayList<>();
    }

    public boolean isWalletActiveAtThisTime(long timeMillis) {
        int timeInSeconds = (int) (timeMillis % 86_400_000) / 1000;
        int numberOfParts = timeIntervals.size();
        int index = (int) ((long) timeInSeconds * (long) numberOfParts / 86_400L);
        TimeInterval timeInterval = timeIntervals.get(index);
        int numberOfOperations = timeInterval.getNumber();
        return numberOfOperations > 0;
    }

    public void createTimeIntervals(int number) {
        LocalTime midnight = LocalTime.MIDNIGHT;
        long interval = 86_400 / number;
        LocalTime startTime;
        LocalTime endTime = midnight.minusSeconds(1);
        for (int i = 0; i < number; i++) {
            startTime = endTime.plusSeconds(1);
            endTime = startTime.plusSeconds(interval - 1);
            String name = startTime.format(DateTimeFormatter.ISO_LOCAL_TIME) + " - " + endTime.format(DateTimeFormatter.ISO_LOCAL_TIME);
            timeIntervals.add(new TimeInterval(name, startTime, endTime));
        }
    }

    public void fillWalletsTimeIntervals() {
        try (BufferedReader br = new BufferedReader(
                new FileReader(
                        "E:\\Projects\\SolanaWalletActivityChecker\\heroku\\data\\schedule\\" + walletAddress + ".txt"))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] values = line.split("\t");
                addToTimeInterval(Integer.parseInt(values[1]));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addToTimeInterval(int timeInSeconds) {
        timeInSeconds = timeInSeconds % 86_400;
        int numberOfParts = timeIntervals.size();
        int index = timeInSeconds * numberOfParts / 86_400;
        timeIntervals.get(index).add();
    }

    public void printAllTimeIntervals() {
        for (TimeInterval ti : timeIntervals) {
            System.out.println(ti);
        }
    }

    public class TimeInterval {
        private final String name;
        private final LocalTime startTime;
        private final LocalTime  endTime;
        private int number;

        public TimeInterval(String name, LocalTime  startTime, LocalTime  endTime) {
            this.name = name;
            this.startTime = startTime;
            this.endTime = endTime;
            this.number = 0;
        }

        public int getNumber() {
            return number;
        }

        public void add() {
            number++;
        }

        public void remove() {
            number--;
        }

        @Override
        public String toString() {
            return name + ": " + number;
        }
    }
}
