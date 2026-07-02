package com.example.Project.Teza.exception;

import org.springframework.http.HttpStatus;

/**
 * Aruncata cand o inregistrare cautata (comanda, produs, campanie etc.)
 * nu exista in baza de date sau nu apartine utilizatorului curent.
 */
public class ResursaNegasitaException extends AplicatieException {

    public ResursaNegasitaException(String tipResursa, Object identificator) {
        super(
            HttpStatus.NOT_FOUND,
            tipResursa + " negasit(a)",
            tipResursa + " cu identificatorul '" + identificator + "' nu exista sau nu iti apartine.",
            "Inregistrarea poate fi fost stearsa. Revino la lista principala si incearca din nou."
        );
    }

    /** Fara identificator specific (ex: "Utilizatorul autentificat nu a fost gasit") */
    public ResursaNegasitaException(String mesajComplet) {
        super(
            HttpStatus.NOT_FOUND,
            "Inregistrare negasita",
            mesajComplet,
            "Inregistrarea poate fi fost stearsa. Revino la lista principala si incearca din nou."
        );
    }
}
