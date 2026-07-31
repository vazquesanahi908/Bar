package com.barclub.service;

import com.barclub.dto.*;
import com.barclub.entity.*;
import com.barclub.exception.BusinessException;
import com.barclub.exception.ResourceNotFoundException;
import com.barclub.repository.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class PedidoService {

    private static final Logger logger = LoggerFactory.getLogger(PedidoService.class);

    private final PedidoRepository pedidoRepository;
    private final ProductoRepository productoRepository;
    private final ClienteRepository clienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final VentaRepository ventaRepository;
    private final ProductoService productoService;
    private final com.barclub.service.ConfigLocalService configLocalService;
    private final ClienteService clienteService;
    private final UsuarioService usuarioService;

    @Transactional(readOnly = true)
    public List<PedidoResponseDTO> listarTodos() {
        return pedidoRepository.findAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PedidoResponseDTO> listarActivos() {
        return pedidoRepository.findPedidosActivos().stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PedidoResponseDTO> listarPorEstado(EstadoPedido estado) {
        return pedidoRepository.findByEstado(estado).stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PedidoResponseDTO> listarPorFecha(LocalDate fecha) {
        return pedidoRepository.findByFecha(fecha).stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PedidoResponseDTO obtenerPorId(Long id) {
        return toDTO(pedidoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido", id)));
    }

    public PedidoResponseDTO crear(PedidoRequestDTO dto) {
        logger.info("NUEVO PEDIDO: tipo={}, usuario={}, cliente='{}', productos={}",
                dto.getTipo(), dto.getUsuarioId(), dto.getNombreCliente(),
                dto.getDetalles() != null ? dto.getDetalles().size() : 0);

        // Los pedidos de la página pública no pertenecen a ningún empleado, pero el
        // sistema necesita asociarlos a un usuario. Si el indicado no existe (por
        // ejemplo, si ese usuario fue eliminado), usamos cualquier admin disponible
        // en lugar de rechazar el pedido: un cliente no debe quedarse sin pedir por
        // un cambio interno de usuarios.
        Usuario usuario = (dto.getUsuarioId() == null)
                ? null
                : usuarioRepository.findById(dto.getUsuarioId()).orElse(null);
        if (usuario == null) {
            usuario = usuarioRepository.findAll().stream()
                    .filter(u -> u.getRol() == Rol.ADMIN)
                    .findFirst()
                    .orElseGet(() -> usuarioRepository.findAll().stream().findFirst()
                            .orElseThrow(() -> new BusinessException(
                                    "No hay usuarios cargados en el sistema")));
            logger.warn("Pedido recibido con usuarioId={} inexistente. Se asigna a {}",
                    dto.getUsuarioId(), usuario.getEmail());
        }

        // El rol MOZO solo puede registrar pedidos de mesa (LOCAL)
        if (usuario.getRol() == Rol.MOZO && dto.getTipo() != TipoPedido.LOCAL) {
            throw new BusinessException("El rol MOZO solo puede registrar pedidos de tipo LOCAL (mesa)");
        }

        // Todo pedido tiene que poder identificarse a la hora de entregarlo.
        // En retiro y delivery hace falta el nombre; en el local alcanza con la mesa.
        boolean sinNombre = dto.getNombreCliente() == null || dto.getNombreCliente().isBlank();
        boolean sinMesa   = dto.getMesa() == null || dto.getMesa().isBlank();
        if (dto.getTipo() == TipoPedido.LOCAL) {
            if (sinNombre && sinMesa) {
                throw new BusinessException("Indicá la mesa o el nombre del cliente");
            }
        } else if (sinNombre) {
            throw new BusinessException("El nombre del cliente es obligatorio");
        }

        if (dto.getTipo() == TipoPedido.DELIVERY
                && (dto.getDireccionEntrega() == null || dto.getDireccionEntrega().isBlank())) {
            logger.warn("PEDIDO RECHAZADO: delivery sin dirección de entrega. Usuario={}", dto.getUsuarioId());
            throw new BusinessException("El delivery requiere una dirección de entrega");
        }

        Pedido pedido = Pedido.builder()
                .fecha(LocalDate.now())
                .hora(LocalTime.now())
                .estado(EstadoPedido.PENDIENTE)
                .tipo(dto.getTipo())
                .total(0.0)
                .usuario(usuario)
                .nombreCliente(dto.getNombreCliente())
                .telefonoCliente(dto.getTelefonoCliente())
                .direccionEntrega(dto.getDireccionEntrega())
                .horarioEntrega(dto.getHorarioEntrega())
                .mesa(dto.getMesa())
                .metodoPagoPreferido(dto.getMetodoPagoPreferido())
                .build();

        if (dto.getClienteId() != null) {
            Cliente cliente = clienteRepository.findById(dto.getClienteId())
                    .orElseThrow(() -> new ResourceNotFoundException("Cliente", dto.getClienteId()));
            pedido.setCliente(cliente);
        }

        Pedido pedidoGuardado = pedidoRepository.save(pedido);

        double total = 0.0;
        for (DetallePedidoRequestDTO detalleDTO : dto.getDetalles()) {
            Producto producto = productoRepository.findById(detalleDTO.getProductoId())
                    .orElseThrow(() -> new ResourceNotFoundException("Producto", detalleDTO.getProductoId()));

            if (!producto.getActivo()) {
                logger.warn("PRODUCTO INACTIVO en pedido: productoId={}, nombre='{}'",
                        producto.getId(), producto.getNombre());
                throw new BusinessException("El producto '" + producto.getNombre() + "' no está disponible");
            }

            // Precio según la variante elegida. Si pidieron la grande (ej: pizza
            // entera) y el producto tiene ese precio cargado, se cobra ese.
            // El precio SIEMPRE sale de la base, nunca de lo que mande el cliente.
            String variante = detalleDTO.getVariante();
            boolean esGrande = variante != null && variante.trim().equalsIgnoreCase("Entera");
            Double precioUnit;
            if (esGrande) {
                // Pidieron la entera: DEBE tener precio de entera cargado. Si no lo
                // tiene, rechazamos en vez de cobrar mal (antes cobraba la entera al
                // precio de la media, perdiendo plata sin avisar).
                if (producto.getPrecioEntera() == null || producto.getPrecioEntera() <= 0) {
                    throw new BusinessException(
                        "La pizza '" + producto.getNombre() + "' no tiene cargado el precio de la "
                        + "versión entera. Cargalo en el gestor de menú antes de venderla entera.");
                }
                precioUnit = producto.getPrecioEntera();
            } else {
                precioUnit = producto.getPrecio();
            }

            DetallePedido detalle = DetallePedido.builder()
                    .pedido(pedidoGuardado)
                    .producto(producto)
                    .cantidad(detalleDTO.getCantidad())
                    .variante(variante != null && !variante.isBlank() ? variante.trim() : null)
                    .precioUnitario(precioUnit)
                    .costoUnitario(producto.getCosto())
                    .subtotal(precioUnit * detalleDTO.getCantidad())
                    .build();

            pedidoGuardado.getDetalles().add(detalle);
            total += detalle.getSubtotal();
        }

        pedidoGuardado.setTotal(total);
        Pedido resultado = pedidoRepository.save(pedidoGuardado);
        logger.info("PEDIDO CREADO: id={}, total=${}, tipo={}", resultado.getId(), resultado.getTotal(), resultado.getTipo());
        return toDTO(resultado);
    }

    // Pedidos entregados desde el último cierre de caja. Igual en todos los
    // dispositivos, porque el filtro lo hace el servidor (no el navegador).
    public List<PedidoResponseDTO> entregadosDesdeCierre() {
        java.time.LocalDateTime desde;
        try {
            String c = configLocalService.obtener().getCierreCaja();
            desde = (c == null || c.isBlank())
                    ? java.time.LocalDate.now().atStartOfDay()
                    : java.time.LocalDateTime.parse(c);
        } catch (Exception e) {
            desde = java.time.LocalDate.now().atStartOfDay();
        }
        return pedidoRepository.findEntregadosDesde(desde).stream()
                .map(this::toDTO).collect(java.util.stream.Collectors.toList());
    }

    public PedidoResponseDTO cambiarEstado(Long id, EstadoPedido nuevoEstado) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido", id));

        EstadoPedido estadoAnterior = pedido.getEstado();
        validarTransicionEstado(estadoAnterior, nuevoEstado);

        pedido.setEstado(nuevoEstado);
        if (nuevoEstado == EstadoPedido.ENTREGADO && pedido.getEntregadoEn() == null) {
            pedido.setEntregadoEn(java.time.LocalDateTime.now());
        }
        logger.info("ESTADO PEDIDO: id={} -> {} -> {}", id, estadoAnterior, nuevoEstado);
        return toDTO(pedidoRepository.save(pedido));
    }

    public PedidoResponseDTO cancelar(Long id) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido", id));

        if (pedido.getEstado() == EstadoPedido.ENTREGADO) {
            throw new BusinessException("No se puede cancelar un pedido ya entregado");
        }
        if (pedido.getEstado() == EstadoPedido.CANCELADO) {
            throw new BusinessException("El pedido ya está cancelado");
        }
        // Se puede cancelar mientras no esté cobrado. Antes solo se permitía en
        // PENDIENTE, y un pedido cargado por error quedaba trabado para siempre.
        if (pedido.getEstado() != EstadoPedido.PENDIENTE
                && pedido.getEstado() != EstadoPedido.PREPARACION
                && pedido.getEstado() != EstadoPedido.LISTO) {
            throw new BusinessException("No se puede cancelar un pedido en estado: " + pedido.getEstado());
        }

        LocalDateTime creacion = LocalDateTime.of(pedido.getFecha(), pedido.getHora());
        LocalDateTime limite = creacion.plusMinutes(30);
        if (LocalDateTime.now().isAfter(limite)) {
            logger.warn("CANCELACIÓN TARDÍA: pedidoId={}, cliente='{}'", id, pedido.getNombreCliente());
            throw new BusinessException("El tiempo para cancelar este pedido ha vencido (30 minutos desde la creación)");
        }

        pedido.setEstado(EstadoPedido.CANCELADO);
        logger.info("PEDIDO CANCELADO: id={}, cliente='{}'", id, pedido.getNombreCliente());
        return toDTO(pedidoRepository.save(pedido));
    }

    public void eliminarEntregadosHoy() {
        List<Pedido> entregados = pedidoRepository.findByFecha(LocalDate.now())
                .stream()
                .filter(p -> p.getEstado() == EstadoPedido.ENTREGADO)
                .collect(Collectors.toList());

        logger.info("LIMPIEZA DIARIA: eliminando {} pedidos entregados de hoy", entregados.size());

        for (Pedido p : entregados) {
            ventaRepository.findByPedidoId(p.getId()).ifPresent(ventaRepository::delete);
            pedidoRepository.delete(p);
        }
    }

    public PedidoResponseDTO agregarDetalle(Long pedidoId, DetallePedidoRequestDTO detalleDTO) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido", pedidoId));

        if (pedido.getEstado() != EstadoPedido.PENDIENTE) {
            throw new BusinessException("Solo se pueden modificar pedidos en estado PENDIENTE");
        }

        Producto producto = productoRepository.findById(detalleDTO.getProductoId())
                .orElseThrow(() -> new ResourceNotFoundException("Producto", detalleDTO.getProductoId()));

        logger.info("AGREGAR DETALLE: pedidoId={}, productoId={}, cantidad={}",
                pedidoId, detalleDTO.getProductoId(), detalleDTO.getCantidad());

        pedido.getDetalles().stream()
                .filter(d -> d.getProducto() != null && d.getProducto().getId().equals(producto.getId()))
                .findFirst()
                .ifPresentOrElse(
                        detalle -> {
                            detalle.setCantidad(detalle.getCantidad() + detalleDTO.getCantidad());
                            detalle.setSubtotal(detalle.getPrecioUnitario() * detalle.getCantidad());
                        },
                        () -> {
                            DetallePedido nuevo = DetallePedido.builder()
                                    .pedido(pedido)
                                    .producto(producto)
                                    .cantidad(detalleDTO.getCantidad())
                                    .precioUnitario(producto.getPrecio())
                                    .subtotal(producto.getPrecio() * detalleDTO.getCantidad())
                                    .build();
                            pedido.getDetalles().add(nuevo);
                        }
                );

        recalcularTotal(pedido);
        return toDTO(pedidoRepository.save(pedido));
    }

    public PedidoResponseDTO eliminarDetalle(Long pedidoId, Long detalleId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido", pedidoId));

        if (pedido.getEstado() != EstadoPedido.PENDIENTE) {
            throw new BusinessException("Solo se pueden modificar pedidos en estado PENDIENTE");
        }

        pedido.getDetalles().removeIf(d -> d.getId().equals(detalleId));
        recalcularTotal(pedido);
        return toDTO(pedidoRepository.save(pedido));
    }

    private void recalcularTotal(Pedido pedido) {
        double total = pedido.getDetalles().stream().mapToDouble(DetallePedido::getSubtotal).sum();
        pedido.setTotal(total);
    }

    private void validarTransicionEstado(EstadoPedido actual, EstadoPedido nuevo) {
        boolean valido = switch (actual) {
            // Se puede avanzar al paso siguiente, y también marcar ENTREGADO desde
            // cualquier estado activo: cuando el cajero cobra, el pedido se entrega,
            // sin importar si venía de pendiente, preparación o listo.
            case PENDIENTE -> nuevo == EstadoPedido.PREPARACION
                    || nuevo == EstadoPedido.ENTREGADO
                    || nuevo == EstadoPedido.CANCELADO;
            case PREPARACION -> nuevo == EstadoPedido.LISTO
                    || nuevo == EstadoPedido.ENTREGADO
                    || nuevo == EstadoPedido.CANCELADO;
            case LISTO -> nuevo == EstadoPedido.ENTREGADO
                    || nuevo == EstadoPedido.CANCELADO;
            case ENTREGADO, CANCELADO -> false;
        };
        if (!valido) {
            logger.warn("TRANSICIÓN INVÁLIDA: {} -> {}", actual, nuevo);
            throw new BusinessException("Transición de estado inválida: " + actual + " -> " + nuevo);
        }
    }

    private boolean esCancelable(Pedido pedido) {
        if (pedido.getEstado() != EstadoPedido.PENDIENTE) return false;
        LocalDateTime creacion = LocalDateTime.of(pedido.getFecha(), pedido.getHora());
        return LocalDateTime.now().isBefore(creacion.plusMinutes(30));
    }

    public PedidoResponseDTO toDTO(Pedido p) {
        List<DetallePedidoResponseDTO> detalles = p.getDetalles().stream()
                .map(d -> DetallePedidoResponseDTO.builder()
                        .id(d.getId())
                        .cantidad(d.getCantidad())
                        .precioUnitario(d.getPrecioUnitario())
                        .subtotal(d.getSubtotal())
                        // Si el producto fue borrado del menú, el pedido histórico
                        // igual tiene que poder verse (cantidad, precio y subtotal
                        // quedaron guardados en el detalle).
                        .variante(d.getVariante())
                        .producto(d.getProducto() != null ? productoService.toDTO(d.getProducto()) : null)
                        .build())
                .collect(Collectors.toList());

        return PedidoResponseDTO.builder()
                .id(p.getId())
                .fecha(p.getFecha())
                .hora(p.getHora())
                .estado(p.getEstado())
                .tipo(p.getTipo())
                .total(p.getTotal())
                .nombreCliente(p.getNombreCliente())
                .telefonoCliente(p.getTelefonoCliente())
                .direccionEntrega(p.getDireccionEntrega())
                .horarioEntrega(p.getHorarioEntrega())
                .mesa(p.getMesa())
                .metodoPagoPreferido(p.getMetodoPagoPreferido())
                .cliente(p.getCliente() != null ? clienteService.toDTO(p.getCliente()) : null)
                .usuario(p.getUsuario() != null ? usuarioService.toDTO(p.getUsuario()) : null)
                .detalles(detalles)
                .cancelable(esCancelable(p))
                .build();
    }
}
