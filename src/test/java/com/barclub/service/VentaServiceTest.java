package com.barclub.service;

import com.barclub.dto.VentaRequestDTO;
import com.barclub.dto.VentaResponseDTO;
import com.barclub.entity.*;
import com.barclub.exception.BusinessException;
import com.barclub.repository.CierreCajaRepository;
import com.barclub.repository.PedidoRepository;
import com.barclub.repository.VentaRepository;
import com.barclub.websocket.RealtimeNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Cobertura del flujo de cobro (VentaService), que hasta ahora no tenía
 * ningún test: es el camino que maneja plata real, así que las reglas de
 * negocio de acá (no cobrar con la caja cerrada, no cobrar dos veces el
 * mismo pedido, no cobrar un pedido ya cerrado) son las que más conviene
 * dejar cubiertas para no romperlas sin querer en un cambio futuro.
 */
@ExtendWith(MockitoExtension.class)
class VentaServiceTest {

    @Mock private VentaRepository ventaRepository;
    @Mock private PedidoRepository pedidoRepository;
    @Mock private ConfigLocalService configLocalService;
    @Mock private RealtimeNotifier realtimeNotifier;
    @Mock private CierreCajaRepository cierreCajaRepository;

    @InjectMocks
    private VentaService ventaService;

    private Pedido pedidoMock;
    private ConfigLocal configAbierta;

    @BeforeEach
    void setUp() {
        pedidoMock = Pedido.builder()
                .id(1L)
                .fecha(LocalDate.now())
                .hora(LocalTime.now())
                .estado(EstadoPedido.LISTO)
                .tipo(TipoPedido.LOCAL)
                .total(5000.0)
                .nombreCliente("Cliente Test")
                .build();

        configAbierta = ConfigLocal.builder().cajaAbierta(true).build();
    }

