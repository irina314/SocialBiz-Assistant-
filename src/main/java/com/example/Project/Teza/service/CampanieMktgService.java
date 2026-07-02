package com.example.Project.Teza.service;

import com.example.Project.Teza.model.CampanieMktg;
import com.example.Project.Teza.repository.MarketingRepository;
import com.example.Project.Teza.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Gestioneaza CRUD-ul campaniilor de marketing, separat de MarketingService
 * (care calculeaza analytics/ROAS pe baza comenzilor).
 * Separarea exista ca sa evite o dependenta circulara:
 * ComandaService are nevoie sa caute campania dupa codul promo (pentru reduceri),
 * iar MarketingService are nevoie de ComandaService (pentru calcule de venit) -
 * cele doua nu pot depinde una de cealalta direct.
 */
@Service
@RequiredArgsConstructor
public class CampanieMktgService {

    private final MarketingRepository marketingRepository;
    private final UtilizatorService utilizatorService;

    public List<CampanieMktg> toateCampaniile() {
        return marketingRepository.findByUtilizatorId(
                utilizatorService.getIdUtilizatorCurent());
    }

    public CampanieMktg salveazaCampanie(CampanieMktg campanie) {
        if (campanie.getUtilizator() == null)
            campanie.setUtilizator(utilizatorService.getUtilizatorCurent());
        return marketingRepository.save(campanie);
    }

    public CampanieMktg gasesteDupaId(Long id) {
        CampanieMktg c = marketingRepository.findById(id)
                .orElseThrow(() -> new ResursaNegasitaException("Campanie", id));
        if (!c.getUtilizator().getId().equals(utilizatorService.getIdUtilizatorCurent()))
            throw new AccesInterzisException("campanie");
        return c;
    }

    public void stergeCampanie(Long id) {
        gasesteDupaId(id);
        marketingRepository.deleteById(id);
    }

    /**
     * Gaseste campania a carei cod promo corespunde (case-insensitive, fara spatii)
     * codului dat. Returneaza empty daca nu exista nicio campanie cu acel cod
     * sau daca codul e null/gol.
     */
    public Optional<CampanieMktg> gasesteDupaCodPromo(String codPromo) {
        if (codPromo == null || codPromo.isBlank()) return Optional.empty();
        String cautat = codPromo.trim();
        return toateCampaniile().stream()
                .filter(c -> c.getCodPromo() != null && c.getCodPromo().trim().equalsIgnoreCase(cautat))
                .findFirst();
    }

    /**
     * Gaseste campania al carei nume corespunde exact (case-insensitive, fara spatii)
     * celui dat. Folosit pentru atribuirea MANUALA a unei campanii (fara cod promo),
     * ex. campanii cu reducere per produs setate direct din selectorul de campanii.
     */
    public Optional<CampanieMktg> gasesteDupaNume(String nume) {
        if (nume == null || nume.isBlank()) return Optional.empty();
        String cautat = nume.trim();
        return toateCampaniile().stream()
                .filter(c -> c.getNume() != null && c.getNume().trim().equalsIgnoreCase(cautat))
                .findFirst();
    }

    public long numarCampaniiActive() {
        return marketingRepository.findByUtilizatorIdAndStatus(
                utilizatorService.getIdUtilizatorCurent(),
                CampanieMktg.Status.ACTIVA).size();
    }
}