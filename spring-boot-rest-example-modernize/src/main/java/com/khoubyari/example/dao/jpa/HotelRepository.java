package com.khoubyari.example.dao.jpa;

import com.khoubyari.example.domain.Hotel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HotelRepository extends JpaRepository<Hotel, Long> {
    Hotel findHotelByCity(String city);
}
