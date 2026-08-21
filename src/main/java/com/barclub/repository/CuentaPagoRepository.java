package com.barclub.repository;

import com.barclub.entity.CuentaPago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CuentaPagoRepository extends JpaRepository<CuentaPago, Long> {
    List<CuentaPago> findByActivaTrueOrderByIdAsc();
}
