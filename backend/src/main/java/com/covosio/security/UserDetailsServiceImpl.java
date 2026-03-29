package com.covosio.security;

import com.covosio.entity.Admin;
import com.covosio.entity.User;
import com.covosio.repository.AdminRepository;
import com.covosio.repository.DriverProfileRepository;
import com.covosio.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Loads a User (platform user) or Admin (staff) by email and builds Spring Security UserDetails.
 * Platform users always have ROLE_PASSENGER; drivers also have ROLE_DRIVER.
 * Admins are loaded from a separate table and have ROLE_ADMIN only.
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;
    private final AdminRepository adminRepository;
    private final DriverProfileRepository driverProfileRepository;

    /**
     * Loads a user by their email address.
     * Tries platform users first, then admin staff.
     *
     * @param email the user's email (used as username)
     * @return Spring Security UserDetails including role authority(ies)
     * @throws UsernameNotFoundException if no user or admin exists with the given email
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // 1. Try admin first — admins take precedence if email exists in both tables
        Optional<Admin> adminOpt = adminRepository.findByEmail(email);
        if (adminOpt.isPresent()) {
            Admin admin = adminOpt.get();
            return org.springframework.security.core.userdetails.User.builder()
                    .username(admin.getEmail())
                    .password(admin.getPasswordHash())
                    .accountLocked(!admin.getIsActive())
                    .disabled(false)
                    .credentialsExpired(false)
                    .accountExpired(false)
                    .authorities(List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
                    .build();
        }

        // 2. Try platform user
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
        List<SimpleGrantedAuthority> roles = new ArrayList<>();
        roles.add(new SimpleGrantedAuthority("ROLE_PASSENGER"));
        if (driverProfileRepository.existsByUserId(user.getId())) {
            roles.add(new SimpleGrantedAuthority("ROLE_DRIVER"));
        }
        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash())
                .accountLocked(!user.getIsActive())
                .disabled(false)
                .credentialsExpired(false)
                .accountExpired(false)
                .authorities(roles)
                .build();
    }
}
