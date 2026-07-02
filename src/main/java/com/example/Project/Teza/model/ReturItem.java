package com.example.Project.Teza.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "retur_items")
public class ReturItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "retur_id")
    private Retur retur;

    private String idProdus;
    private Integer cantitate = 1;
    private Double pretUnitar;

    @Enumerated(EnumType.STRING)
    private ComandaItem.CotaTVA cotaTVA;

    public double calculeazaTotal() {
        if (pretUnitar == null || cantitate == null) return 0;
        double tva = cotaTVA != null ? cotaTVA.getValoare() : 0;
        return cantitate * pretUnitar * (1 + tva);
    }
}