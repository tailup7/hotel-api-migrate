package com.khoubyari.example.api;

import com.khoubyari.example.config.DatabaseSettings;
import com.khoubyari.example.config.PersistenceConfiguration;
import com.khoubyari.example.service.HotelService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/**
 * One instance per Mule application. Register as a singleton with destroy-method="close".
 * Owns its Spring/JPA context and pool; does not start HTTP servers or shutdown hooks.
 */
public final class HotelApplication implements AutoCloseable {
    private final AnnotationConfigApplicationContext context;
    private final HotelService service;
    private final ReentrantReadWriteLock lifecycle = new ReentrantReadWriteLock();
    private boolean closed;

    public HotelApplication() { this(DatabaseSettings.fromEnvironment()); }
    public HotelApplication(Map<String, String> settings) { this(DatabaseSettings.from(settings)); }

    public HotelApplication(DatabaseSettings settings) {
        context = new AnnotationConfigApplicationContext();
        context.setClassLoader(HotelApplication.class.getClassLoader());
        try {
            withClassLoader(() -> {
                context.registerBean(DatabaseSettings.class, () -> settings);
                context.register(PersistenceConfiguration.class);
                context.refresh();
                return null;
            });
            service = context.getBean(HotelService.class);
        } catch (RuntimeException | Error ex) {
            context.close();
            throw ex;
        }
    }

    public Map<String, Object> createHotel(Map<String, Object> hotel) {
        return invoke(() -> HotelValues.write(service.createHotel(HotelValues.read(hotel))));
    }

    public Map<String, Object> getHotel(long id) {
        return invoke(() -> HotelValues.write(service.getHotel(id)));
    }

    public Map<String, Object> getAllHotels(Integer page, Integer size) {
        return invoke(() -> {
            var result = service.getAllHotels(page, size);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("content", result.getContent().stream().map(HotelValues::write).toList());
            response.put("totalElements", result.getTotalElements());
            response.put("totalPages", result.getTotalPages());
            response.put("number", result.getNumber());
            response.put("size", result.getSize());
            response.put("numberOfElements", result.getNumberOfElements());
            response.put("first", result.isFirst());
            response.put("last", result.isLast());
            response.put("sort", java.util.List.of(Map.of("property", "id", "direction", "ASC",
                    "ascending", true, "descending", false, "ignoreCase", false, "nullHandling", "NATIVE")));
            return response;
        });
    }

    public void updateHotel(long id, Map<String, Object> hotel) {
        invoke(() -> { service.updateHotel(id, HotelValues.read(hotel)); return null; });
    }

    public void deleteHotel(long id) {
        invoke(() -> { service.deleteHotel(id); return null; });
    }

    public Map<String, Object> health() { return invoke(service::health); }
    public Map<String, Object> metrics() { return invoke(service::metrics); }

    private <T> T invoke(Supplier<T> operation) {
        lifecycle.readLock().lock();
        try {
            if (closed) throw new IllegalStateException("HotelApplication is closed");
            return withClassLoader(operation);
        } finally {
            lifecycle.readLock().unlock();
        }
    }

    private static <T> T withClassLoader(Supplier<T> operation) {
        Thread thread = Thread.currentThread();
        ClassLoader original = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(HotelApplication.class.getClassLoader());
            return operation.get();
        } finally {
            thread.setContextClassLoader(original);
        }
    }

    @Override public void close() {
        lifecycle.writeLock().lock();
        try {
            if (!closed) {
                closed = true;
                withClassLoader(() -> { context.close(); return null; });
            }
        } finally {
            lifecycle.writeLock().unlock();
        }
    }
}
