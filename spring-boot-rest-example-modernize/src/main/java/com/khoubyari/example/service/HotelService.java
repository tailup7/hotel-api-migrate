package com.khoubyari.example.service;

import com.khoubyari.example.dao.jpa.HotelRepository;
import com.khoubyari.example.domain.Hotel;
import com.khoubyari.example.exception.DataFormatException;
import com.khoubyari.example.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

@Transactional
public class HotelService {
    private static final Logger LOG = LoggerFactory.getLogger(HotelService.class);
    private final HotelRepository repository;
    private final DataSource dataSource;
    private final String serviceName;
    private final LongAdder largePayloads = new LongAdder();

    public HotelService(HotelRepository repository, DataSource dataSource, String serviceName) {
        this.repository = repository;
        this.dataSource = dataSource;
        this.serviceName = serviceName;
    }

    public Hotel createHotel(Hotel hotel) {
        LOG.info("Hotel service: create");
        // POST must never merge/overwrite an existing row supplied by a caller.
        if (hotel.getId() != null && hotel.getId() != 0) throw new DataFormatException("id must be omitted on create");
        hotel.setId(null);
        return repository.saveAndFlush(hotel);
    }

    @Transactional(readOnly = true)
    public Hotel getHotel(long id) {
        LOG.info("Hotel service: get id={}", id);
        return requireHotel(id);
    }

    @Transactional(readOnly = true)
    public Page<Hotel> getAllHotels(Integer page, Integer size) {
        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? 100 : size;
        if (pageNumber < 0 || pageSize < 1) throw new DataFormatException("page must be >= 0 and size must be >= 1");
        LOG.info("Hotel service: list page={}, size={}", pageNumber, pageSize);
        Page<Hotel> result = repository.findAll(PageRequest.of(pageNumber, pageSize, Sort.by("id")));
        if (pageSize > 50) largePayloads.increment();
        return result;
    }

    public void updateHotel(long id, Hotel values) {
        LOG.info("Hotel service: update id={}", id);
        Hotel existing = requireHotel(id);
        if (values.getId() == null || values.getId() != id) throw new DataFormatException("ID doesn't match!");
        existing.setName(values.getName());
        existing.setDescription(values.getDescription());
        existing.setCity(values.getCity());
        existing.setRating(values.getRating());
        repository.flush();
    }

    public void deleteHotel(long id) {
        LOG.info("Hotel service: delete id={}", id);
        repository.delete(requireHotel(id));
        repository.flush();
    }

    private Hotel requireHotel(long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("resource not found"));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Map<String, Object> health() {
        boolean valid;
        try (Connection connection = dataSource.getConnection()) {
            valid = connection.isValid(3);
        } catch (SQLException ex) {
            LOG.warn("Database health check failed (SQLState={})", ex.getSQLState());
            valid = false;
        }
        return Map.of("status", valid ? "UP" : "DOWN",
                "database", "PostgreSQL",
                "hotelService", Map.of("status", "itsok!", "profile", serviceName,
                        "details", "{ 'internals' : 'getting close to limit', 'profile' : '" + serviceName + "' }"));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Map<String, Object> metrics() {
        return Map.of("Khoubyari.HotelService.getAll.largePayload", largePayloads.sum());
    }
}
