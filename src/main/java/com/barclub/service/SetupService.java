package com.barclub.service;

import com.barclub.exception.BusinessException;
import com.barclub.repository.ClienteRepository;
import com.barclub.repository.PedidoRepository;
import com.barclub.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Utilidades para dejar lista una instalación nueva antes de entregarla a un
 * cliente real. Cada instalación de este sistema arranca con un menú y
 * clientes de ejemplo (ver DataInitializer), pensados para poder probar la
 * app apenas se levanta el backend por primera vez. Este servicio borra ese
 * contenido de ejemplo de una sola vez, en vez de tener que hacerlo a mano
 * producto por producto desde Gestor de Menú.
 */
@Service
@RequiredArgsConstructor
public class SetupService {

    private final ProductoRepository productoRepository;
    private final ClienteRepository clienteRepository;
    private final PedidoRepository pedidoRepository;

    @Transactional
    public Map<String, Object> borrarDatosDeEjemplo(boolean confirmar) {
        if (!confirmar) {
            throw new BusinessException("Confirmá la operación (confirmar=true) antes de borrar los datos de ejemplo.");
        }
        // Salvaguarda: si ya hay pedidos cargados, esto ya no es una instalación
        // nueva — borrar productos/clientes referenciados por pedidos reales
        // rompería esos pedidos (o directamente fallaría por la FK de la base).
        if (pedidoRepository.count() > 0) {
            throw new BusinessException("Ya hay pedidos cargados en esta instalación: esto no es una instalación " +
                    "nueva y borrar el menú de ejemplo ahora podría romper pedidos reales. Editá el menú a mano " +
                    "desde Gestor de Menú en su lugar.");
        }
        long productos = productoRepository.count();
        long clientes = clienteRepository.count();
        productoRepository.deleteAll();
        clienteRepository.deleteAll();
        return Map.of(
                "productosBorrados", productos,
                "clientesBorrados", clientes
        );
    }
}
