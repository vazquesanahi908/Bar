package com.barclub.config;

import com.barclub.entity.*;
import com.barclub.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class DataInitializer {

    private final PasswordEncoder passwordEncoder;

    // Controla si se carga el menú y los clientes de ejemplo (empanadas,
    // pizzas, etc.) además de los 4 usuarios de prueba. Por defecto queda en
    // true para no cambiarle el comportamiento a instalaciones que ya existen
    // (por ejemplo, una instancia de demo que se usa para mostrarle la app a
    // clientes potenciales, con datos falsos a propósito). Para instalar una
    // instancia nueva ya "limpia" para un cliente real, sin tener que borrar
    // el menú de ejemplo a mano ni con el botón de Configuración, arrancá esa
    // instalación con la variable de entorno CARGAR_MENU_EJEMPLO=false: los 4
    // usuarios de prueba se siguen creando igual (hacen falta para poder
    // entrar la primera vez), pero el sistema arranca sin productos ni
    // clientes cargados, listo para cargar el menú real desde cero.
    @Value("${app.datos-ejemplo.cargar-menu:true}")
    private boolean cargarMenuEjemplo;

    @Bean
    public CommandLineRunner initData(
            UsuarioRepository usuarioRepo,
            ProductoRepository productoRepo,
            ClienteRepository clienteRepo,
            JdbcTemplate jdbc) {

        return args -> {
            // Migración automática: Hibernate 6 crea usuarios.rol como ENUM de MySQL
            // con los valores que existían en ese momento. Lo pasamos a VARCHAR para
            // que acepte roles nuevos (MOZO) sin ALTER manual. Inofensivo si ya es VARCHAR.
            try {
                jdbc.execute("ALTER TABLE usuarios MODIFY COLUMN rol VARCHAR(20) NOT NULL");
                log.info("Columna usuarios.rol normalizada a VARCHAR(20)");
            } catch (Exception e) {
                log.warn("No se pudo normalizar usuarios.rol (normal en el primer arranque): {}", e.getMessage());
            }

            // Solo carga datos si la DB está vacía
            if (usuarioRepo.count() == 0) {
                log.info("=== Cargando datos iniciales del sistema ===");

                // ---- Usuarios ----
                usuarioRepo.save(Usuario.builder()
                        .nombre("Admin Principal")
                        .email("admin@miapp.com")
                        .password(passwordEncoder.encode("admin123"))
                        .rol(Rol.ADMIN).build());

                usuarioRepo.save(Usuario.builder()
                        .nombre("Carlos Cajero")
                        .email("cajero@miapp.com")
                        .password(passwordEncoder.encode("cajero123"))
                        .rol(Rol.CAJERO).build());

                usuarioRepo.save(Usuario.builder()
                        .nombre("Maria Cocina")
                        .email("cocina@miapp.com")
                        .password(passwordEncoder.encode("cocina123"))
                        .rol(Rol.COCINA).build());

                usuarioRepo.save(Usuario.builder()
                        .nombre("Mateo Mozo")
                        .email("mozo@miapp.com")
                        .password(passwordEncoder.encode("mozo123"))
                        .rol(Rol.MOZO).build());

                log.info("  ✓ Usuarios creados");

                if (!cargarMenuEjemplo) {
                    log.info("=== CARGAR_MENU_EJEMPLO=false: se omite el menú y los clientes de ejemplo. " +
                            "Instalación arrancando limpia, lista para cargar el menú real. ===");
                    return;
                }

                // ---- Clientes ----
                clienteRepo.save(Cliente.builder()
                        .nombre("Lucas González").telefono("3447-551234").direccion("San Martin 450").build());
                clienteRepo.save(Cliente.builder()
                        .nombre("Ana Martínez").telefono("3447-221100").direccion("Belgrano 123").build());

                log.info("  ✓ Clientes creados");

                // ---- Productos - Empanadas ----
                productoRepo.save(p("Carne", "Cortada a cuchillo", 2500.0, "Empanadas"));
                productoRepo.save(p("Jamón y Queso", "", 2200.0, "Empanadas"));
                productoRepo.save(p("Pollo a la Mostaza", "", 2200.0, "Empanadas"));
                productoRepo.save(p("Verdura", "", 2200.0, "Empanadas"));
                productoRepo.save(p("Humita", "", 2200.0, "Empanadas"));
                productoRepo.save(p("Calabaza", "", 2200.0, "Empanadas"));

                // ---- Pizzas ----
                productoRepo.save(p("Muzzarella", "Media $16.000", 8500.0, "Pizzas"));
                productoRepo.save(p("Fugazzeta", "Media $20.000", 11000.0, "Pizzas"));
                productoRepo.save(p("Jamón y Morrones", "Media $24.000", 13000.0, "Pizzas"));
                productoRepo.save(p("Rúcula con Parmesano", "Media $17.000", 9000.0, "Pizzas"));
                productoRepo.save(p("4 Quesos", "Media $25.000", 12000.0, "Pizzas"));
                productoRepo.save(p("Napolitana", "Media $22.000", 12000.0, "Pizzas"));
                productoRepo.save(p("Roquefort", "Media $24.000", 12500.0, "Pizzas"));

                // ---- Sandwiches ----
                productoRepo.save(p("Super Pancho", "", 3000.0, "Sandwiches"));
                productoRepo.save(p("Bondiola Simple", "", 11000.0, "Sandwiches"));
                productoRepo.save(p("Bondiola Completa", "Con fritas", 13000.0, "Sandwiches"));
                productoRepo.save(p("Lomo Simple", "", 12000.0, "Sandwiches"));
                productoRepo.save(p("Lomo Completo", "Con fritas", 19000.0, "Sandwiches"));
                productoRepo.save(p("Hamburguesa Simple", "", 7000.0, "Sandwiches"));
                productoRepo.save(p("Hamburguesa de la casa", "Con fritas", 17000.0, "Sandwiches"));
                productoRepo.save(p("Tostado Jamón y Queso", "", 6500.0, "Sandwiches"));
                productoRepo.save(p("Tostado Primavera", "Jamón, queso, tomate y huevo", 8500.0, "Sandwiches"));

                // ---- Minutas ----
                productoRepo.save(p("Milanesa de Pollo Sola", "", 9000.0, "Minutas"));
                productoRepo.save(p("Milanesa de Pollo c/ Guarnición", "", 12500.0, "Minutas"));
                productoRepo.save(p("Milanesa de Pollo Napo Sola", "", 13000.0, "Minutas"));
                productoRepo.save(p("Milanesa de Pollo Napo c/ Guarnición", "", 15000.0, "Minutas"));
                productoRepo.save(p("Milanesa de Carne Sola", "", 8000.0, "Minutas"));
                productoRepo.save(p("Milanesa de Carne c/ Guarnición", "", 11000.0, "Minutas"));
                productoRepo.save(p("Milanesa de Carne Napo Sola", "", 10000.0, "Minutas"));
                productoRepo.save(p("Milanesa de Carne Napo c/ Guarnición", "", 12000.0, "Minutas"));

                // ---- Papas ----
                productoRepo.save(p("Papas Bastón", "", 4500.0, "Papas"));
                productoRepo.save(p("Papas con Cheddar", "", 5500.0, "Papas"));
                productoRepo.save(p("Papas Rancheras", "Cheddar, panceta y verdeo", 7500.0, "Papas"));
                productoRepo.save(p("Papas con Provenzal", "", 4800.0, "Papas"));

                // ---- Parrilla ----
                productoRepo.save(p("Matambre de Cerdo", "", 22000.0, "Parrilla"));
                productoRepo.save(p("Parrillada para 2", "Riñón, tripa, chinchulín, chorizo, morcilla, asado", 45000.0, "Parrilla"));
                productoRepo.save(p("Asado para 1 persona", "", 18500.0, "Parrilla"));
                productoRepo.save(p("Vacío", "", 19500.0, "Parrilla"));
                productoRepo.save(p("Chorizo", "", 4500.0, "Parrilla"));
                productoRepo.save(p("Morcilla", "", 4000.0, "Parrilla"));
                productoRepo.save(p("Riñón", "", 5500.0, "Parrilla"));
                productoRepo.save(p("Choripán", "", 5000.0, "Parrilla"));

                // ---- Pastas ----
                productoRepo.save(p("Tallarines", "Con salsa incluida", 10500.0, "Pastas"));
                productoRepo.save(p("Ñoquis de Papas", "Con salsa incluida", 13000.0, "Pastas"));
                productoRepo.save(p("Ravioles de Verdura", "Con salsa incluida", 15000.0, "Pastas"));
                productoRepo.save(p("Canelones de Verdura", "Con salsa incluida", 13000.0, "Pastas"));
                productoRepo.save(p("Sorrentinos de Jamón y Queso", "Con salsa incluida", 15000.0, "Pastas"));

                // ---- Ensaladas ----
                productoRepo.save(p("Lechuga y Tomate", "", 4800.0, "Ensaladas"));
                productoRepo.save(p("Mixta", "", 5500.0, "Ensaladas"));
                productoRepo.save(p("Completa", "", 6500.0, "Ensaladas"));
                productoRepo.save(p("Rusa", "", 5500.0, "Ensaladas"));

                // ---- Vegetariano ----
                productoRepo.save(p("Medallón de Quinoa", "", 4000.0, "Vegetariano"));
                productoRepo.save(p("Medallón de Lentejas", "", 4000.0, "Vegetariano"));
                productoRepo.save(p("Milanesa de Zucchini", "", 9000.0, "Vegetariano"));
                productoRepo.save(p("Milanesa de Berenjena", "", 9000.0, "Vegetariano"));

                // ---- Vinos ----
                productoRepo.save(p("Cosecha Tardía Dulce Blanco", "", 6200.0, "Vinos"));
                productoRepo.save(p("Postales", "", 8000.0, "Vinos"));
                productoRepo.save(p("Alma Mora", "", 10000.0, "Vinos"));
                productoRepo.save(p("Fond De Cave", "", 12000.0, "Vinos"));
                productoRepo.save(p("Santa Julia", "", 8600.0, "Vinos"));
                productoRepo.save(p("Emilia Malbec", "", 12000.0, "Vinos"));
                productoRepo.save(p("Colón Malbec", "", 7200.0, "Vinos"));

                // ---- Cervezas ----
                productoRepo.save(p("Santa Fe Lata", "", 3500.0, "Cervezas"));
                productoRepo.save(p("Heineken Lata", "", 5000.0, "Cervezas"));
                productoRepo.save(p("Salta Roja Lata", "", 3500.0, "Cervezas"));
                productoRepo.save(p("Corona Lata", "", 4000.0, "Cervezas"));

                // ---- Gaseosas ----
                productoRepo.save(p("Coca-Cola / Sprite vidrio", "Descartable", 6000.0, "Gaseosas"));
                productoRepo.save(p("Gaseosa Chica", "", 2500.0, "Gaseosas"));
                productoRepo.save(p("Jugo Grande", "", 3000.0, "Gaseosas"));
                productoRepo.save(p("Agua Grande", "", 3000.0, "Gaseosas"));
                productoRepo.save(p("Agua Chica", "", 2500.0, "Gaseosas"));

                // ---- Postres ----
                productoRepo.save(p("Flan Casero", "Hecho en casa", 4500.0, "Postres"));
                productoRepo.save(p("Casatta", "", 4500.0, "Postres"));
                productoRepo.save(p("Budín de Pan", "", 4500.0, "Postres"));
                productoRepo.save(p("Frutillas con Crema", "", 4800.0, "Postres"));

                log.info("  ✓ {} productos cargados", productoRepo.count());
                log.info("=== Datos iniciales cargados correctamente ===");
            }
        };
    }

    private Producto p(String nombre, String desc, Double precio, String categoria) {
        return Producto.builder()
                .nombre(nombre)
                .descripcion(desc.isBlank() ? null : desc)
                .precio(precio)
                .activo(true)
                .categoria(categoria)
                .build();
    }
}
