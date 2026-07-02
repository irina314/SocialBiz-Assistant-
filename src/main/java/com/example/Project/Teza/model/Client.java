package com.example.Project.Teza.model;

import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Data
@Table(name = "clienti", indexes = {
        @Index(name = "idx_clienti_utilizator", columnList = "utilizator_id"),
        @Index(name = "idx_clienti_telefon",   columnList = "telefon")
})
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nume;
    private String telefon;
    private String adresa;

    private LocalDateTime dataAdaugare;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilizator_id")
    private Utilizator utilizator;

    @PrePersist
    public void preSave() {
        if (this.dataAdaugare == null) this.dataAdaugare = LocalDateTime.now();
    }
}