package com.example.Project.Teza.repository;

import com.example.Project.Teza.model.Receptie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ReceptieRepository extends JpaRepository<Receptie, Long> {
    List<Receptie> findByUtilizatorIdOrderByDataInregistrareDesc(Long utilizatorId);
    List<Receptie> findByFurnizorIdAndUtilizatorId(Long furnizorId, Long utilizatorId);
    // Receptii pe aviz fara factura inca
    List<Receptie> findByUtilizatorIdAndTipDocumentAndStatusFacturare(
            Long utilizatorId,
            Receptie.TipDocument tipDocument,
            Receptie.StatusFacturare statusFacturare);
}