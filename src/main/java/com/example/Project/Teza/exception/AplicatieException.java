package com.example.Project.Teza.exception;

import org.springframework.http.HttpStatus;

/**
 * Baza ierarhiei de exceptii custom ale aplicatiei.
 * Orice exceptie de business extinde aceasta clasa,
 * ceea ce permite GlobalExceptionHandler sa le prinda
 * toate printr-un singur handler si sa returneze
 * un mesaj clar utilizatorului, nu un stack trace tehnic.
 */
public class AplicatieException extends RuntimeException {

    private final HttpStatus status;
    private final String titlu;
    private final String detalii;

    public AplicatieException(HttpStatus status, String titlu, String mesaj, String detalii) {
        super(mesaj);
        this.status  = status;
        this.titlu   = titlu;
        this.detalii = detalii;
    }

    public HttpStatus getStatus()  { return status; }
    public String getTitlu()       { return titlu; }
    public String getDetalii()     { return detalii; }
}
