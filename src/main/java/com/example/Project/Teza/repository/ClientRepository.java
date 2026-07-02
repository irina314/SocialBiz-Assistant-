package com.example.Project.Teza.repository;

import com.example.Project.Teza.model.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {
    List<Client> findByUtilizatorId(Long utilizatorId);
    List<Client> findByUtilizatorIdAndDataAdaugareAfter(Long utilizatorId, LocalDateTime data);
    Optional<Client> findByTelefonAndUtilizatorId(String telefon, Long utilizatorId);
}