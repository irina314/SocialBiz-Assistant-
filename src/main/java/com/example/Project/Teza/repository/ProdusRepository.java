package com.example.Project.Teza.repository;

import com.example.Project.Teza.model.Produs;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProdusRepository extends JpaRepository<Produs, Long> {
    List<Produs> findByUtilizatorId(Long utilizatorId);
    List<Produs> findByUtilizatorIdAndNumeContainingIgnoreCase(Long utilizatorId, String nume);
    List<Produs> findByCategorie(Produs.Categorie categorie);
    List<Produs> findByNumeContainingIgnoreCase(String nume);
    List<Produs> findByCodSKU(String codSKU);
    List<Produs> findByStocLessThanEqual(Integer stoc);

    // Produsele unui furnizor specific
    List<Produs> findByUtilizatorIdAndFurnizorId(Long utilizatorId, Long furnizorId);

    // C3 - verifica daca un SKU exista deja pentru un utilizator dat
    // Folosit pentru a preveni duplicate inainte de salvare
    boolean existsByCodSKUIgnoreCaseAndUtilizatorId(String codSKU, Long utilizatorId);

    // Cauta un produs dupa SKU exact + utilizator (folosit si in validari de editare)
    Optional<Produs> findByCodSKUIgnoreCaseAndUtilizatorId(String codSKU, Long utilizatorId);
}
