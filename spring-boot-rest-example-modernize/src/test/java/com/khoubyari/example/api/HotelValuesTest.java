package com.khoubyari.example.api;

import com.khoubyari.example.config.DatabaseSettings;
import com.khoubyari.example.exception.DataFormatException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class HotelValuesTest {
    @Test void acceptsDataWeaveNumbersAndPreservesNulls() {
        var input = Map.<String, Object>of("id", new BigDecimal("12"), "name", "Hotel", "rating", new BigDecimal("4"));
        var result = HotelValues.write(HotelValues.read(input));
        assertEquals(12L, result.get("id"));
        assertEquals(4, result.get("rating"));
        assertTrue(result.containsKey("city"));
        assertNull(result.get("city"));
        assertEquals(0, HotelValues.read(Map.of("name", "Hotel")).getRating());
    }

    @Test void rejectsInvalidNumbersWithoutTruncating() {
        assertThrows(DataFormatException.class, () -> HotelValues.read(Map.of("name", "Hotel", "rating", 2.5)));
        assertThrows(DataFormatException.class, () -> HotelValues.read(Map.of("name", "Hotel", "rating", Long.MAX_VALUE)));
        assertThrows(DataFormatException.class, () -> HotelValues.read(Map.of("name", "Hotel", "id", "NaN")));
    }

    @Test void requiresNameAndRejectsInvalidFieldTypes() {
        assertThrows(DataFormatException.class, () -> HotelValues.read(null));
        assertThrows(DataFormatException.class, () -> HotelValues.read(Map.of()));
        assertThrows(DataFormatException.class, () -> HotelValues.read(Map.of("name", 5)));
        assertThrows(DataFormatException.class, () -> HotelValues.read(Map.of("name", "a".repeat(256))));
    }

    @Test void settingsRequireExplicitPasswordAndDoNotLeakIt() {
        assertThrows(IllegalArgumentException.class, () -> DatabaseSettings.from(Map.of()));
        var values = new HashMap<>(Map.of("DB_PASSWORD", "test-secret"));
        var settings = DatabaseSettings.from(values);
        assertEquals("validate", settings.getSchemaMode());
        assertFalse(settings.toString().contains("test-secret"));
        values.put("DB_URL", "jdbc:other://localhost/db");
        assertThrows(IllegalArgumentException.class, () -> DatabaseSettings.from(values));
        values.remove("DB_URL");
        values.put("DB_SCHEMA_MODE", "create-drop");
        assertThrows(IllegalArgumentException.class, () -> DatabaseSettings.from(values));
    }
}
