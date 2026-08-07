package com.barclub.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Aviso de arranque (solo en el log, no bloquea nada) cuando el sistema sigue
 * usando las contraseñas/claves de fábrica en vez de las variables de entorno
 * (JWT_SECRET, MASTER_KEY, DB_PASSWORD). Pensado para instalaciones que van a
 * un cliente real: sirve para no olvidarse de cambiarlas antes de entregar.
 *
 * No cambia ningún valor ni comportamiento existente — solo deja constancia
 * en el log al iniciar. Development/uso local sigue funcionando exactamente
 * igual que antes.
 */
@Component
public class CredencialesPorDefectoWarner {

    private static final Logger log = LoggerFactory.getLogger(CredencialesPorDefectoWarner.class);

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${app.master-key}")
    private String masterKey;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    private static final String JWT_SECRET_DEFAULT = "CambiameEnProduccionSecretJWTGenericoDelSistema2026";
    private static final String MASTER_KEY_DEFAULT = "admin2026";

    @PostConstruct
    public void avisarSiHayValoresPorDefecto() {
        boolean hayDefaults = false;
        StringBuilder detalle = new StringBuilder();

        if (JWT_SECRET_DEFAULT.equals(jwtSecret)) {
            detalle.append("\n  - JWT_SECRET: usando el valor de fábrica (definir la variable de entorno JWT_SECRET)");
            hayDefaults = true;
        }
        if (MASTER_KEY_DEFAULT.equals(masterKey)) {
            detalle.append("\n  - MASTER_KEY: usando el valor de fábrica (definir la variable de entorno MASTER_KEY)");
            hayDefaults = true;
        }
        if (dbPassword == null || dbPassword.isBlank()) {
            detalle.append("\n  - DB_PASSWORD: no está definida");
            hayDefaults = true;
        }

        if (hayDefaults) {
            log.warn("=====================================================================");
            log.warn("ATENCIÓN: este sistema está corriendo con credenciales de fábrica:{}", detalle);
            log.warn("Antes de entregarlo a un cliente real, definí las variables de entorno");
            log.warn("correspondientes (cada instalación debería tener las suyas propias).");
            log.warn("Para uso local/desarrollo esto no es un problema.");
            log.warn("=====================================================================");
        }
    }
}
