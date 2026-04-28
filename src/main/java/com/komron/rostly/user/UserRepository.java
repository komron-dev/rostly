package com.komron.rostly.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    List<User> findAllByRole(Role role);

    @Query("""
    SELECT u FROM User u
    WHERE u.id != :excludeId
    AND (:role IS NULL OR u.role = :role)
    AND (CAST(:search AS string) IS NULL OR (
        LOWER(u.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
        OR LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
    ))
    """)
    Page<User> listUsers(
            @Param("excludeId") UUID excludeId,
            @Param("role") Role role,
            @Param("search") String search,
            Pageable pageable
    );
}