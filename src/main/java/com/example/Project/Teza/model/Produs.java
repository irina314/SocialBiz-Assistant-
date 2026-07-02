package com.example.Project.Teza.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "produse", indexes = {
        @Index(name = "idx_produse_utilizator", columnList = "utilizator_id"),
        @Index(name = "idx_produse_sku", columnList = "codsku"),
        @Index(name = "idx_produse_categorie",  columnList = "categorie")
})
public class Produs {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nume;
    private String codSKU;
    private String descriere;

    // Relatie reala cu Furnizor in loc de String simplu
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "furnizor_id")
    private Furnizor furnizor;

    @Enumerated(EnumType.STRING)
    private Categorie categorie;

    @Enumerated(EnumType.STRING)
    private UnitateMasura unitateMasura = UnitateMasura.BUC;

    private Double pret;
    private Integer stoc;
    private Integer stocMinim = 5;

    // ERP: produsele nu se sterg niciodata - se dezactiveaza
    // Un produs inactiv nu apare in comenzi/smart input dar ramane in istoric
    private Boolean activ = true;

    public boolean esteActiv() {
        return activ == null || activ;
    }

    public boolean esteStocCritic() {
        return stoc != null && stocMinim != null && stoc <= stocMinim;
    }

    // Helper pentru Thymeleaf - returneaza numele furnizorului sau "-"
    public String getFurnizorNume() {
        return furnizor != null ? furnizor.getNume() : "-";
    }

    public enum Categorie {
        ALIMENTE_BAUTURI          ("Alimente & Bauturi"),
        COSMETICE_INGRIJIRE       ("Cosmetice & Ingrijire"),
        HAINE_INCALTAMINTE        ("Moda & Haine"),
        ACCESORII_BIJUTERII       ("Accesorii & Bijuterii"),
        ELECTRONICE_IT            ("Electronice & IT"),
        CASA_GRADINA              ("Casa & Gradina"),
        SPORT_SANATATE            ("Sport & Sanatate"),
        HANDMADE_ARTIZANAT        ("Handmade & Artizanat"),
        SERVICII                  ("Servicii"),
        MATERII_PRIME             ("Materii prime"),
        ALTELE                    ("Altele");

        private final String label;
        Categorie(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public enum UnitateMasura {
        BUC("bucata"),
        KG("kilogram"),
        L("litru"),
        M("metru"),
        PERECHE("pereche"),
        SET("set"),
        PACHET("pachet");

        private final String label;
        UnitateMasura(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilizator_id")
    private Utilizator utilizator;
}