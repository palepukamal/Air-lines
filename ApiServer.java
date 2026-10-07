import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ApiServer {

    private static final ReservationSystem system =
            new ReservationSystem(
                    100,
                    0.05
            );

    public static void main(String[] args)
            throws Exception {

        /*
         * Try to load estimated no-show probabilities.
         */
        try {

            Path history =
                    Path.of("history.csv");

            Path result =
                    Path.of("noshow.csv");

            if (java.nio.file.Files.exists(history)) {

                system.runEstimator(
                        history,
                        result
                );

                system.loadNoShowRates(
                        result
                );
            }

        } catch (Exception e) {

            System.out.println(
                    "Estimator warning: "
                    + e.getMessage()
            );
        }

        HttpServer server =
                HttpServer.create(
                        new InetSocketAddress(
                                8090
                        ),
                        0
                );

        /*
         * API endpoints
         */
        server.createContext(
                "/api/state",
                ApiServer::state
        );

        server.createContext(
                "/api/settings",
                ApiServer::settings
        );

        server.createContext(
                "/api/book",
                ApiServer::book
        );

        server.createContext(
                "/api/cancel",
                ApiServer::cancel
        );

        server.createContext(
                "/api/find",
                ApiServer::find
        );

        server.createContext(
                "/api/estimate",
                ApiServer::estimate
        );

        /*
         * Frontend
         */
        server.createContext(
                "/",
                ApiServer::home
        );

        server.setExecutor(null);

        server.start();

        System.out.println(
                "Airline Overbooking Server running at:"
        );

        System.out.println(
                "http://localhost:8090"
        );
    }

    // =========================================================
    // HOME
    // =========================================================

    private static void home(
            HttpExchange exchange
    ) throws IOException {

        if (!exchange
                .getRequestMethod()
                .equalsIgnoreCase("GET")) {

            send(
                    exchange,
                    405,
                    "application/json",
                    "{\"error\":\"GET required\"}"
            );

            return;
        }

        try {

            Path file =
                    Path.of("index.html");

            if (!java.nio.file.Files.exists(file)) {

                send(
                        exchange,
                        404,
                        "text/plain",
                        "index.html not found"
                );

                return;
            }

            byte[] data =
                    java.nio.file.Files.readAllBytes(
                            file
                    );

            exchange.getResponseHeaders()
                    .set(
                            "Content-Type",
                            "text/html; charset=UTF-8"
                    );

            exchange.sendResponseHeaders(
                    200,
                    data.length
            );

            try (OutputStream out =
                         exchange.getResponseBody()) {

                out.write(data);
            }

        } catch (Exception e) {

            send(
                    exchange,
                    500,
                    "text/plain",
                    e.getMessage()
            );
        }
    }

    // =========================================================
    // STATE
    // =========================================================

    private static void state(
            HttpExchange exchange
    ) throws IOException {

        if (!exchange
                .getRequestMethod()
                .equalsIgnoreCase("GET")) {

            send(
                    exchange,
                    405,
                    "application/json",
                    "{\"error\":\"GET required\"}"
            );

            return;
        }

        send(
                exchange,
                200,
                "application/json",
                buildStateJson()
        );
    }

    // =========================================================
    // SETTINGS
    // =========================================================

    private static void settings(
            HttpExchange exchange
    ) throws IOException {

        if (!exchange
                .getRequestMethod()
                .equalsIgnoreCase("POST")) {

            send(
                    exchange,
                    405,
                    "application/json",
                    "{\"error\":\"POST required\"}"
            );

            return;
        }

        try {

            String body =
                    new String(
                            exchange
                                    .getRequestBody()
                                    .readAllBytes(),
                            StandardCharsets.UTF_8
                    );

            String capacityText =
                    jsonNumber(
                            body,
                            "capacity"
                    );

            String riskText =
                    jsonNumber(
                            body,
                            "maxRisk"
                    );

            if (capacityText != null) {

                int capacity =
                        Integer.parseInt(
                                capacityText
                        );

                system.setCapacity(
                        capacity
                );
            }

            if (riskText != null) {

                double risk =
                        Double.parseDouble(
                                riskText
                        );

                system.setMaxBumpRisk(
                        risk
                );
            }

            send(
                    exchange,
                    200,
                    "application/json",
                    buildStateJson()
            );

        } catch (Exception e) {

            send(
                    exchange,
                    400,
                    "application/json",
                    "{\"error\":"
                            + quote(
                            e.getMessage()
                    )
                            + "}"
            );
        }
    }

    // =========================================================
    // BOOK
    // =========================================================

    private static void book(
            HttpExchange exchange
    ) throws IOException {

        if (!exchange
                .getRequestMethod()
                .equalsIgnoreCase("POST")) {

            send(
                    exchange,
                    405,
                    "application/json",
                    "{\"error\":\"POST required\"}"
            );

            return;
        }

        try {

            String body =
                    new String(
                            exchange
                                    .getRequestBody()
                                    .readAllBytes(),
                            StandardCharsets.UTF_8
                    );

            String name =
                    jsonString(
                            body,
                            "name"
                    );

            String fare =
                    jsonString(
                            body,
                            "fare"
                    );

            if (name == null ||
                    name.trim().isEmpty()) {

                send(
                        exchange,
                        400,
                        "application/json",
                        "{\"error\":\"Passenger name is required\"}"
                );

                return;
            }

            if (fare == null) {

                send(
                        exchange,
                        400,
                        "application/json",
                        "{\"error\":\"Fare class is required\"}"
                );

                return;
            }

            ReservationSystem.FareClass fareClass =
                    ReservationSystem.FareClass.valueOf(
                            fare.toUpperCase()
                    );

            ReservationSystem.Booking booking =
                    system.book(
                            name,
                            fareClass
                    );

            /*
             * Safe booking limit reached.
             */
            if (booking == null) {

                send(
                        exchange,
                        409,
                        "application/json",
                        "{\"error\":\"Safe booking limit reached\","
                                + "\"state\":"
                                + buildStateJson()
                                + "}"
                );

                return;
            }

            send(
                    exchange,
                    200,
                    "application/json",
                    "{\"message\":\"Booking successful\","
                            + "\"booking\":"
                            + bookingJson(booking)
                            + ",\"state\":"
                            + buildStateJson()
                            + "}"
            );

        } catch (Exception e) {

            send(
                    exchange,
                    400,
                    "application/json",
                    "{\"error\":"
                            + quote(
                            e.getMessage()
                    )
                            + "}"
            );
        }
    }

    // =========================================================
    // CANCEL
    // =========================================================

    private static void cancel(
            HttpExchange exchange
    ) throws IOException {

        if (!exchange
                .getRequestMethod()
                .equalsIgnoreCase("POST")) {

            send(
                    exchange,
                    405,
                    "application/json",
                    "{\"error\":\"POST required\"}"
            );

            return;
        }

        try {

            String body =
                    new String(
                            exchange
                                    .getRequestBody()
                                    .readAllBytes(),
                            StandardCharsets.UTF_8
                    );

            String idText =
                    jsonNumber(
                            body,
                            "id"
                    );

            if (idText == null) {

                send(
                        exchange,
                        400,
                        "application/json",
                        "{\"error\":\"Booking ID is required\"}"
                );

                return;
            }

            int id =
                    Integer.parseInt(
                            idText
                    );

            ReservationSystem.Booking cancelled =
                    system.cancel(id);

            if (cancelled == null) {

                send(
                        exchange,
                        404,
                        "application/json",
                        "{\"error\":\"Booking not found\"}"
                );

                return;
            }

            send(
                    exchange,
                    200,
                    "application/json",
                    "{\"message\":\"Booking cancelled\","
                            + "\"booking\":"
                            + bookingJson(cancelled)
                            + ",\"state\":"
                            + buildStateJson()
                            + "}"
            );

        } catch (Exception e) {

            send(
                    exchange,
                    400,
                    "application/json",
                    "{\"error\":"
                            + quote(
                            e.getMessage()
                    )
                            + "}"
            );
        }
    }

    // =========================================================
    // FIND
    // =========================================================

    private static void find(
            HttpExchange exchange
    ) throws IOException {

        if (!exchange
                .getRequestMethod()
                .equalsIgnoreCase("GET")) {

            send(
                    exchange,
                    405,
                    "application/json",
                    "{\"error\":\"GET required\"}"
            );

            return;
        }

        try {

            String query =
                    exchange
                            .getRequestURI()
                            .getQuery();

            String idText =
                    queryParameter(
                            query,
                            "id"
                    );

            if (idText == null) {

                send(
                        exchange,
                        400,
                        "application/json",
                        "{\"error\":\"ID required\"}"
                );

                return;
            }

            int id =
                    Integer.parseInt(
                            idText
                    );

            ReservationSystem.Booking booking =
                    system.find(id);

            if (booking == null) {

                send(
                        exchange,
                        404,
                        "application/json",
                        "{\"error\":\"Booking not found\"}"
                );

                return;
            }

            send(
                    exchange,
                    200,
                    "application/json",
                    bookingJson(booking)
            );

        } catch (Exception e) {

            send(
                    exchange,
                    400,
                    "application/json",
                    "{\"error\":"
                            + quote(
                            e.getMessage()
                    )
                            + "}"
            );
        }
    }

    // =========================================================
    // ESTIMATE
    // =========================================================

    private static void estimate(
            HttpExchange exchange
    ) throws IOException {

        if (!exchange
                .getRequestMethod()
                .equalsIgnoreCase("POST")) {

            send(
                    exchange,
                    405,
                    "application/json",
                    "{\"error\":\"POST required\"}"
            );

            return;
        }

        try {

            Path history =
                    Path.of("history.csv");

            Path result =
                    Path.of("noshow.csv");

            system.exportHistory(
                    history
            );

            system.runEstimator(
                    history,
                    result
            );

            system.loadNoShowRates(
                    result
            );

            send(
                    exchange,
                    200,
                    "application/json",
                    buildStateJson()
            );

        } catch (Exception e) {

            send(
                    exchange,
                    500,
                    "application/json",
                    "{\"error\":"
                            + quote(
                            e.getMessage()
                    )
                            + "}"
            );
        }
    }

    // =========================================================
    // BUILD STATE JSON
    // =========================================================

    private static String buildStateJson() {

        int capacity =
                system.getCapacity();

        double maxRisk =
                system.getMaxBumpRisk();

        int total =
                system.getTotalBookings();

        int safeLimit =
                system.safeLimit();

        double blended =
                system.blendedNoShow();

        double bumpRisk =
                system.bumpRiskAtLimit();

        int availableSeats =
                system.getAvailableSeatCount();

        int sellable =
                Math.max(
                        0,
                        safeLimit - total
                );

        StringBuilder json =
                new StringBuilder();

        json.append("{");

        json.append(
                "\"capacity\":"
        ).append(capacity);

        json.append(
                ",\"maxRisk\":"
        ).append(
                number(maxRisk)
        );

        json.append(
                ",\"total\":"
        ).append(total);

        json.append(
                ",\"safeLimit\":"
        ).append(safeLimit);

        json.append(
                ",\"sellable\":"
        ).append(sellable);

        json.append(
                ",\"availableSeats\":"
        ).append(availableSeats);

        json.append(
                ",\"blendedNoShow\":"
        ).append(
                number(blended)
        );

        json.append(
                ",\"bumpRiskAtLimit\":"
        ).append(
                number(bumpRisk)
        );

        // -----------------------------------------------------
        // BOOKINGS
        // -----------------------------------------------------

        json.append(
                ",\"bookings\":["
        );

        List<ReservationSystem.Booking> bookings =
                system.getBookings();

        for (int i = 0;
             i < bookings.size();
             i++) {

            if (i > 0) {
                json.append(",");
            }

            json.append(
                    bookingJson(
                            bookings.get(i)
                    )
            );
        }

        json.append("]");

        // -----------------------------------------------------
        // NO-SHOW HISTORY
        // -----------------------------------------------------

        json.append(
                ",\"history\":{"
        );

        Map<ReservationSystem.FareClass, Double>
                rates =
                system.getNoShowRates();

        int index = 0;

        for (
                ReservationSystem.FareClass fare :
                ReservationSystem.FareClass.values()
        ) {

            if (index++ > 0) {
                json.append(",");
            }

            json.append(
                    quote(fare.name())
            )
            .append(":")
            .append(
                    number(
                            rates.get(fare)
                    )
            );
        }

        json.append("}");

        // -----------------------------------------------------
        // PARTITIONS
        // -----------------------------------------------------

        json.append(
                ",\"partitions\":{"
        );

        for (
                int i = 0;
                i < ReservationSystem.FareClass
                        .values()
                        .length;
                i++
        ) {

            ReservationSystem.FareClass fare =
                    ReservationSystem.FareClass
                            .values()[i];

            if (i > 0) {
                json.append(",");
            }

            int count = 0;

            for (
                    ReservationSystem.Booking booking :
                    bookings
            ) {

                if (booking.fare() == fare) {
                    count++;
                }
            }

            json.append(
                    quote(fare.name())
            )
            .append(":")
            .append(count);
        }

        json.append("}");

        json.append("}");

        return json.toString();
    }

    // =========================================================
    // BOOKING JSON
    // =========================================================

    private static String bookingJson(
            ReservationSystem.Booking booking
    ) {

        String seat =
                booking.seatNumber() > 0
                        ? String.valueOf(
                        booking.seatNumber()
                )
                        : "null";

        return "{"
                + "\"id\":"
                + booking.id()
                + ",\"name\":"
                + quote(booking.name())
                + ",\"seat\":"
                + seat
                + ",\"fare\":"
                + quote(
                booking.fare().name()
        )
                + "}";
    }

    // =========================================================
    // JSON HELPERS
    // =========================================================

    private static String quote(
            String value
    ) {

        if (value == null) {
            return "null";
        }

        return "\""
                + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                + "\"";
    }

    private static String number(
            double value
    ) {

        return String.format(
                java.util.Locale.US,
                "%.6f",
                value
        );
    }

    private static String jsonString(
            String json,
            String key
    ) {

        String pattern =
                "\"" + key + "\"\\s*:\\s*\"([^\"]*)\"";

        java.util.regex.Matcher matcher =
                java.util.regex.Pattern
                        .compile(pattern)
                        .matcher(json);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    private static String jsonNumber(
            String json,
            String key
    ) {

        String pattern =
                "\"" + key
                        + "\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?)";

        java.util.regex.Matcher matcher =
                java.util.regex.Pattern
                        .compile(pattern)
                        .matcher(json);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    private static String queryParameter(
            String query,
            String key
    ) {

        if (query == null) {
            return null;
        }

        for (String part :
                query.split("&")) {

            String[] pieces =
                    part.split("=", 2);

            if (pieces.length == 2 &&
                    pieces[0].equals(key)) {

                return pieces[1];
            }
        }

        return null;
    }

    // =========================================================
    // SEND RESPONSE
    // =========================================================

    private static void send(
            HttpExchange exchange,
            int status,
            String contentType,
            String body
    ) throws IOException {

        byte[] data =
                body.getBytes(
                        StandardCharsets.UTF_8
                );

        exchange.getResponseHeaders()
                .set(
                        "Content-Type",
                        contentType
                                + "; charset=UTF-8"
                );

        exchange.getResponseHeaders()
                .set(
                        "Access-Control-Allow-Origin",
                        "*"
                );

        exchange.sendResponseHeaders(
                status,
                data.length
        );

        try (OutputStream out =
                     exchange.getResponseBody()) {

            out.write(data);
        }
    }
}