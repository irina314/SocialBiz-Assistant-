package com.example.Project.Teza.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@Table(name = "campanii_marketing")
public class CampanieMktg {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nume;

    @Enumerated(EnumType.STRING)
    private Platforma platforma;

    private Double bugetCheltuit = 0.0;
    private LocalDate dataStart;
    private LocalDate dataStop;
    private String codPromo;
    private String note;

    @Enumerated(EnumType.STRING)
    private Status status;

    // -- Reducere asociata codului promo (optional) -------------------------
    // Daca areReducere=false, codPromo e doar un cod de tracking, fara beneficiu pentru client.
    private Boolean areReducere = false;

    @Enumerated(EnumType.STRING)
    private TipReducere tipReducere;

    // Pentru TipReducere.PROCENT: valoare 0-100. Pentru SUMA_FIXA: valoare in RON / bucata.
    private Double valoareReducere;

    @Enumerated(EnumType.STRING)
    private AplicabilitateReducere aplicabilitate = AplicabilitateReducere.TOATE_PRODUSELE;

    // Codurile SKU ale produselor eligibile, folosit doar cand aplicabilitate=PRODUSE_SELECTATE.
    @ElementCollection
    @CollectionTable(name = "campanie_produse_eligibile", joinColumns = @JoinColumn(name = "campanie_id"))
    @Column(name = "cod_sku")
    private List<String> produseEligibile = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilizator_id")
    private Utilizator utilizator;

    @PrePersist
    public void preSave() {
        if (this.status == null) this.status = Status.ACTIVA;
        if (this.bugetCheltuit == null) this.bugetCheltuit = 0.0;
        if (this.areReducere == null) this.areReducere = false;
        if (this.aplicabilitate == null) this.aplicabilitate = AplicabilitateReducere.TOATE_PRODUSELE;
    }

    /** Verifica daca reducerea acestei campanii se aplica unui produs anume (dupa codSKU). */
    public boolean reducereSeAplicaPe(String codSKU) {
        if (areReducere == null || !areReducere) return false;
        if (aplicabilitate == AplicabilitateReducere.TOATE_PRODUSELE) return true;
        return codSKU != null && produseEligibile != null && produseEligibile.contains(codSKU);
    }

    public enum Platforma {
        Instagram, Facebook, TikTok, Google, YouTube, Organic, Altele
    }

    public enum Status {
        ACTIVA, INCHEIATA, PROGRAMATA
    }

    public enum TipReducere {
        PROCENT("%"), SUMA_FIXA("RON / buc");

        public final String unitate;
        TipReducere(String unitate) { this.unitate = unitate; }
    }

    public enum AplicabilitateReducere {
        TOATE_PRODUSELE, PRODUSE_SELECTATE
    }
}