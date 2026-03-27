package com.covosio.repository;

import com.covosio.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Admin stats: count users by discriminator value (UC-A11). */
    @Query(nativeQuery = true, value = "SELECT COUNT(*) FROM users WHERE dtype = :dtype")
    long countByDtype(@Param("dtype") String dtype);
}
