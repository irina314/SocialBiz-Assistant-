package com.example.Project.Teza.exception;

import org.springframework.http.HttpStatus;

/**
 * Aruncata cand un utilizator incearca sa acceseze sau sa modifice
 * o inregistrare care nu ii apartine (alt utilizator a creat-o).
 */
public class AccesInterzisException extends AplicatieException {

    public AccesInterzisException(String tipResursa) {
        super(
            HttpStatus.FORBIDDEN,
            "Acces interzis",
            "Nu ai permisiunea sa accesezi sau sa modifici aceasta " + tipResursa + ".",
            "Daca crezi ca e o eroare, deconecteaza-te si autentifica-te din nou."
        );
    }
}
