package com.example.Project.Teza.controller;

import com.example.Project.Teza.exception.AplicatieException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.ui.Model;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

/**
 * Handler global pentru toate exceptiile aplicatiei.
 *
 * Strategia de prindere:
 *  1. AplicatieException (+ subclase) - erori de business cu mesaj clar pentru utilizator
 *  2. DataIntegrityViolationException - constrangeri DB (duplicate, FK violation etc.)
 *  3. JpaSystemException - erori JPA/Hibernate (lazy load in afara tranzactiei etc.)
 *  4. MissingServletRequestParameterException - camp obligatoriu lipsa din formular
 *  5. NoHandlerFoundException / NoResourceFoundException - pagina inexistenta (404)
 *  6. Exception - fallback general pentru orice alta eroare neacoperita
 *
 * Endpoint-urile AJAX (Accept: application/json) primesc raspuns JSON, nu pagina HTML.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    // -- 1. Toate exceptiile custom de business (AplicatieException + subclase) --------------
    @ExceptionHandler(AplicatieException.class)
    public Object handleAplicatieException(AplicatieException ex,
                                           HttpServletRequest request,
                                           Model model) {
        if (esteAjax(request)) {
            return ResponseEntity
                    .status(ex.getStatus())
                    .body(Map.of(
                            "eroare", true,
                            "titlu", ex.getTitlu(),
                            "mesaj", ex.getMessage(),
                            "detalii", ex.getDetalii()
                    ));
        }
        model.addAttribute("titluEroare",   ex.getTitlu());
        model.addAttribute("mesajEroare",   ex.getMessage());
        model.addAttribute("detaliiEroare", ex.getDetalii());
        model.addAttribute("urlInapoi",     getReferer(request));
        model.addAttribute("codHttp",       ex.getStatus().value());
        return "error";
    }

    // -- 2. Incalcari de constrangeri la baza de date ----------------------------------------
    @ExceptionHandler(DataIntegrityViolationException.class)
    public Object handleDataIntegrity(DataIntegrityViolationException ex,
                                      HttpServletRequest request,
                                      Model model) {
        // Extrage cauza radacina pentru un mesaj mai specific
        String cauza = "";
        Throwable root = ex;
        while (root.getCause() != null) root = root.getCause();
        String rootMsg = root.getMessage() != null ? root.getMessage().toLowerCase() : "";

        String mesaj;
        String detalii;

        if (rootMsg.contains("duplicate") || rootMsg.contains("unique")) {
            mesaj   = "Exista deja o inregistrare cu aceste date.";
            detalii = "Un camp unic (ex: cod SKU, cod promotional, email) are o valoare care exista deja. Modifica valorile duplicate si incearca din nou.";
        } else if (rootMsg.contains("foreign key") || rootMsg.contains("referenced")) {
            mesaj   = "Aceasta inregistrare este folosita in alta parte si nu poate fi stearsa.";
            detalii = "De exemplu, un produs care apare in comenzi, sau un furnizor cu receptii asociate, nu poate fi eliminat direct. Arhiveaza-l sau elimina mai intai referintele.";
        } else if (rootMsg.contains("not-null") || rootMsg.contains("null value")) {
            mesaj   = "Un camp obligatoriu nu a fost completat.";
            detalii = "Verifica ca toate campurile marcate cu * sunt completate corect.";
        } else {
            mesaj   = "Datele nu au putut fi salvate din cauza unei incompatibilitati cu baza de date.";
            detalii = "Detaliu tehnic: " + (root.getMessage() != null ? root.getMessage() : "necunoscut");
        }

        if (esteAjax(request)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("eroare", true, "titlu", "Conflict de date", "mesaj", mesaj, "detalii", detalii));
        }
        model.addAttribute("titluEroare",   "Conflict de date");
        model.addAttribute("mesajEroare",   mesaj);
        model.addAttribute("detaliiEroare", detalii);
        model.addAttribute("urlInapoi",     getReferer(request));
        model.addAttribute("codHttp",       409);
        return "error";
    }

    // -- 3. Erori JPA/Hibernate (lazy load in afara sesiunii, conversie tip etc.) -----------
    @ExceptionHandler(JpaSystemException.class)
    public Object handleJpa(JpaSystemException ex,
                            HttpServletRequest request,
                            Model model) {
        Throwable root = ex;
        while (root.getCause() != null) root = root.getCause();
        String rootMsg = root.getMessage() != null ? root.getMessage().toLowerCase() : "";

        String mesaj;
        String detalii;

        if (rootMsg.contains("lazyinitializationexception") || rootMsg.contains("no session")) {
            mesaj   = "Datele necesare nu au putut fi incarcate complet.";
            detalii = "Eroare interna de incarcare laziness (sesiune JPA inchisa). Raporteaza aceasta problema.";
        } else if (rootMsg.contains("detached") || rootMsg.contains("persistent")) {
            mesaj   = "Inregistrarea a fost modificata din alt loc si nu poate fi salvata in starea curenta.";
            detalii = "Revino la pagina anterioara, reincarca datele si incearca din nou.";
        } else {
            mesaj   = "A aparut o eroare la accesarea bazei de date.";
            detalii = "Detaliu tehnic: " + root.getClass().getSimpleName() + " - " + root.getMessage();
        }

        if (esteAjax(request)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("eroare", true, "titlu", "Eroare baza de date", "mesaj", mesaj, "detalii", detalii));
        }
        model.addAttribute("titluEroare",   "Eroare baza de date");
        model.addAttribute("mesajEroare",   mesaj);
        model.addAttribute("detaliiEroare", detalii);
        model.addAttribute("urlInapoi",     getReferer(request));
        model.addAttribute("codHttp",       500);
        return "error";
    }

    // -- 4. Camp obligatoriu lipsa din formular ----------------------------------------------
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Object handleMissingParam(MissingServletRequestParameterException ex,
                                     HttpServletRequest request,
                                     Model model) {
        String mesaj = "Campul '" + ex.getParameterName() + "' este obligatoriu si nu a fost completat";        String detalii = "Completeaza toate campurile marcate cu * si trimite formularul din nou.";

        if (esteAjax(request)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("eroare", true, "titlu", "Formular incomplet", "mesaj", mesaj, "detalii", detalii));
        }
        model.addAttribute("titluEroare",   "Formular incomplet");
        model.addAttribute("mesajEroare",   mesaj);
        model.addAttribute("detaliiEroare", detalii);
        model.addAttribute("urlInapoi",     getReferer(request));
        model.addAttribute("codHttp",       400);
        return "error";
    }

    // -- 5. Pagina / resursa inexistenta (404) -----------------------------------------------
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(Exception ex, HttpServletRequest request, Model model) {
        model.addAttribute("titluEroare",   "Pagina nu a fost gasita");
        model.addAttribute("mesajEroare",   "Adresa '" + request.getRequestURI() + "' nu exista in aplicatie.");
        model.addAttribute("detaliiEroare", "Verifica URL-ul sau foloseste meniul din stanga pentru navigare.");
        model.addAttribute("urlInapoi",     "/dashboard");
        model.addAttribute("codHttp",       404);
        return "error";
    }

    // -- 6. Fallback general - orice alta exceptie necaptata ---------------------------------
    @ExceptionHandler(Exception.class)
    public Object handleGeneral(Exception ex, HttpServletRequest request, Model model) {
        // Construim un detaliu tehnic util pentru debugging, fara sa expunem stack trace complet
        Throwable root = ex;
        while (root.getCause() != null) root = root.getCause();

        String locatie = "";
        for (StackTraceElement e : ex.getStackTrace()) {
            if (e.getClassName().contains("com.example")) {
                locatie = e.getClassName().replaceAll(".*\\.", "") + "." + e.getMethodName() + "():" + e.getLineNumber();
                break;
            }
        }

        String detaliiTehnice = root.getClass().getSimpleName()
                + (root.getMessage() != null ? ": " + root.getMessage() : "")
                + (locatie.isEmpty() ? "" : " (la " + locatie + ")");

        String mesaj = "A aparut o eroare neasteptata. Daca problema persista, contacteaza administratorul.";

        if (esteAjax(request)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("eroare", true, "titlu", "Eroare interna", "mesaj", mesaj, "detalii", detaliiTehnice));
        }
        model.addAttribute("titluEroare",   "Eroare interna");
        model.addAttribute("mesajEroare",   mesaj);
        model.addAttribute("detaliiEroare", detaliiTehnice);
        model.addAttribute("urlInapoi",     getReferer(request));
        model.addAttribute("codHttp",       500);
        return "error";
    }

    // -- Helper: detecteaza cereri AJAX (Accept: application/json sau X-Requested-With) ------
    private boolean esteAjax(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        String xrw    = request.getHeader("X-Requested-With");
        return (accept != null && accept.contains("application/json"))
                || "XMLHttpRequest".equals(xrw);
    }

    // -- Helper: returneaza path-ul paginii anterioare din header Referer --------------------
    private String getReferer(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer != null && !referer.isBlank()) {
            try {
                java.net.URL url = new java.net.URL(referer);
                return url.getPath();
            } catch (Exception ignored) {}
        }
        return "/dashboard";
    }
}
