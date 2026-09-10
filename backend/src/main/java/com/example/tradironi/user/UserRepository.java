package com.example.tradironi.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByKeycloakId(UUID keycloakId);

    boolean existsByKeycloakId(UUID keycloakId);
}
