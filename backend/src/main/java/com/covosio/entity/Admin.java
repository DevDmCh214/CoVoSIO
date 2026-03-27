package com.covosio.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Entity
@Table(name = "admins")
@DiscriminatorValue("ADMIN")
@PrimaryKeyJoinColumn(name = "user_id")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Admin extends User {

    /** Comma-separated list of extra admin permissions (e.g. "MANAGE_USERS,MANAGE_DOCS"). */
    @Column(columnDefinition = "TEXT")
    private String permissions;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;
}
