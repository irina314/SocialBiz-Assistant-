package com.example.Project.Teza.repository;

import com.example.Project.Teza.model.Furnizor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface FurnizorRepository extends JpaRepository<Furnizor, Long> {
    List<Furnizor> findByUtilizatorId(Long utilizatorId);
    List<Furnizor> findByUtilizatorIdAndStatus(Long utilizatorId, Furnizor.Status status);
    List<Furnizor> findByUtilizatorIdAndNumeContainingIgnoreCase(Long utilizatorId, String nume);
}