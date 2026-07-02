package com.example.Project.Teza.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "comanda_items")
public class ComandaItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "comanda_id")
    private Comanda comanda;

    private String idProdus;
    private Integer cantitate = 1;
    private Double pretUnitar;

    // Pret unitar INAINTE de reducerea campaniei (null daca nu s-a aplicat nicio reducere).
    // pretUnitar ramane mereu pretul FINAL folosit la calcul; acesta e doar pentru afisare ("pret taiat").
    private Double pretUnitarOriginal;

    @Enumerated(EnumType.STRING)
    private CotaTVA cotaTVA;

    public enum CotaTVA {
        TVA_0(0.0),
        TVA_9(0.09),
        TVA_11(0.11),
        TVA_21(0.21);

        private final double valoare;
        CotaTVA(double valoare) { this.valoare = valoare; }
        public double getValoare() { return valoare; }
    }

    public double calculeazaTotal() {
        if (pretUnitar == null || cantitate == null) return 0;
        double tva = cotaTVA != null ? cotaTVA.getValoare() : 0;
        return cantitate * pretUnitar * (1 + tva);
    }
}