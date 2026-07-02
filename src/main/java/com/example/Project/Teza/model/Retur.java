package com.example.Project.Teza.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@Table(name = "retururi")
public class Retur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String numeClient;
    private String telefon;
    private String nrComanda;
    private String motivRetur;

    /** Costul logistic al procesării returului (transport retur, manipulare etc.) - scazut din venit la calculul ROAS real. */
    private Double costLogisticRetur = 0.0;

    // Legatura cu comanda originala (pentru a extrage sursa si codul promo)
    private Long comandaId;
    private String sursaComanda;        // preluata din Comanda - pentru impact ROAS per canal
    private String codPromotionalComanda; // pentru impact ROAS per influencer

    @OneToMany(mappedBy = "retur", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReturItem> items = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    private StatusRetur status;

    private LocalDateTime dataCreare;
    private LocalDateTime dataRezolvare; // cand a fost aprobat/respins

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilizator_id")
    private Utilizator utilizator;

    @PrePersist
    public void preSave() {
        this.dataCreare = LocalDateTime.now();
        if (this.status == null) this.status = StatusRetur.IN_ASTEPTARE;
    }

    public double calculeazaTotal() {
        return items.stream().mapToDouble(ReturItem::calculeazaTotal).sum();
    }

    public enum StatusRetur {
        IN_ASTEPTARE, APROBAT, RESPINS
    }
}