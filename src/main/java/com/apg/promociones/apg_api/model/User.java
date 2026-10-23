package com.apg.promociones.apg_api.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Integer id;

    @Column(name = "user_first_name", nullable = false)
    private String firstName;

    @Column(name = "user_second_name")
    private String secondName;

    @Column(name = "user_last_name", nullable = false)
    private String lastName;

    @Column(name = "user_birthday", nullable = false)
    private LocalDate birthday;

    @Column(name = "user_join_date", updatable = false)
    private LocalDateTime joinDate;

    @Column(name = "user_email", nullable = false, unique = true)
    private String email;

    @Column(name = "user_password_hash", nullable = false)
    private String passwordHash;
}
