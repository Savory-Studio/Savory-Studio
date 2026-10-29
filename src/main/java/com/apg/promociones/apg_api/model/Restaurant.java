package com.apg.promociones.apg_api.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "restaurants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Restaurant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "restaurant_id")
    private Integer id;

    @Column(name = "restaurant_name", nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_owner_id", nullable = false)
    private User owner;

    @Column(name = "restaurant_description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "restaurant_full_address", nullable = false)
    private String fullAddress;

    @Column(name = "restaurant_creation_date", updatable = false)
    private LocalDateTime creationDate;
}
