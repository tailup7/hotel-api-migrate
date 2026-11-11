package com.khoubyari.example.api;

import com.khoubyari.example.domain.Hotel;
import com.khoubyari.example.exception.DataFormatException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** JDK collection types form the boundary with DataWeave, never JPA proxies. */
final class HotelValues {
    private HotelValues() { }

    static Hotel read(Map<String, Object> values) {
        if (values == null) throw new DataFormatException("Hotel body is required");
        Hotel hotel = new Hotel();
        Object id = values.get("id");
        if (id != null) hotel.setId(longValue(id, "id"));
        hotel.setName(text(values, "name", true));
        hotel.setDescription(text(values, "description", false));
        hotel.setCity(text(values, "city", false));
        if (values.get("rating") != null) {
            long rating = longValue(values.get("rating"), "rating");
            if (rating < Integer.MIN_VALUE || rating > Integer.MAX_VALUE) {
                throw new DataFormatException("rating must be a 32-bit integer");
            }
            hotel.setRating((int) rating);
        }
        return hotel;
    }

    private static String text(Map<String, Object> values, String key, boolean required) {
        Object value = values.get(key);
        if (value == null && !required) return null;
        if (!(value instanceof String string)) throw new DataFormatException(key + " must be a string");
        if (string.length() > 255) throw new DataFormatException(key + " must be at most 255 characters");
        return string;
    }

    private static long longValue(Object value, String key) {
        try {
            if (!(value instanceof Number) && !(value instanceof String)) throw new NumberFormatException();
            return new BigDecimal(value.toString()).longValueExact();
        } catch (ArithmeticException | NumberFormatException ex) {
            throw new DataFormatException(key + " must be an integer");
        }
    }

    static Map<String, Object> write(Hotel hotel) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", hotel.getId());
        result.put("name", hotel.getName());
        result.put("description", hotel.getDescription());
        result.put("city", hotel.getCity());
        result.put("rating", hotel.getRating());
        return result;
    }
}
