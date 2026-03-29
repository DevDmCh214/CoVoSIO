package com.covosio.repository;

import com.covosio.entity.Role;
import com.covosio.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    long countByRole(Role role);

    Page<User> findByRole(Role role, Pageable pageable);

    /**
     * Flexible admin user query — all parameters are optional (null = no filter).
     * Filters by role, isActive status, email substring, and name substring.
     */
    @Query("""
            SELECT u FROM User u
            WHERE (:role     IS NULL OR u.role     = :role)
              AND (:isActive IS NULL OR u.isActive = :isActive)
              AND (:email    IS NULL OR LOWER(u.email)     LIKE LOWER(CONCAT('%', :email,     '%')))
              AND (:name     IS NULL OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :name, '%'))
                                    OR LOWER(u.lastName)  LIKE LOWER(CONCAT('%', :name, '%')))
            """)
    Page<User> findByFilters(@Param("role")     Role role,
                             @Param("isActive") Boolean isActive,
                             @Param("email")    String email,
                             @Param("name")     String name,
                             Pageable pageable);
}
