package com.example.Project.Teza.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "facturi_furnizori")
public class FacturaFurnizor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "furnizor_id", nullable = false)
    private Furnizor furnizor;

    private String numarFactura;
    private LocalDate dataEmitere;
    private LocalDate dataScadenta;

    private Double totalFactura;
    private Double sumaPlatita = 0.0;

    @Enumerated(EnumType.STRING)
    private StatusFactura status = StatusFactura.NEPLATITA;

    private String descriere;
    private String note;

    // Campuri noi pentru confirmarea platii
    private String metodaPlata;        // TRANSFER / NUMERAR / CARD / CEC / BILET_ORDIN
    private String documentPlata;      // nr. ordin de plata, chitanta, etc.
    private LocalDate dataPlata;

    private LocalDateTime dataInregistrare;

    @Enumerated(EnumType.STRING)
    private TipFactura tip = TipFactura.MANUALA;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilizator_id")
    private Utilizator utilizator;

    @PrePersist
    public void preSave() {
        if (this.dataInregistrare == null) this.dataInregistrare = LocalDateTime.now();
        if (this.sumaPlatita == null) this.sumaPlatita = 0.0;
        if (this.status == null) this.status = StatusFactura.NEPLATITA;
        if (this.tip == null) this.tip = TipFactura.MANUALA;
    }

    public double getRest() {
        if (totalFactura == null) return 0.0;
        return Math.max(0, totalFactura - (sumaPlatita != null ? sumaPlatita : 0.0));
    }

    public boolean esteScadenta() {
        return status != StatusFactura.PLATITA
                && dataScadenta != null
                && dataScadenta.isBefore(LocalDate.now());
    }

    public void recalculeazaStatus() {
        if (totalFactura == null || totalFactura == 0) return;
        double platit = sumaPlatita != null ? sumaPlatita : 0.0;
        if (platit <= 0) {
            this.status = StatusFactura.NEPLATITA;
        } else if (platit >= totalFactura) {
            this.status = StatusFactura.PLATITA;
        } else {
            this.status = StatusFactura.PLATITA_PARTIAL;
        }
    }

    public enum StatusFactura { NEPLATITA, PLATITA_PARTIAL, PLATITA }
    public enum TipFactura { MANUALA, DIN_RECEPTIE }
}