package com.example.Project.Teza.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Migrare automata la startup:
 * 1. Elimina CHECK CONSTRAINT-ul vechi pe coloana status
 * 2. Migreaza valorile: IN_PROCESARE -> CONFIRMAT, FINALIZAT -> LIVRAT
 * Operatiunile sunt idempotente - pot rula de mai multe ori fara efecte.
 */
@Component
@RequiredArgsConstructor
public class StatusMigrareRunner {

    private final JdbcTemplate jdbc;

    @PostConstruct
    public void migreaza() {
        // Pasul 1: Elimina TOATE check constraint-urile de pe coloana status
        // (Hibernate poate genera nume diferite, le eliminam pe toate)
        try {
            // Gasim numele exact al constraint-ului din PostgreSQL system catalog
            var constraints = jdbc.queryForList(
                    "SELECT conname FROM pg_constraint " +
                            "WHERE conrelid = 'comenzi'::regclass AND contype = 'c' " +
                            "AND conname LIKE '%status%'",
                    String.class
            );
            for (String conname : constraints) {
                jdbc.execute("ALTER TABLE comenzi DROP CONSTRAINT IF EXISTS \"" + conname + "\"");
                System.out.println("[StatusMigrare] Eliminat constraint: " + conname);
            }
            // Elimina si constraint-ul generic generat de Hibernate (fara 'status' in nume)
            jdbc.execute("ALTER TABLE comenzi DROP CONSTRAINT IF EXISTS comenzi_status_check");
        } catch (Exception e) {
            System.out.println("[StatusMigrare] Nu s-a putut elimina constraint (poate nu exista): " + e.getMessage());
        }

        // Pasul 2: Migreaza valorile vechi -> noi
        int c1 = 0, c2 = 0;
        try {
            c1 = jdbc.update("UPDATE comenzi SET status = 'CONFIRMAT' WHERE status = 'IN_PROCESARE'");
            c2 = jdbc.update("UPDATE comenzi SET status = 'LIVRAT'    WHERE status = 'FINALIZAT'");
        } catch (Exception e) {
            System.out.println("[StatusMigrare] Eroare la migrare date: " + e.getMessage());
        }

        if (c1 > 0 || c2 > 0) {
            System.out.printf("[StatusMigrare] Migrat: %d IN_PROCESARE->CONFIRMAT, %d FINALIZAT->LIVRAT%n", c1, c2);
        } else {
            System.out.println("[StatusMigrare] Nicio migrare necesara.");
        }
    }
}