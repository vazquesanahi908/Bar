package com.barclub.service;

import com.barclub.entity.CuentaPago;
import com.barclub.exception.BusinessException;
import com.barclub.exception.ResourceNotFoundException;
import com.barclub.repository.CuentaPagoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CuentaPagoService {

    private final CuentaPagoRepository repo;

    @Transactional(readOnly = true)
    public List<CuentaPago> listarTodas() {
        return repo.findAll();
    }

    // Lo que ve la web pública: solo las que el dueño dejó activas.
    @Transactional(readOnly = true)
    public List<CuentaPago> listarActivas() {
        return repo.findByActivaTrueOrderByIdAsc();
    }

    public CuentaPago crear(CuentaPago datos) {
        validar(datos);
        datos.setId(null);
        if (datos.getActiva() == null) datos.setActiva(true);
        return repo.save(datos);
    }

    public CuentaPago actualizar(Long id, CuentaPago datos) {
        CuentaPago actual = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cuenta de pago", id));
        if (datos.getEtiqueta() != null && !datos.getEtiqueta().isBlank()) actual.setEtiqueta(datos.getEtiqueta());
        if (datos.getAlias() != null && !datos.getAlias().isBlank()) actual.setAlias(datos.getAlias());
        if (datos.getActiva() != null) actual.setActiva(datos.getActiva());
        return repo.save(actual);
    }

    public void eliminar(Long id) {
        if (!repo.existsById(id)) throw new ResourceNotFoundException("Cuenta de pago", id);
        repo.deleteById(id);
    }

    private void validar(CuentaPago datos) {
        if (datos.getEtiqueta() == null || datos.getEtiqueta().isBlank()) {
            throw new BusinessException("Ponele un nombre a la cuenta (ej: Mercado Pago, Uala, Banco).");
        }
        if (datos.getAlias() == null || datos.getAlias().isBlank()) {
            throw new BusinessException("Cargá el alias, CBU o dato para transferir a esa cuenta.");
        }
    }
}
