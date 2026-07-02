package com.example.Project.Teza.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.BatchSize;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@Table(name = "comenzi", indexes = {
        @Index(name = "idx_comenzi_utilizator", columnList = "utilizator_id"),
        @Index(name = "idx_comenzi_status",     columnList = "status"),
        @Index(name = "idx_comenzi_data",       columnList = "data")
})
public class Comanda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String numeClient;
    private String telefon;
    private String adresa;
    private String codPostal;
    private LocalDateTime data;

    private String sursa;
    private String codPromotional;
    private String campanie;
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private StatusComanda status;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private StatusPlata statusPlata;

    private String metodaPlata;
    private LocalDateTime dataConfirmarePlata;
    private Double sumaIncasata;   // suma efectiv incasata (poate fi avans partial)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilizator_id")
    private Utilizator utilizator;

    // BatchSize rezolva N+1: in loc de 1 SELECT per comanda,
    // face SELECT WHERE comanda_id IN (1,2,3,...20)
    @OneToMany(mappedBy = "comanda", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 20)
    private List<ComandaItem> items = new ArrayList<>();

    @PrePersist
    public void preSave() {
        if (this.data == null) this.data = LocalDateTime.now();
        if (this.status == null) this.status = StatusComanda.NOU;
        if (this.statusPlata == null) this.statusPlata = StatusPlata.RAMBURS;
    }

    public double calculeazaTotal() {
        if (items == null || items.isEmpty()) return 0;
        return items.stream()
                .mapToDouble(ComandaItem::calculeazaTotal)
                .sum();
    }

    public boolean esteAchitata() {
        return this.statusPlata == StatusPlata.PLATIT_ONLINE
                || this.statusPlata == StatusPlata.PLATIT_RAMBURS
                || this.statusPlata == StatusPlata.PLATIT_CU_CARDUL;
    }

    public boolean areAvans() {
        return this.statusPlata == StatusPlata.AVANS_PARTIAL;
    }

    public enum StatusComanda {
        NOU(0, "[N] Nou", "#185FA5", "#e6f1fb"),
        CONFIRMAT(1, "[OK] Confirmat", "#1D9E75", "#e6f5ef"),
        EXPEDIAT(2, "[E] Expediat", "#854F0B", "#faeeda"),
        LIVRAT(3, "[C] Livrat", "#3B6D11", "#eaf3de"),
        FINALIZAT(4, "[L] Finalizat", "#6c47ff", "#f0ebff"),
        ANULAT(-1, "[X] Anulat", "#A32D2D", "#fcebeb");

        public final int ordine;
        public final String label;
        public final String culoare;
        public final String background;

        StatusComanda(int ordine, String label, String culoare, String background) {
            this.ordine = ordine;
            this.label = label;
            this.culoare = culoare;
            this.background = background;
        }

        public boolean poateAvansaSpre(StatusComanda urmator) {
            if (this == ANULAT || this == FINALIZAT) return false;
            if (this == LIVRAT && urmator == ANULAT) return false;
            if (urmator == ANULAT) return this.ordine <= CONFIRMAT.ordine;
            return urmator.ordine == this.ordine + 1;
        }

        public List<StatusComanda> paginaUrmatoare() {
            List<StatusComanda> result = new java.util.ArrayList<>();
            for (StatusComanda s : values()) {
                if (this.poateAvansaSpre(s)) result.add(s);
            }
            return result;
        }
    }

    public enum StatusPlata {
        RAMBURS, PLATIT_ONLINE, PLATIT_RAMBURS, PLATIT_CU_CARDUL, AVANS_PARTIAL, ANULAT
    }
}