    // -------------------------------------------------------
    // TEST 1: Camino feliz — cobrar un pedido válido
    // -------------------------------------------------------
    @Test
    void registrar_pedidoValido_debeCrearVentaYMarcarEntregado() {
        VentaRequestDTO dto = new VentaRequestDTO();
        dto.setPedidoId(1L);
        dto.setMetodoPago(MetodoPago.EFECTIVO);

        Venta ventaGuardada = Venta.builder()
                .id(10L).fecha(LocalDate.now()).hora(LocalTime.now())
                .total(5000.0).metodoPago(MetodoPago.EFECTIVO).pedido(pedidoMock)
                .build();

        when(configLocalService.obtener()).thenReturn(configAbierta);
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedidoMock));
        when(ventaRepository.findByPedidoId(1L)).thenReturn(Optional.empty());
        when(ventaRepository.save(any(Venta.class))).thenReturn(ventaGuardada);

        VentaResponseDTO resultado = ventaService.registrar(dto);

        assertNotNull(resultado);
        assertEquals(10L, resultado.getId());
        assertEquals(EstadoPedido.ENTREGADO, pedidoMock.getEstado());
        verify(pedidoRepository).save(pedidoMock);
        verify(ventaRepository).save(any(Venta.class));
    }

    // -------------------------------------------------------
    // TEST 2: Caja cerrada — no debe permitir cobrar
    // -------------------------------------------------------
    @Test
    void registrar_cajaCerrada_debeLanzarBusinessException() {
        VentaRequestDTO dto = new VentaRequestDTO();
        dto.setPedidoId(1L);
        dto.setMetodoPago(MetodoPago.EFECTIVO);

        ConfigLocal configCerrada = ConfigLocal.builder().cajaAbierta(false).build();
        when(configLocalService.obtener()).thenReturn(configCerrada);

        assertThrows(BusinessException.class, () -> ventaService.registrar(dto));
        verifyNoInteractions(ventaRepository);
    }

    // -------------------------------------------------------
    // TEST 3: Pedido que ya tiene una venta registrada — no cobrar dos veces
    // -------------------------------------------------------
    @Test
    void registrar_pedidoYaCobrado_debeLanzarBusinessException() {
        VentaRequestDTO dto = new VentaRequestDTO();
        dto.setPedidoId(1L);
        dto.setMetodoPago(MetodoPago.EFECTIVO);

        Venta ventaExistente = Venta.builder().id(99L).pedido(pedidoMock).build();

        when(configLocalService.obtener()).thenReturn(configAbierta);
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedidoMock));
        when(ventaRepository.findByPedidoId(1L)).thenReturn(Optional.of(ventaExistente));

        assertThrows(BusinessException.class, () -> ventaService.registrar(dto));
        verify(ventaRepository, never()).save(any(Venta.class));
    }

    // -------------------------------------------------------
    // TEST 4: Pedido ya entregado — no se puede volver a cobrar
    // -------------------------------------------------------
    @Test
    void registrar_pedidoYaEntregado_debeLanzarBusinessException() {
        pedidoMock.setEstado(EstadoPedido.ENTREGADO);

        VentaRequestDTO dto = new VentaRequestDTO();
        dto.setPedidoId(1L);
        dto.setMetodoPago(MetodoPago.EFECTIVO);

        when(configLocalService.obtener()).thenReturn(configAbierta);
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedidoMock));

        assertThrows(BusinessException.class, () -> ventaService.registrar(dto));
    }

    // -------------------------------------------------------
    // TEST 5: Pedido cancelado — tampoco se puede cobrar
    // -------------------------------------------------------
    @Test
    void registrar_pedidoCancelado_debeLanzarBusinessException() {
        pedidoMock.setEstado(EstadoPedido.CANCELADO);

        VentaRequestDTO dto = new VentaRequestDTO();
        dto.setPedidoId(1L);
        dto.setMetodoPago(MetodoPago.TARJETA);

        when(configLocalService.obtener()).thenReturn(configAbierta);
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedidoMock));

        assertThrows(BusinessException.class, () -> ventaService.registrar(dto));
    }

    // -------------------------------------------------------
    // TEST 6: Pedido inexistente — 404 (ResourceNotFoundException)
    // -------------------------------------------------------
    @Test
    void registrar_pedidoInexistente_debeLanzarResourceNotFoundException() {
        VentaRequestDTO dto = new VentaRequestDTO();
        dto.setPedidoId(999L);
        dto.setMetodoPago(MetodoPago.EFECTIVO);

        when(configLocalService.obtener()).thenReturn(configAbierta);
        when(pedidoRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(com.barclub.exception.ResourceNotFoundException.class,
                () -> ventaService.registrar(dto));
    }

    // -------------------------------------------------------
    // TEST 7: calcularJornada — una caja abierta a la noche que sigue
    // corriendo pasada la medianoche cuenta para la noche en que abrió,
    // no para el día calendario nuevo.
    // -------------------------------------------------------
    @Test
    void calcularJornada_ventaDeMadrugadaConCajaAbiertaAnoche_debeContarParaAyer() {
        LocalDateTime apertura = LocalDateTime.of(2026, 8, 20, 22, 0);
        LocalDateTime venta = LocalDateTime.of(2026, 8, 21, 2, 30);

        LocalDate jornada = ventaService.calcularJornada(venta, apertura);

        assertEquals(LocalDate.of(2026, 8, 20), jornada);
    }

    // -------------------------------------------------------
    // TEST 8: calcularJornada — una caja olvidada abierta más de 24hs ya
    // no debe seguir arrastrando ventas nuevas al día en que abrió.
    // -------------------------------------------------------
    @Test
    void calcularJornada_cajaOlvidadaMasDe24Horas_debeUsarElDiaReal() {
        LocalDateTime apertura = LocalDateTime.of(2026, 8, 18, 20, 0);
        LocalDateTime venta = LocalDateTime.of(2026, 8, 20, 10, 0); // > 24hs después

        LocalDate jornada = ventaService.calcularJornada(venta, apertura);

        assertEquals(LocalDate.of(2026, 8, 20), jornada);
    }

    // -------------------------------------------------------
    // TEST 9: calcularJornada — sin ninguna caja registrada, usa el día real.
    // -------------------------------------------------------
    @Test
    void calcularJornada_sinAperturaRegistrada_debeUsarElDiaReal() {
        LocalDateTime venta = LocalDateTime.of(2026, 8, 21, 15, 0);

        LocalDate jornada = ventaService.calcularJornada(venta, null);

        assertEquals(LocalDate.of(2026, 8, 21), jornada);
    }
}
