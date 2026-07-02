package com.example.Project.Teza.repository;

import com.example.Project.Teza.model.Influencer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface InfluencerRepository extends JpaRepository<Influencer, Long> {
    List<Influencer> findByUtilizatorId(Long utilizatorId);
    Optional<Influencer> findByCodPromotionalIgnoreCaseAndUtilizatorId(String cod, Long uid);
}