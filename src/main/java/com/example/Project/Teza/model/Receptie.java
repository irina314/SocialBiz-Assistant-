package com.example.Project.Teza.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@Table(name = "receptii")
public class Receptie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "furnizor_id", nullable = false)
    private Furnizor furnizor;

    // FACTURA sau AVIZ
    @Enumerated(EnumType.STRING)
    private TipDocument tipDocument = TipDocument.AVIZ;

    private String numarDocument;      // nr. aviz sau nr. factura
    private LocalDate dataReceptie;
    private String note;

    // Daca tipDocument == FACTURA, datoria se creeaza imediat
    // Daca tipDocument == AVIZ, datoria se creeaza cand se ataseaza factura
    private String numarFacturaAferenta;   // completat ulterior pt avize
    private LocalDate dataFacturaAferenta;
    private LocalDate dataScadentaFactura;

    @Enumerated(EnumType.STRING)
    private StatusFacturare statusFacturare = StatusFacturare.NEFACTURATA;

    @OneToMany(mappedBy = "receptie", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReceptieItem> items = new ArrayList<>();

    private LocalDateTime dataInregistrare;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilizator_id")
    private Utilizator utilizator;

    @PrePersist
    public void preSave() {
        if (this.dataInregistrare == null) this.dataInregistrare = LocalDateTime.now();
        if (this.dataReceptie == null) this.dataReceptie = LocalDate.now();
        if (this.tipDocument == TipDocument.FACTURA) {
            this.statusFacturare = StatusFacturare.FACTURATA;
        }
    }

    public double calculeazaTotal() {
        return items.stream()
                .mapToDouble(i -> i.getCantitate() * i.getPretAchizitie())
                .sum();
    }

    public enum TipDocument {
        FACTURA("Factura"),
        AVIZ("Aviz de insotire");

        private final String label;
        TipDocument(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public enum StatusFacturare {
        NEFACTURATA,   // aviz primit, factura inca nu a venit
        FACTURATA      // factura a fost primita si inregistrata
    }
}