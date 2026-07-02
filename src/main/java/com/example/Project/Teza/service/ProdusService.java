package com.example.Project.Teza.service;

import com.example.Project.Teza.model.Furnizor;
import com.example.Project.Teza.model.Produs;
import com.example.Project.Teza.repository.ProdusRepository;
import com.example.Project.Teza.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProdusService {

    private final ProdusRepository produsRepository;
    private final UtilizatorService utilizatorService;
    private final FurnizorService furnizorService;

    public List<Produs> toate() {
        return produsRepository.findByUtilizatorId(utilizatorService.getIdUtilizatorCurent());
    }

    public Produs salveaza(Produs produs) {
        if (produs.getUtilizator() == null) {
            produs.setUtilizator(utilizatorService.getUtilizatorCurent());
        }
        return produsRepository.save(produs);
    }

    // Salveaza produs cu furnizor dupa ID (folosit din controller)
    public Produs salveazaCuFurnizor(Produs produs, Long furnizorId) {
        if (furnizorId != null) {
            Furnizor furnizor = furnizorService.gasesteDupaId(furnizorId);
            produs.setFurnizor(furnizor);
        } else {
            produs.setFurnizor(null);
        }
        return salveaza(produs);
    }

    public Produs gasesteDupaId(Long id) {
        Produs produs = produsRepository.findById(id)
                .orElseThrow(() -> new ResursaNegasitaException("Produs", id));
        if (!produs.getUtilizator().getId().equals(utilizatorService.getIdUtilizatorCurent())) {
            throw new AccesInterzisException("produs");
        }
        return produs;
    }

    public void sterge(Long id) {
        gasesteDupaId(id);
        produsRepository.deleteById(id);
    }

    /**
     * ERP: produsele nu se sterg - se dezactiveaza.
     * Un produs cu stoc > 0 nu poate fi dezactivat (trebuie lichidat stocul mai intai).
     * Un produs inactiv nu apare in comenzi/smart input dar ramane in istoricul comenzilor.
     */
    public void dezactiveaza(Long id) {
        Produs p = gasesteDupaId(id);
        if (p.getStoc() != null && p.getStoc() > 0) {
            throw new OperatiuneInvalidaException(
                    "Produsul \"" + p.getNume() + "\" are " + p.getStoc() +
                            " buc in stoc. Lichideaza stocul inainte de dezactivare.");
        }
        p.setActiv(false);
        produsRepository.save(p);
    }

    public void reactiveaza(Long id) {
        Produs p = gasesteDupaId(id);
        p.setActiv(true);
        produsRepository.save(p);
    }

    public List<Produs> toateActive() {
        return toate().stream().filter(Produs::esteActiv).toList();
    }

    public List<Produs> toateInclusivinactive() {
        return toate();
    }

    /**
     * C3 - verifica daca un cod SKU este deja folosit de alt produs al utilizatorului curent.
     * Comparatia este case-insensitive: "TRC-001" si "trc-001" sunt considerate identice.
     */
    public boolean existaSkuPentruUtilizator(String codSKU) {
        if (codSKU == null || codSKU.isBlank()) return false;
        return produsRepository.existsByCodSKUIgnoreCaseAndUtilizatorId(
                codSKU.trim(), utilizatorService.getIdUtilizatorCurent());
    }

    public List<Produs> produseSubStocMinim() {
        return produsRepository.findByUtilizatorId(utilizatorService.getIdUtilizatorCurent())
                .stream().filter(Produs::esteStocCritic).toList();
    }

    public long numarProduseSubStocMinim() {
        return produseSubStocMinim().size();
    }

    // Produsele unui furnizor specific
    public List<Produs> dupaFurnizor(Long furnizorId) {
        return produsRepository.findByUtilizatorIdAndFurnizorId(
                utilizatorService.getIdUtilizatorCurent(), furnizorId);
    }

    /**
     * Scade stocul unui produs cu cantitatea vanduta.
     * Folosit automat la confirmarea comenzii (NOU -> CONFIRMAT).
     * Nu blocheaza daca stocul devine negativ - alertele de stoc critic
     * vor aparea in Dashboard.
     */
    @org.springframework.transaction.annotation.Transactional
    public void scadeStoc(String codSKU, int cantitate) {
        if (codSKU == null || codSKU.isBlank() || cantitate <= 0) return;
        produsRepository.findByCodSKUIgnoreCaseAndUtilizatorId(
                        codSKU.trim(), utilizatorService.getIdUtilizatorCurent())
                .ifPresent(p -> {
                    int stocNou = (p.getStoc() != null ? p.getStoc() : 0) - cantitate;
                    p.setStoc(stocNou);
                    produsRepository.save(p);
                });
    }

    /**
     * Restituie stocul unui produs.
     * Folosit automat la anularea unei comenzi deja confirmate (storno stoc).
     */
    @org.springframework.transaction.annotation.Transactional
    public void adaugaStoc(String codSKU, int cantitate) {
        if (codSKU == null || codSKU.isBlank() || cantitate <= 0) return;
        produsRepository.findByCodSKUIgnoreCaseAndUtilizatorId(
                        codSKU.trim(), utilizatorService.getIdUtilizatorCurent())
                .ifPresent(p -> {
                    int stocNou = (p.getStoc() != null ? p.getStoc() : 0) + cantitate;
                    p.setStoc(stocNou);
                    produsRepository.save(p);
                });
    }
}