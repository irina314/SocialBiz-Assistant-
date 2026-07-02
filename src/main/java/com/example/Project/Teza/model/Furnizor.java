package com.example.Project.Teza.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "furnizori")
public class Furnizor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nume;
    private String persoanaContact;
    private String telefon;
    private String email;
    private String adresa;
    private String cui;           // Cod Unic de Identificare
    private String nrRegCom;      // Nr. Registrul Comertului
    private String contBancar;
    private String banca;
    private String note;

    @Enumerated(EnumType.STRING)
    private Status status = Status.ACTIV;

    @Enumerated(EnumType.STRING)
    private Categorie categorie;

    private LocalDateTime dataAdaugare;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilizator_id")
    private Utilizator utilizator;

    @PrePersist
    public void preSave() {
        if (this.dataAdaugare == null) this.dataAdaugare = LocalDateTime.now();
        if (this.status == null) this.status = Status.ACTIV;
    }

    public enum Status {
        ACTIV, INACTIV
    }

    public enum Categorie {
        MARFURI("Marfuri"),
        AMBALAJE("Ambalaje"),
        SERVICII("Servicii"),
        TRANSPORT("Transport"),
        PRODUCTIE("Productie"),
        ALTELE("Altele");

        private final String label;
        Categorie(String label) { this.label = label; }
        public String getLabel() { return label; }
    }
}