package com.barclub.repository;

import com.barclub.entity.CierreCaja;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CierreCajaRepository extends JpaRepository<CierreCaja, Long> {

    List<CierreCaja> findByFechaCierreBetweenOrderByFechaCierreAsc(LocalDateTime desde, LocalDateTime hasta);
}
