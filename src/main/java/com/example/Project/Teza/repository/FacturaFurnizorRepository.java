package com.example.Project.Teza.repository;

import com.example.Project.Teza.model.FacturaFurnizor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface FacturaFurnizorRepository extends JpaRepository<FacturaFurnizor, Long> {

    List<FacturaFurnizor> findByUtilizatorId(Long utilizatorId);

    List<FacturaFurnizor> findByFurnizorIdAndUtilizatorId(Long furnizorId, Long utilizatorId);

    List<FacturaFurnizor> findByUtilizatorIdAndStatus(
            Long utilizatorId, FacturaFurnizor.StatusFactura status);

    // Returneaza direct furnizorId fara lazy load pe entitatea Furnizor
    // Folosit in controller inainte de confirmaPlata() pentru a evita LazyInitializationException
    @Query("SELECT f.furnizor.id FROM FacturaFurnizor f WHERE f.id = :facturaId")
    Long findFurnizorIdByFacturaId(Long facturaId);

    // Suma totala datorata (toate facturile neplatite sau partial platite)
    @Query("SELECT COALESCE(SUM(f.totalFactura - f.sumaPlatita), 0) " +
            "FROM FacturaFurnizor f " +
            "WHERE f.utilizator.id = :utilizatorId " +
            "AND f.status <> 'PLATITA'")
    Double totalDatorii(Long utilizatorId);

    // Suma datorata unui furnizor specific
    @Query("SELECT COALESCE(SUM(f.totalFactura - f.sumaPlatita), 0) " +
            "FROM FacturaFurnizor f " +
            "WHERE f.furnizor.id = :furnizorId " +
            "AND f.utilizator.id = :utilizatorId " +
            "AND f.status <> 'PLATITA'")
    Double totalDatoriiCatreFurnizor(Long furnizorId, Long utilizatorId);
}