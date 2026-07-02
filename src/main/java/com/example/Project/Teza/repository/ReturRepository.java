package com.example.Project.Teza.repository;

import com.example.Project.Teza.model.Retur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ReturRepository extends JpaRepository<Retur, Long> {
    List<Retur> findByUtilizatorId(Long utilizatorId);
    List<Retur> findByUtilizatorIdAndStatus(Long utilizatorId, Retur.StatusRetur status);
}