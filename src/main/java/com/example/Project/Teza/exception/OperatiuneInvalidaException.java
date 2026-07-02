package com.example.Project.Teza.exception;

import org.springframework.http.HttpStatus;

/**
 * Aruncata cand se incearca o operatiune invalida din cauza starii
 * curente a inregistrarii (ex: schimbare status nepermisa, stornare
 * factura platita, receptie deja facturata).
 */
public class OperatiuneInvalidaException extends AplicatieException {

    public OperatiuneInvalidaException(String mesaj, String sfat) {
        super(
            HttpStatus.BAD_REQUEST,
            "Operatiune nepermisa",
            mesaj,
            sfat
        );
    }

    public OperatiuneInvalidaException(String mesaj) {
        this(mesaj, "Verifica starea curenta a inregistrarii si incearca o alta actiune.");
    }
}
