package com.example.Project.Teza.exception;

import org.springframework.http.HttpStatus;

/**
 * Aruncata cand datele introduse de utilizator nu respecta
 * regulile de business (camp lipsa, valoare imposibila, cod inexistent etc.).
 */
public class DateInvalideException extends AplicatieException {

    public DateInvalideException(String mesaj, String sfat) {
        super(
            HttpStatus.BAD_REQUEST,
            "Date invalide",
            mesaj,
            sfat
        );
    }

    public DateInvalideException(String mesaj) {
        this(mesaj, "Verifica datele introduse si incearca din nou.");
    }
}
