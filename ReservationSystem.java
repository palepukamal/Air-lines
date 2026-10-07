import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ReservationSystem {

    public enum FareClass {
        ECONOMY,
        BUSINESS,
        FIRST
    }

    // ---------------------------------------------------------
    // BOOKING
    // ---------------------------------------------------------
    static class Booking {

        final int id;
        final String name;
        final FareClass fare;

        // 0 means passenger is currently overbooked
        // and does not have a physical seat.
        private int seatNumber;

        Booking(
                int id,
                String name,
                FareClass fare,
                int seatNumber
        ) {
            this.id = id;
            this.name = name;
            this.fare = fare;
            this.seatNumber = seatNumber;
        }

        public int id() {
            return id;
        }

        public String name() {
            return name;
        }

        public FareClass fare() {
            return fare;
        }

        public int seatNumber() {
            return seatNumber;
        }

        public void setSeatNumber(int seatNumber) {
            this.seatNumber = seatNumber;
        }

        @Override
        public String toString() {
            String seat =
                    seatNumber > 0
                            ? "Seat " + seatNumber
                            : "OVERBOOKED";

            return id + " | "
                    + name + " | "
                    + seat + " | "
                    + fare;
        }
    }

    // ---------------------------------------------------------
    // DYNAMIC SETTINGS
    // ---------------------------------------------------------

    private int capacity;
    private double maxBumpRisk;

    // ---------------------------------------------------------
    // DATA STRUCTURES
    // ---------------------------------------------------------

    private final BTree<Booking> index = new BTree<>();

    private final Map<FareClass, Set<Integer>> partitions =
            new EnumMap<>(FareClass.class);

    /*
     * history[fare] = {booked, noShows}
     */
    private final Map<FareClass, int[]> history =
            new EnumMap<>(FareClass.class);

    /*
     * Estimated no-show probability for each class.
     */
    private final Map<FareClass, Double> noShow =
            new EnumMap<>(FareClass.class);

    /*
     * Currently active bookings.
     */
    private final Map<Integer, Booking> activeBookings =
            new LinkedHashMap<>();

    /*
     * Physical seats currently available.
     */
    private final Set<Integer> availableSeats =
            new HashSet<>();

    private final Random random = new Random();

    private int nextId = 1001;

    private int total = 0;

    // ---------------------------------------------------------
    // CONSTRUCTOR
    // ---------------------------------------------------------

    public ReservationSystem(
            int capacity,
            double maxBumpRisk
    ) {

        if (capacity < 1) {
            throw new IllegalArgumentException(
                    "Capacity must be at least 1"
            );
        }

        if (maxBumpRisk < 0 || maxBumpRisk > 1) {
            throw new IllegalArgumentException(
                    "Maximum bump risk must be between 0 and 1"
            );
        }

        this.capacity = capacity;
        this.maxBumpRisk = maxBumpRisk;

        for (FareClass f : FareClass.values()) {

            partitions.put(
                    f,
                    new LinkedHashSet<>()
            );

            history.put(
                    f,
                    new int[]{0, 0}
            );

            noShow.put(
                    f,
                    0.10
            );
        }

        /*
         * Create physical seats.
         *
         * Example:
         * capacity = 10
         *
         * availableSeats =
         * 1,2,3,4,5,6,7,8,9,10
         */
        for (int seat = 1; seat <= capacity; seat++) {
            availableSeats.add(seat);
        }
    }

    // ---------------------------------------------------------
    // SETTINGS
    // ---------------------------------------------------------

    public synchronized int getCapacity() {
        return capacity;
    }

    public synchronized double getMaxBumpRisk() {
        return maxBumpRisk;
    }

    public synchronized void setCapacity(int newCapacity) {

        if (newCapacity < 1) {
            throw new IllegalArgumentException(
                    "Seat capacity must be at least 1"
            );
        }

        /*
         * Do not allow capacity reduction if an existing
         * passenger is already sitting in a seat that would
         * disappear.
         */
        for (Booking booking : activeBookings.values()) {

            if (booking.seatNumber() > newCapacity) {

                throw new IllegalArgumentException(
                        "Cannot reduce capacity to "
                        + newCapacity
                        + " because seat "
                        + booking.seatNumber()
                        + " is already assigned."
                );
            }
        }

        int oldCapacity = this.capacity;

        this.capacity = newCapacity;

        /*
         * Capacity increased.
         * Add new physical seats.
         */
        if (newCapacity > oldCapacity) {

            for (
                    int seat = oldCapacity + 1;
                    seat <= newCapacity;
                    seat++
            ) {
                availableSeats.add(seat);
            }
        }

        /*
         * Capacity decreased.
         * Remove seats outside the new capacity.
         */
        else if (newCapacity < oldCapacity) {

            availableSeats.removeIf(
                    seat -> seat > newCapacity
            );
        }
    }

    public synchronized void setMaxBumpRisk(
            double newRisk
    ) {

        if (newRisk < 0 || newRisk > 1) {
            throw new IllegalArgumentException(
                    "Maximum bump risk must be between 0 and 1"
            );
        }

        this.maxBumpRisk = newRisk;
    }

    // ---------------------------------------------------------
    // BOOKING
    // ---------------------------------------------------------

    public synchronized Booking book(
            String name,
            FareClass fare
    ) {

        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Passenger name is required"
            );
        }

        if (fare == null) {
            throw new IllegalArgumentException(
                    "Fare class is required"
            );
        }

        /*
         * Dynamic safe limit.
         */
        if (total >= safeLimit()) {
            return null;
        }

        /*
         * seatNumber = 0 means:
         *
         * Booking is accepted because overbooking is allowed,
         * but currently no physical seat is available.
         */
        int seatNumber = 0;

        /*
         * If physical seats are available,
         * randomly assign one.
         */
        if (!availableSeats.isEmpty()) {

            List<Integer> seats =
                    new ArrayList<>(availableSeats);

            seatNumber =
                    seats.get(
                            random.nextInt(seats.size())
                    );

            availableSeats.remove(seatNumber);
        }

        Booking booking =
                new Booking(
                        nextId++,
                        name.trim(),
                        fare,
                        seatNumber
                );

        index.insert(
                booking.id,
                booking
        );

        activeBookings.put(
                booking.id,
                booking
        );

        partitions
                .get(fare)
                .add(booking.id);

        total++;

        return booking;
    }

    // ---------------------------------------------------------
    // FIND BOOKING
    // ---------------------------------------------------------

    public synchronized Booking find(int id) {
        return activeBookings.get(id);
    }

    // ---------------------------------------------------------
    // GET BOOKINGS
    // ---------------------------------------------------------

    public synchronized List<Booking> getBookings() {

        List<Booking> result =
                new ArrayList<>(
                        activeBookings.values()
                );

        result.sort(
                Comparator.comparingInt(
                        Booking::id
                )
        );

        return result;
    }

    // ---------------------------------------------------------
    // CANCEL BOOKING
    // ---------------------------------------------------------

    public synchronized Booking cancel(int id) {

        Booking booking =
                activeBookings.remove(id);

        if (booking == null) {
            return null;
        }

        /*
         * Remove from fare partition.
         */
        partitions
                .get(booking.fare())
                .remove(id);

        total--;

        /*
         * If passenger had a real physical seat,
         * return that seat.
         */
        if (booking.seatNumber() > 0) {

            int freedSeat =
                    booking.seatNumber();

            availableSeats.add(freedSeat);

            /*
             * If somebody is waiting in the overbooked
             * list, give the freed seat to them.
             */
            promoteOverbookedPassenger(
                    freedSeat
            );
        }

        return booking;
    }

    // ---------------------------------------------------------
    // PROMOTE OVERBOOKED PASSENGER
    // ---------------------------------------------------------

    private void promoteOverbookedPassenger(
            int freeSeat
    ) {

        /*
         * Find the first passenger without a seat.
         */
        for (Booking booking :
                activeBookings.values()) {

            if (booking.seatNumber() == 0) {

                booking.setSeatNumber(
                        freeSeat
                );

                availableSeats.remove(
                        freeSeat
                );

                break;
            }
        }
    }

    // ---------------------------------------------------------
    // AVAILABLE SEATS
    // ---------------------------------------------------------

    public synchronized int getAvailableSeatCount() {
        return availableSeats.size();
    }

    public synchronized Set<Integer> getAvailableSeats() {

        return new HashSet<>(
                availableSeats
        );
    }

    // ---------------------------------------------------------
    // TOTAL BOOKINGS
    // ---------------------------------------------------------

    public synchronized int getTotalBookings() {
        return total;
    }

    // ---------------------------------------------------------
    // NO-SHOW HISTORY
    // ---------------------------------------------------------

    public synchronized void exportHistory(
            Path file
    ) throws IOException {

        StringBuilder sb =
                new StringBuilder();

        sb.append(
                "fare_class,booked,no_shows\n"
        );

        for (FareClass f :
                FareClass.values()) {

            int[] h =
                    history.get(f);

            sb.append(
                    f.name()
            )
            .append(",")
            .append(h[0])
            .append(",")
            .append(h[1])
            .append("\n");
        }

        Files.writeString(
                file,
                sb.toString()
        );
    }

    // ---------------------------------------------------------
    // RUN PYTHON ESTIMATOR
    // ---------------------------------------------------------

    public synchronized void runEstimator(
            Path historyFile,
            Path resultFile
    )
            throws IOException,
            InterruptedException {

        Process p =
                new ProcessBuilder(
                        "python",
                        "estimator.py",
                        historyFile.toString(),
                        resultFile.toString()
                )
                .inheritIO()
                .start();

        if (p.waitFor() != 0) {

            throw new IOException(
                    "estimator.py failed"
            );
        }
    }

    // ---------------------------------------------------------
    // LOAD NO-SHOW RATES
    // ---------------------------------------------------------

    public synchronized void loadNoShowRates(
            Path file
    ) throws IOException {

        List<String> lines =
                Files.readAllLines(file);

        for (int i = 1; i < lines.size(); i++) {

            String line =
                    lines.get(i).trim();

            if (line.isEmpty()) {
                continue;
            }

            String[] parts =
                    line.split(",");

            if (parts.length < 2) {
                continue;
            }

            FareClass fare =
                    FareClass.valueOf(
                            parts[0].trim()
                                    .toUpperCase()
                    );

            double probability =
                    Double.parseDouble(
                            parts[1].trim()
                    );

            noShow.put(
                    fare,
                    probability
            );
        }
    }

    // ---------------------------------------------------------
    // NO-SHOW RATES
    // ---------------------------------------------------------

    public synchronized Map<FareClass, Double>
    getNoShowRates() {

        return new EnumMap<>(
                noShow
        );
    }

    // ---------------------------------------------------------
    // BLENDED NO-SHOW
    // ---------------------------------------------------------

    public synchronized double blendedNoShow() {

        if (total == 0) {

            return noShow
                    .values()
                    .stream()
                    .mapToDouble(
                            Double::doubleValue
                    )
                    .average()
                    .orElse(0.10);
        }

        double sum = 0;

        for (FareClass f :
                FareClass.values()) {

            sum +=
                    partitions
                            .get(f)
                            .size()
                    *
                    noShow.get(f);
        }

        return sum / total;
    }

    // ---------------------------------------------------------
    // BINOMIAL BUMP RISK
    // ---------------------------------------------------------

    public static double bumpRisk(
            int n,
            double p,
            int cap
    ) {

        /*
         * If number of bookings is not greater than
         * physical capacity, bump risk is zero.
         */
        if (n <= cap) {
            return 0.0;
        }

        if (p <= 0) {
            return 0.0;
        }

        if (p >= 1) {
            return 1.0;
        }

        double q = 1.0 - p;

        double pmf =
                Math.pow(q, n);

        double tail = 0.0;

        for (int k = 0; k <= n; k++) {

            /*
             * k = number of no-shows.
             *
             * Passenger count remaining:
             * n - k
             *
             * Bump occurs when:
             *
             * n - k > capacity
             *
             * therefore:
             *
             * k < n - capacity
             */
            if ((n - k) > cap) {
                tail += pmf;
            }

            if (k < n) {

                pmf *=
                        ((double) (n - k)
                                / (k + 1))
                        *
                        (p / q);
            }
        }

        return Math.min(
                1.0,
                Math.max(0.0, tail)
        );
    }

    // ---------------------------------------------------------
    // SAFE BOOKING LIMIT
    // ---------------------------------------------------------

    public synchronized int safeLimit() {

        double p =
                blendedNoShow();

        int limit =
                capacity;

        int maximum =
                capacity * 3 / 2;

        for (
                int n = capacity;
                n <= maximum;
                n++
        ) {

            double risk =
                    bumpRisk(
                            n,
                            p,
                            capacity
                    );

            if (risk <= maxBumpRisk) {

                limit = n;

            } else {

                break;
            }
        }

        return limit;
    }

    // ---------------------------------------------------------
    // BUMP RISK AT CURRENT LIMIT
    // ---------------------------------------------------------

    public synchronized double
    bumpRiskAtLimit() {

        return bumpRisk(
                safeLimit(),
                blendedNoShow(),
                capacity
        );
    }
}