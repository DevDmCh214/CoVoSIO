package com.covosio.config;

import com.covosio.security.JwtFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final UserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public auth endpoints
                .requestMatchers(
                    "/auth/register",
                    "/auth/login",
                    "/auth/refresh",
                    "/auth/logout"
                ).permitAll()
                // OpenAPI/Swagger docs (dev convenience)
                .requestMatchers("/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                // User profile endpoints — any authenticated user (UC-C05 to UC-C08)
                .requestMatchers("/users/me", "/users/me/password").authenticated()
                .requestMatchers("/users/{id}").authenticated()
                // Car endpoints — drivers only (UC-D01, UC-D01b)
                .requestMatchers("/cars", "/cars/**").hasRole("DRIVER")
                // Document endpoints — drivers only (UC-D11, UC-D12)
                .requestMatchers("/documents").hasRole("DRIVER")
                .requestMatchers("/users/me/documents", "/users/me/documents/**").hasRole("DRIVER")
                // Review endpoint — any authenticated user (passengers UC-P06, drivers UC-D09)
                .requestMatchers(HttpMethod.POST, "/reservations/*/review").authenticated()
                // Reservation endpoints — passengers only (UC-P03, UC-P04, UC-P05)
                .requestMatchers(HttpMethod.POST,   "/reservations").hasRole("PASSENGER")
                .requestMatchers(HttpMethod.DELETE, "/reservations/**").hasRole("PASSENGER")
                .requestMatchers(HttpMethod.GET,    "/reservations/me").hasRole("PASSENGER")
                // Trip endpoints — read access for all authenticated; write access for drivers only
                .requestMatchers(HttpMethod.GET,    "/trips/*/reservations").hasRole("DRIVER")
                .requestMatchers(HttpMethod.POST,   "/trips").hasRole("DRIVER")
                .requestMatchers(HttpMethod.PUT,    "/trips/**").hasRole("DRIVER")
                .requestMatchers(HttpMethod.DELETE, "/trips/**").hasRole("DRIVER")
                .requestMatchers(HttpMethod.GET,    "/trips/me", "/trips/map/me").hasRole("DRIVER")
                .requestMatchers(HttpMethod.GET,    "/trips", "/trips/**").authenticated()
                // All other endpoints require authentication
                .anyRequest().authenticated()
            )
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
