package com.example.Project.Teza.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "utilizatori")
public class Utilizator {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String parola;

    private String numeAfacere;
    private String telefon;

    // -- Date firma pentru generare documente PDF --------------------------
    private String numeFirma;      // ex: SC MODA CHIC SRL
    private String cui;            // ex: RO12345678
    private String nrRegCom;       // ex: J40/1234/2020
    private String adresaFirma;    // ex: Str. Florilor 5, Iasi
    private String emailFirma;     // ex: contact@modachic.ro
    private String telefonFirma;   // ex: 0712 345 678
    private String banca;          // ex: ING Bank
    private String iban;           // ex: RO49AAAA1B31007593840000

    // -- Date suplimentare firma, necesare pentru generarea AUTOMATA a AWB-urilor -----
    // (blocul "expeditor_*" din fisierul de import al curierilor). Adresa de mai sus
    // (adresaFirma) e text liber, folosita pentru facturi/avize; AWB-ul cere insa
    // judet/localitate/strada/cod postal pe campuri SEPARATE, ca sa nu mai fie nevoie
    // sa le deducem prin parsare de text (predispusa la erori pentru localitati
    // necunoscute sau adrese scrise altfel decat se asteapta).
    private String persoanaContactFirma; // persoana care preda coletul curierului (nume real, nu numele afacerii)
    private String judetFirma;
    private String localitateFirma;
    private String stradaFirma;
    private String numarFirma;
    private String codPostalFirma;
    private String blocFirma;
    private String scaraFirma;
    private String etajFirma;
    private String apartamentFirma;
}