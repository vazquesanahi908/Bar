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
                .requestMatchers(HttpMethod.POST, "/api/usuarios/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/usuarios/reset-password").permitAll()
                // Canal de avisos en tiempo real (WebSocket): nunca manda datos
                // sensibles, solo un aviso de "algo cambió" (ver RealtimeNotifier),
                // así que no necesita requerir sesión para el handshake.
                .requestMatchers("/ws/**").permitAll()
                // Swagger: solo ADMIN (no exponer el mapa de la API al público)
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").hasRole("ADMIN")

                // Cambiar la propia contraseña: cualquier usuario logueado (no
                // solo ADMIN), porque cualquier rol puede haber quedado con la
                // contraseña de fábrica. Va ANTES de la regla general de
                // "/api/usuarios/**" (solo ADMIN) para que la pise.
                .requestMatchers(HttpMethod.PATCH, "/api/usuarios/me/password").authenticated()

                // ── SOLO ADMIN: administración del sistema ──────────────────
                // Gestión de usuarios, configuración del local, backups y
                // puesta a punto de una instalación nueva para un cliente.
                .requestMatchers("/api/usuarios/**").hasRole("ADMIN")
                .requestMatchers("/api/setup/**").hasRole("ADMIN")
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
                // Editar un pedido ya cargado (productos y datos del cliente,
                // mientras esté PENDIENTE o PREPARACION): solo admin y cajero.
                .requestMatchers(HttpMethod.POST,   "/api/pedidos/*/detalles").hasAnyRole("ADMIN", "CAJERO")
                .requestMatchers(HttpMethod.PATCH,  "/api/pedidos/*/detalles/*/cantidad").hasAnyRole("ADMIN", "CAJERO")
                .requestMatchers(HttpMethod.PATCH,  "/api/pedidos/*/datos-cliente").hasAnyRole("ADMIN", "CAJERO")
                // Crear pedidos: también el mozo desde el salón.
                .requestMatchers(HttpMethod.POST,   "/api/pedidos/**").hasAnyRole("ADMIN", "CAJERO", "MOZO")
                // Cancelar un pedido: solo admin y cajero (el cliente cancela por WhatsApp).
                .requestMatchers(HttpMethod.PATCH,  "/api/pedidos/*/cancelar").hasAnyRole("ADMIN", "CAJERO")
                // Cambiar el estado (avance del Kanban): admin, cajero y cocina (no el mozo).
                .requestMatchers(HttpMethod.PATCH,  "/api/pedidos/*/estado").hasAnyRole("ADMIN", "CAJERO", "COCINA")
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
        // Única fuente de verdad de CORS para toda la API (antes había también
        // @CrossOrigin(origins = "*") repetido en 7 controllers, redundante con
        // esta configuración global y más propenso a quedar desactualizado si
        // se cambia acá pero no allá). Cualquier ajuste de CORS futuro se hace
        // solo en este lugar.
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        // La app autentica con token en el header Authorization, no con cookies.
        // Con credenciales en false, permitir cualquier origen deja de ser riesgoso.
        config.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        // El handshake inicial de SockJS (antes de convertirse en WebSocket)
        // es un pedido HTTP normal, así que también necesita CORS habilitado
        // para que el frontend (otro dominio, en Netlify) pueda conectarse.
        source.registerCorsConfiguration("/ws/**", config);
        return source;
    }
}
