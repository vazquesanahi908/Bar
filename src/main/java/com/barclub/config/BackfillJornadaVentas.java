package com.barclub.config;

import com.barclub.entity.CierreCaja;
import com.barclub.entity.Venta;
import com.barclub.repository.CierreCajaRepository;
import com.barclub.repository.VentaRepository;
import jakarta.annotation.PostConstruct;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Las ventas registradas ANTES de que existiera el campo "jornada" (ver
 * Venta.jornada) quedaron con ese valor vacío en la base. Como ahora todo lo
 * que agrupa "por día" (Resumen por medio de pago, detalle de cada caja,
 * etc.) se apoya en ese campo, esas ventas viejas quedaban invisibles ahí
 * aunque siguieran contando en el resumen de la caja (que se había guardado
 * aparte, en su momento). Esto corre una sola vez al arrancar el servidor y
 * les asigna la jornada que les corresponde según en qué caja cayeron —
 * después de la primera vez no encuentra nada para arreglar y no hace nada.
 */
@Component
public class BackfillJornadaVentas {

    private static final Logger log = LoggerFactory.getLogger(BackfillJornadaVentas.class);

    private final VentaRepository ventaRepository;
    private final CierreCajaRepository cierreCajaRepository;

    public BackfillJornadaVentas(VentaRepository ventaRepository, CierreCajaRepository cierreCajaRepository) {
        this.ventaRepository = ventaRepository;
        this.cierreCajaRepository = cierreCajaRepository;
    }

    @PostConstruct
    @Transactional
    public void rellenar() {
        List<Venta> sinJornada = ventaRepository.findAll().stream()
                .filter(v -> v.getJornada() == null)
                .toList();
        if (sinJornada.isEmpty()) return;

        List<CierreCaja> cajas = cierreCajaRepository.findAll();
        int corregidas = 0;
        for (Venta v : sinJornada) {
            if (v.getFecha() == null || v.getHora() == null) continue;
            LocalDateTime momento = LocalDateTime.of(v.getFecha(), v.getHora());

            // Buscar en qué caja cayó esta venta (apertura <= momento < cierre)
            // y usar la fecha en que ESA caja se abrió como jornada.
            LocalDate jornada = cajas.stream()
                    .filter(c -> c.getFechaApertura() != null && c.getFechaCierre() != null)
                    .filter(c -> !momento.isBefore(c.getFechaApertura()) && momento.isBefore(c.getFechaCierre()))
                    .map(c -> c.getFechaApertura().toLocalDate())
                    .findFirst()
                    // Si no cayó en ninguna caja conocida (dato muy viejo, de
                    // antes de que existiera el historial de cajas), se usa
                    // directamente su propia fecha como mejor estimación.
                    .orElse(v.getFecha());

            v.setJornada(jornada);
            corregidas++;
        }
        ventaRepository.saveAll(sinJornada);
        log.info("BackfillJornadaVentas: se completó la jornada de {} venta(s) vieja(s).", corregidas);
    }
}
