package com.example.Project.Teza.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "influenceri")
public class Influencer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nume;
    private String platforma;       // Instagram, TikTok, etc.
    private String username;        // @handle
    private String codPromotional;  // codul unic de tracking - leaga comenzile
    private String telefon;
    private String email;
    private Double comisionProcent; // % din vanzari (optional)
    private String note;

    @Enumerated(EnumType.STRING)
    private Status status = Status.ACTIV;

    private LocalDateTime dataAdaugare;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilizator_id")
    private Utilizator utilizator;

    @PrePersist
    public void preSave() {
        if (this.dataAdaugare == null) this.dataAdaugare = LocalDateTime.now();
        if (this.status == null) this.status = Status.ACTIV;
    }

    public enum Status { ACTIV, INACTIV }
}