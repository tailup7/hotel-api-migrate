package com.khoubyari.example.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "hotel")
public class Hotel {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "hotel_id")
    @SequenceGenerator(name = "hotel_id", sequenceName = "hibernate_sequence", allocationSize = 1)
    private Long id;
    @Column(nullable = false)
    private String name;
    private String description;
    private String city;
    private int rating;

    public Hotel() { }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = rating; }
}
