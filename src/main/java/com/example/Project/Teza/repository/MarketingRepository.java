package com.example.Project.Teza.repository;

import com.example.Project.Teza.model.CampanieMktg;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MarketingRepository extends JpaRepository<CampanieMktg, Long> {
    List<CampanieMktg> findByUtilizatorId(Long utilizatorId);
    List<CampanieMktg> findByUtilizatorIdAndStatus(Long utilizatorId, CampanieMktg.Status status);
}