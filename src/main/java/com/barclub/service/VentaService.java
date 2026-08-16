package com.barclub.service;

import com.barclub.dto.VentaRequestDTO;
import com.barclub.dto.VentaResponseDTO;
import com.barclub.entity.ConfigLocal;
import com.barclub.entity.CierreCaja;
import com.barclub.entity.EstadoPedido;
import com.barclub.entity.MetodoPago;
import com.barclub.entity.Pedido;
import com.barclub.entity.Venta;
import com.barclub.exception.BusinessException;
import com.barclub.exception.ResourceNotFoundException;
import com.barclub.repository.PedidoRepository;
import com.barclub.repository.VentaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class VentaService {

    private final VentaRepository ventaRepository;
    private final PedidoRepository pedidoRepository;
    private final ConfigLocalService configLocalService;
    private final com.barclub.websocket.RealtimeNotifier realtimeNotifier;
    private final com.barclub.repository.CierreCajaRepository cierreCajaRepository;

    // ---- Registrar venta (cierra el pedido) ----
    public VentaResponseDTO registrar(VentaRequestDTO dto) {
        Pedido pedido = pedidoRepository.findById(dto.getPedidoId())
                .orElseThrow(() -> new ResourceNotFoundException("Pedido", dto.getPedidoId()));

        // Se puede cobrar cualquier pedido que no esté ya entregado o cancelado.
        // Antes exigía estado LISTO, pero en la práctica el cajero cobra pedidos
        // que siguen en preparación, y eso hacía fallar el cobro.
        if (pedido.getEstado() == EstadoPedido.ENTREGADO
                || pedido.getEstado() == EstadoPedido.CANCELADO) {
            throw new BusinessException(
                "No se puede cobrar un pedido que ya está " + pedido.getEstado()
            );
        }

        // Verificar que no tenga ya una venta registrada
        if (ventaRepository.findByPedidoId(dto.getPedidoId()).isPresent()) {
            throw new BusinessException("Este pedido ya tiene una venta registrada");
        }

        // Crear la venta
        Venta venta = Venta.builder()
                .fecha(LocalDate.now())
                .hora(LocalTime.now())
                .total(pedido.getTotal())
                .metodoPago(dto.getMetodoPago())
                .pedido(pedido)
                .build();

        // Marcar pedido como ENTREGADO y sellar la hora de entrega
        pedido.setEstado(EstadoPedido.ENTREGADO);
        if (pedido.getEntregadoEn() == null) {
            pedido.setEntregadoEn(java.time.LocalDateTime.now());
        }
        pedidoRepository.save(pedido);
        realtimeNotifier.avisarPedidos();

        return toDTO(ventaRepository.save(venta));
    }

    // ---- Listar todas ----
    @Transactional(readOnly = true)
    public List<VentaResponseDTO> listarTodas() {
        return ventaRepository.findAll()
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // ---- Ventas del día ----
    @Transactional(readOnly = true)
    public List<VentaResponseDTO> listarPorFecha(LocalDate fecha) {
        return ventaRepository.findByFecha(fecha)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // ---- Total del día ----
    @Transactional(readOnly = true)
    public Double totalDelDia(LocalDate fecha) {
        return ventaRepository.sumTotalByFecha(fecha);
    }

    // ---- Informes por período ----
    @Transactional(readOnly = true)
    public Map<String, Object> informe(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null) throw new BusinessException("Indicá las fechas del informe");
        if (hasta.isBefore(desde)) throw new BusinessException("La fecha final no puede ser anterior a la inicial");

        List<Venta> ventas = ventaRepository.findEntreFechas(desde, hasta);

        double total = ventas.stream().mapToDouble(v -> v.getTotal() != null ? v.getTotal() : 0.0).sum();

        // Desglose por método de pago
        Map<String, Map<String, Object>> porPago = new LinkedHashMap<>();
        for (MetodoPago mp : MetodoPago.values()) {
            List<Venta> delMetodo = ventas.stream()
                    .filter(v -> v.getMetodoPago() == mp).collect(Collectors.toList());
            Map<String, Object> m = new HashMap<>();
            m.put("cantidad", delMetodo.size());
            m.put("total", delMetodo.stream().mapToDouble(v -> v.getTotal() != null ? v.getTotal() : 0.0).sum());
            porPago.put(mp.name(), m);
        }

        // Ranking de productos
        List<Map<String, Object>> productos = new ArrayList<>();
        for (Object[] f : ventaRepository.rankingProductos(desde, hasta)) {
            Map<String, Object> m = new HashMap<>();
            m.put("nombre", f[0]);
            m.put("categoria", f[1]);
            m.put("unidades", f[2] != null ? ((Number) f[2]).longValue() : 0L);
            m.put("ingreso", f[3] != null ? ((Number) f[3]).doubleValue() : 0.0);
            productos.add(m);
        }

        Map<String, Object> r = new HashMap<>();
        r.put("desde", desde.toString());
        r.put("hasta", hasta.toString());
        r.put("cantidadVentas", ventas.size());
        r.put("total", total);
        r.put("ticketPromedio", ventas.isEmpty() ? 0.0 : total / ventas.size());
        r.put("porMetodoPago", porPago);
        r.put("productos", productos);
        r.put("ventas", ventas.stream().map(this::toDTO).collect(Collectors.toList()));
        return r;
    }

    // ---- Cierre de caja ----
    @Transactional(readOnly = true)
    public List<VentaResponseDTO> listarDesdeCierre() {
        LocalDateTime c = obtenerMomentoCierre();
        if (c == null) return listarPorFecha(LocalDate.now());
        return ventaRepository.findDesde(c.toLocalDate(), c.toLocalTime())
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Double totalDesdeCierre() {
        return listarDesdeCierre().stream()
                .mapToDouble(v -> v.getTotal() != null ? v.getTotal() : 0.0).sum();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> estadoCaja() {
        Map<String, Object> m = new HashMap<>();
        m.put("abiertaDesde", configLocalService.obtener().getCierreCaja());
        return m;
    }

    public Map<String, Object> cerrarCaja() {
        List<VentaResponseDTO> ventas = listarDesdeCierre();
        double total = ventas.stream()
                .mapToDouble(v -> v.getTotal() != null ? v.getTotal() : 0.0).sum();

        ConfigLocal cfg = configLocalService.obtener();
        LocalDateTime momentoApertura = obtenerMomentoCierre(); // el cierre anterior = cuándo abrió esta caja
        LocalDateTime ahoraDt = LocalDateTime.now().withNano(0);
        String ahora = ahoraDt.toString();

        // Guardar en el historial ANTES de pisar cierreCaja, así "Movimientos
        // de hoy" puede seguir mostrando esta caja después de cerrada, en vez
        // de que sus ventas "desaparezcan" de la vista (bug reportado en QA).
        cierreCajaRepository.save(CierreCaja.builder()
                .fechaApertura(momentoApertura != null ? momentoApertura : ahoraDt.toLocalDate().atStartOfDay())
                .fechaCierre(ahoraDt)
                .totalVentas(total)
                .cantidadVentas(ventas.size())
                .build());

        cfg.setCierreCaja(ahora);
        configLocalService.guardar(cfg);

        Map<String, Object> m = new HashMap<>();
        m.put("cerradaEn", ahora);
        m.put("totalCerrado", total);
        m.put("cantidadVentas", ventas.size());
        return m;
    }

    // ---- Vista del día completo, separada por cada caja ----
    // Resuelve la ambigüedad reportada en QA sobre qué mostrar cuando hay
    // varias aperturas/cierres en un mismo día: se listan TODAS las cajas
    // de ESE día (cerradas + la actualmente abierta si corresponde), cada
    // una con su propio total, en vez de perder las ya cerradas. Funciona
    // tanto para "hoy" como para cualquier fecha del historial.
    @Transactional(readOnly = true)
    public List<com.barclub.dto.SesionCajaDTO> sesionesDe(LocalDate fecha) {
        List<com.barclub.dto.SesionCajaDTO> resultado = new ArrayList<>();

        cierreCajaRepository.findByFechaCierreBetweenOrderByFechaCierreAsc(
                        fecha.atStartOfDay(), fecha.atTime(LocalTime.MAX))
                .forEach(c -> resultado.add(com.barclub.dto.SesionCajaDTO.builder()
                        .apertura(c.getFechaApertura())
                        .cierre(c.getFechaCierre())
                        .total(c.getTotalVentas())
                        .cantidadVentas(c.getCantidadVentas())
                        .abierta(false)
                        .build()));

        // La caja actualmente abierta solo se agrega si estamos consultando
        // el día de HOY (no tiene sentido mostrar "abierta" en una fecha
        // pasada) y si efectivamente sigue abierta desde ese día o antes.
        if (fecha.isEqual(LocalDate.now())) {
            LocalDateTime momentoApertura = obtenerMomentoCierre();
            boolean aperturaEsDeHoy = momentoApertura == null || !momentoApertura.toLocalDate().isBefore(fecha);
            if (aperturaEsDeHoy) {
                List<VentaResponseDTO> ventasAbierta = listarDesdeCierre();
                double totalAbierta = ventasAbierta.stream()
                        .mapToDouble(v -> v.getTotal() != null ? v.getTotal() : 0.0).sum();
                resultado.add(com.barclub.dto.SesionCajaDTO.builder()
                        .apertura(momentoApertura != null ? momentoApertura : fecha.atStartOfDay())
                        .cierre(null)
                        .total(totalAbierta)
                        .cantidadVentas(ventasAbierta.size())
                        .abierta(true)
                        .build());
            }
        }

        return resultado;
    }

    private LocalDateTime obtenerMomentoCierre() {
        try {
            String c = configLocalService.obtener().getCierreCaja();
            if (c == null || c.isBlank()) return null;
            return LocalDateTime.parse(c);
        } catch (Exception e) {
            return null;
        }
    }

    // ---- Obtener por id ----
    @Transactional(readOnly = true)
    public VentaResponseDTO obtenerPorId(Long id) {
        return toDTO(ventaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Venta", id)));
    }

    // ---- Eliminar ventas de una fecha (borrado definitivo) ----
    public void eliminarPorFecha(LocalDate fecha) {
        List<Venta> ventas = ventaRepository.findByFecha(fecha);
        // Si se borra la venta, el pedido que quedó marcado como ENTREGADO por
        // ese cobro debe volver a un estado utilizable (LISTO). Si no, ese
        // pedido queda invisible para siempre: no aparece en "Pedidos activos"
        // (no está en PENDIENTE/PREPARACION/LISTO) y tampoco en Ventas (se
        // borró), y no se puede volver a cobrar (el registro de venta lo
        // bloquea mientras siga en ENTREGADO).
        for (Venta v : ventas) {
            Pedido pedido = v.getPedido();
            if (pedido != null && pedido.getEstado() == EstadoPedido.ENTREGADO) {
                pedido.setEstado(EstadoPedido.LISTO);
                pedidoRepository.save(pedido);
            }
        }
        ventaRepository.deleteAll(ventas);
        realtimeNotifier.avisarPedidos();
    }

    // ---- Eliminar una venta por id ----
    public void eliminarPorId(Long id) {
        Venta venta = ventaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Venta", id));
        // Mismo motivo que en eliminarPorFecha: sin esto el pedido queda
        // huérfano (invisible en Pedidos activos y sin venta que lo respalde).
        Pedido pedido = venta.getPedido();
        if (pedido != null && pedido.getEstado() == EstadoPedido.ENTREGADO) {
            pedido.setEstado(EstadoPedido.LISTO);
            pedidoRepository.save(pedido);
        }
        ventaRepository.deleteById(id);
        realtimeNotifier.avisarPedidos();
    }

    // ---- Mapper ----
    public VentaResponseDTO toDTO(Venta v) {
        String nombreCliente = null;
        String tipoPedido = null;
        String mesa = null;
        if (v.getPedido() != null) {
            nombreCliente = v.getPedido().getNombreCliente() != null
                    ? v.getPedido().getNombreCliente()
                    : (v.getPedido().getCliente() != null ? v.getPedido().getCliente().getNombre() : "Sin nombre");
            tipoPedido = v.getPedido().getTipo() != null ? v.getPedido().getTipo().name() : null;
            mesa = v.getPedido().getMesa();
        }
        return VentaResponseDTO.builder()
                .id(v.getId())
                .fecha(v.getFecha())
                .hora(v.getHora())
                .total(v.getTotal())
                .metodoPago(v.getMetodoPago())
                .pedidoId(v.getPedido() != null ? v.getPedido().getId() : null)
                .nombreCliente(nombreCliente)
                .tipoPedido(tipoPedido)
                .mesa(mesa)
                .build();
    }
}
