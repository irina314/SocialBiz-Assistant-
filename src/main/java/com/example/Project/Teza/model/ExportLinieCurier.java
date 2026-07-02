package com.example.Project.Teza.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Reprezinta o linie din exportul de AWB-uri catre o firma de curierat.
 *
 * Combina datele permanente ale unei {@link Comanda} cu date specifice expedierii
 * (greutate, nr. colete, cod postal, continut) care se completeaza manual la export,
 * direct in formular - NU se salveaza in baza de date, pentru ca difera de la o
 * expediere la alta si nu fac parte din ciclul de viata al comenzii.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExportLinieCurier {

    private Comanda comanda;

    /** Cod postal destinatar - OBLIGATORIU la Cargus, recomandat la toti ceilalti. */
    private String codPostal;

    /** Greutatea reala a coletului in kg - influenteaza tariful, mai ales la DPD/Cargus. */
    private double greutateKg = 1.0;

    /** Numarul de colete fizice din expediere. */
    private int nrColete = 1;

    /** Descrierea continutului - implicit generata din produsele comenzii, editabila. */
    private String continut = "";
}
