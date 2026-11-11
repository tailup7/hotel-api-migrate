package com.khoubyari.example.api;

import com.khoubyari.example.exception.DataFormatException;
import com.khoubyari.example.exception.ResourceNotFoundException;
import org.junit.jupiter.api.*;
import java.sql.DriverManager;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class HotelApplicationIT {
    private HotelApplication app;
    private Map<String, String> config;
    private final List<Long> createdIds = new CopyOnWriteArrayList<>();

    @BeforeEach void open() {
        config = Map.of("DB_URL", required("TEST_DB_URL"),
                "DB_USERNAME", required("TEST_DB_USERNAME"),
                "DB_PASSWORD", required("TEST_DB_PASSWORD"), "DB_SCHEMA_MODE", "validate",
                "DB_POOL_SIZE", "4", "HOTEL_SERVICE_NAME", "integration test");
        app = new HotelApplication(config);
    }

    private static String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalStateException(key + " is required for -Ppostgres-it");
        return value;
    }

    private Map<String, Object> create(String name) {
        var result = app.createHotel(new HashMap<>(Map.of("name", name, "description", "description", "city", "Tokyo", "rating", 4)));
        createdIds.add((Long) result.get("id"));
        return result;
    }

    @AfterEach void close() throws Exception {
        if (app != null) app.close();
        if (config != null) {
            try (var connection = DriverManager.getConnection(config.get("DB_URL"), config.get("DB_USERNAME"), config.get("DB_PASSWORD"));
                 var statement = connection.prepareStatement("DELETE FROM hotel WHERE id = ?")) {
                for (long id : createdIds) { statement.setLong(1, id); statement.executeUpdate(); }
            }
        }
    }

    @Test void crudCommitsToPostgreSQLAndSurvivesReopening() throws Exception {
        Map<String, Object> hotel = create("Persistent Hotel");
        long id = (Long) hotel.get("id");
        try (var connection = DriverManager.getConnection(config.get("DB_URL"), config.get("DB_USERNAME"), config.get("DB_PASSWORD"));
             var statement = connection.prepareStatement("SELECT name FROM hotel WHERE id = ?")) {
            assertEquals("PostgreSQL", connection.getMetaData().getDatabaseProductName());
            statement.setLong(1, id);
            try (var rows = statement.executeQuery()) {
                assertTrue(rows.next());
                assertEquals("Persistent Hotel", rows.getString(1));
            }
        }
        hotel.put("rating", 5);
        app.updateHotel(id, hotel);
        app.close();
        app.close();
        app = new HotelApplication(config);
        assertEquals(5, app.getHotel(id).get("rating"));
        var next = create("Sequence continues");
        assertTrue((Long) next.get("id") > id);
        app.deleteHotel(id);
        assertThrows(ResourceNotFoundException.class, () -> app.getHotel(id));
    }

    @Test void validationDoesNotOverwriteExistingRows() {
        var hotel = create("Original");
        long id = (Long) hotel.get("id");
        hotel.put("name", "Overwrite attempt");
        assertThrows(DataFormatException.class, () -> app.createHotel(hotel));
        hotel.put("id", id + 1);
        assertThrows(DataFormatException.class, () -> app.updateHotel(id, hotel));
        assertEquals("Original", app.getHotel(id).get("name"));
        assertThrows(ResourceNotFoundException.class, () -> app.getHotel(-1));
        assertThrows(ResourceNotFoundException.class, () -> app.deleteHotel(-1));
        assertThrows(DataFormatException.class, () -> app.getAllHotels(-1, 10));
        assertThrows(DataFormatException.class, () -> app.getAllHotels(0, 0));
    }

    @Test void supportsPaginationMetricsAndHealth() {
        create("Page Hotel");
        var page = app.getAllHotels(0, 1);
        assertEquals(1, ((List<?>) page.get("content")).size());
        assertEquals(0, page.get("number"));
        assertEquals(1, page.get("size"));
        app.getAllHotels(null, null);
        assertEquals(1L, app.metrics().get("Khoubyari.HotelService.getAll.largePayload"));
        assertEquals("UP", app.health().get("status"));
        assertEquals("PostgreSQL", app.health().get("database"));
    }

    @Test void concurrentCallsSharePoolAndRestoreContextClassLoader() throws Exception {
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        ClassLoader foreign = new ClassLoader(null) { };
        Thread.currentThread().setContextClassLoader(foreign);
        try {
            create("Foreign TCCL");
            assertSame(foreign, Thread.currentThread().getContextClassLoader());
            assertThrows(ResourceNotFoundException.class, () -> app.getHotel(-1));
            assertSame(foreign, Thread.currentThread().getContextClassLoader());
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
        ExecutorService executor = Executors.newFixedThreadPool(6);
        try {
            List<Callable<Long>> tasks = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                int index = i;
                tasks.add(() -> (Long) create("Concurrent " + index).get("id"));
            }
            Set<Long> ids = new HashSet<>();
            for (Future<Long> result : executor.invokeAll(tasks)) ids.add(result.get(30, TimeUnit.SECONDS));
            assertEquals(12, ids.size());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test void closeIsIdempotentAndPreventsFurtherUse() {
        app.close();
        app.close();
        assertThrows(IllegalStateException.class, () -> app.getAllHotels(0, 1));
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () -> {
            while (Thread.getAllStackTraces().keySet().stream().anyMatch(t -> t.isAlive() && t.getName().startsWith("hotel-db-"))) {
                Thread.sleep(20);
            }
        });
    }
}
