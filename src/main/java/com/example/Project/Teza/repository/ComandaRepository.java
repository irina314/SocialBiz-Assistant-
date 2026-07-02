package com.example.Project.Teza.repository;

import com.example.Project.Teza.model.Comanda;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ComandaRepository extends JpaRepository<Comanda, Long> {

    @EntityGraph(attributePaths = {"items"})
    List<Comanda> findByUtilizatorId(Long utilizatorId);

    @EntityGraph(attributePaths = {"items"})
    List<Comanda> findByUtilizatorIdAndStatus(Long utilizatorId, Comanda.StatusComanda status);

    List<Comanda> findByStatus(Comanda.StatusComanda status);
    List<Comanda> findByNumeClientContainingIgnoreCase(String nume);
    List<Comanda> findByTelefon(String telefon);
}