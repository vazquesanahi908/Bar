package com.barclub.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // ── Endpoints públicos ──────────────────────────────────────
                .requestMatchers(HttpMethod.GET,  "/api/productos/activos").permitAll()
                .requestMatchers(HttpMethod.GET,  "/api/productos/categoria/**").permitAll()
                .requestMatchers(HttpMethod.GET,  "/api/productos/buscar").permitAll()
                .requestMatchers(HttpMethod.GET,  "/api/config").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/pedidos").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/reservas").permitAll()
                .requestMatchers(HttpMethod.PATCH,"/api/pedidos/*/cancelar").permitAll()
                .requestMatchers(HttpMethod.PATCH,"/api/reservas/*/cancelar").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/usuarios/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/usuarios/reset-password").permitAll()
                // Swagger
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()

                // ── SOLO ADMIN: administración del sistema ──────────────────
                // Gestión de usuarios, configuración del local y backups.
                .requestMatchers("/api/usuarios/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT,    "/api/config/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST,   "/api/config/**").hasRole("ADMIN")
                .requestMatchers("/api/backups/**").hasRole("ADMIN")
                // Borrar ventas es destructivo: solo el dueño.
                .requestMatchers(HttpMethod.DELETE, "/api/ventas/**").hasRole("ADMIN")

                // ── ADMIN y CAJERO: manejo de dinero ────────────────────────
                // Cobrar, cerrar caja y consultar ventas e informes.
                .requestMatchers("/api/ventas/**").hasAnyRole("ADMIN", "CAJERO")
                .requestMatchers("/api/dashboard/**").hasAnyRole("ADMIN", "CAJERO")
                .requestMatchers("/api/clientes/**").hasAnyRole("ADMIN", "CAJERO")
                // Reservas: las gestionan admin y cajero.
                .requestMatchers(HttpMethod.GET,    "/api/reservas/**").hasAnyRole("ADMIN", "CAJERO")
                .requestMatchers(HttpMethod.PATCH,  "/api/reservas/**").hasAnyRole("ADMIN", "CAJERO")
                .requestMatchers(HttpMethod.DELETE, "/api/reservas/**").hasAnyRole("ADMIN", "CAJERO")

                // ── Menú: lo edita admin y cajero; todos pueden leerlo ──────
                .requestMatchers(HttpMethod.POST,   "/api/productos/**").hasAnyRole("ADMIN", "CAJERO")
                .requestMatchers(HttpMethod.PUT,    "/api/productos/**").hasAnyRole("ADMIN", "CAJERO")
                .requestMatchers(HttpMethod.PATCH,  "/api/productos/**").hasAnyRole("ADMIN", "CAJERO")
                .requestMatchers(HttpMethod.DELETE, "/api/productos/**").hasAnyRole("ADMIN", "CAJERO")

                // ── Pedidos ────────────────────────────────────────────────
                // Borrar pedidos es destructivo: admin y cajero.
                .requestMatchers(HttpMethod.DELETE, "/api/pedidos/**").hasAnyRole("ADMIN", "CAJERO")
                // Crear pedidos: también el mozo desde el salón.
                .requestMatchers(HttpMethod.POST,   "/api/pedidos/**").hasAnyRole("ADMIN", "CAJERO", "MOZO")
                // Ver pedidos y cambiar su estado: los cuatro roles
                // (cocina necesita marcar LISTO).

                // ── Todo lo demás requiere JWT ──────────────────────────────
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
