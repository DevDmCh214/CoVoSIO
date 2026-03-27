package com.covosio.security;

import com.covosio.entity.User;
import com.covosio.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Loads a User by email and builds Spring Security {@link UserDetails}.
 * The role is derived from the JPA discriminator value (dtype).
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Loads a user by their email address.
     *
     * @param email the user's email (used as username)
     * @return Spring Security UserDetails including role authority
     * @throws UsernameNotFoundException if no user exists with the given email
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        String role = resolveRole(user);

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + role)))
                .accountLocked(!user.getIsActive())
                .build();
    }

    private String resolveRole(User user) {
        return switch (user.getClass().getSimpleName()) {
            case "Passenger" -> "PASSENGER";
            case "Driver"    -> "DRIVER";
            case "Admin"     -> "ADMIN";
            default          -> "PASSENGER";
        };
    }
}
