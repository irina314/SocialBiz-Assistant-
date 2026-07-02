package com.example.Project.Teza.service;

import com.example.Project.Teza.model.CampanieMktg;
import com.example.Project.Teza.model.Comanda;
import com.example.Project.Teza.model.ComandaItem;
import com.example.Project.Teza.repository.ComandaRepository;
import com.example.Project.Teza.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ComandaService {

    private final ComandaRepository comandaRepository;
    private final UtilizatorService utilizatorService;
    private final ClientService clientService;
    private final CampanieMktgService campanieMktgService;
    private final ProdusService produsService;

    public List<Comanda> toate() {
        return comandaRepository.findByUtilizatorId(
                utilizatorService.getIdUtilizatorCurent());
    }

    public Comanda salveaza(Comanda comanda) {
        if (comanda.getUtilizator() == null) {
            comanda.setUtilizator(utilizatorService.getUtilizatorCurent());
        }
        aplicaReducereaCampaniei(comanda);
        Comanda salvata = comandaRepository.save(comanda);
        if (comanda.getNumeClient() != null && comanda.getTelefon() != null) {
            clientService.salveazaSauActualizeaza(
                    comanda.getNumeClient(),
                    comanda.getTelefon(),
                    comanda.getAdresa());
        }
        return salvata;
    }

    /**
     * Daca comanda are un cod promotional valid (atribuit unei campanii cu reducere activa),
     * recalculeaza automat pretul unitar al produselor eligibile.
     * Pretul original e pastrat in pretUnitarOriginal pentru afisare ("pret taiat"),
     * iar pretUnitar devine pretul final folosit la calculul totalului.
     * Daca nu exista cod promo care sa corespunda unei campanii, se incearca si
     * campania atribuita MANUAL (campul "campanie" - util pentru campanii cu reducere
     * care nu au neaparat un cod promo, ex. campanii de influenceri).
     * Daca nu exista cod / campanie / reducere, items raman neschimbate.
     */
    private void aplicaReducereaCampaniei(Comanda comanda) {
        if (comanda.getItems() == null || comanda.getItems().isEmpty()) return;

        Optional<CampanieMktg> campanieOpt = campanieMktgService.gasesteDupaCodPromo(comanda.getCodPromotional());
        if (campanieOpt.isEmpty()) {
            campanieOpt = campanieMktgService.gasesteDupaNume(comanda.getCampanie());
        }
        if (campanieOpt.isEmpty()) return;

        CampanieMktg campanie = campanieOpt.get();
        if (campanie.getAreReducere() == null || !campanie.getAreReducere()) return;

        for (ComandaItem item : comanda.getItems()) {
            // idProdus poate fi codSKU (selectat din lista) sau text liber introdus manual.
            boolean eligibil = campanie.reducereSeAplicaPe(item.getIdProdus());
            if (!eligibil || item.getPretUnitar() == null) continue;

            double pretOriginal = item.getPretUnitar();
            double pretRedus = calculeazaPretRedus(pretOriginal, campanie);
            if (pretRedus < pretOriginal) {
                item.setPretUnitarOriginal(pretOriginal);
                item.setPretUnitar(pretRedus);
            }
        }
    }

    private double calculeazaPretRedus(double pretOriginal, CampanieMktg campanie) {
        if (campanie.getValoareReducere() == null) return pretOriginal;
        double valoare = campanie.getValoareReducere();
        double pretRedus;
        if (campanie.getTipReducere() == CampanieMktg.TipReducere.PROCENT) {
            pretRedus = pretOriginal * (1 - valoare / 100.0);
        } else {
            pretRedus = pretOriginal - valoare;
        }
        // Reducerea nu poate face pretul negativ.
        return Math.max(pretRedus, 0.0);
    }

    public Comanda gasesteDupaId(Long id) {
        Comanda comanda = comandaRepository.findById(id)
                .orElseThrow(() -> new ResursaNegasitaException("Comanda", id));
        if (!comanda.getUtilizator().getId()
                .equals(utilizatorService.getIdUtilizatorCurent())) {
            throw new AccesInterzisException("comanda");
        }
        return comanda;
    }

    public void sterge(Long id) {
        gasesteDupaId(id);
        comandaRepository.deleteById(id);
    }

    /**
     * Actualizare status cu validare flux.
     * Arunca exceptie daca tranzitia nu e permisa.
     */
    public Comanda actualizeazaStatus(Long id, Comanda.StatusComanda statusNou) {
        Comanda comanda = gasesteDupaId(id);
        Comanda.StatusComanda statusCurent = comanda.getStatus();

        if (!statusCurent.poateAvansaSpre(statusNou)) {
            throw new OperatiuneInvalidaException(
                    "Tranzitia de status " + statusCurent + " -> " + statusNou + " nu este permisa.",
                    "Verifica fluxul permis de status: NOU -> CONFIRMAT -> EXPEDIAT -> LIVRAT. Anularea e posibila doar din NOU sau CONFIRMAT.");
        }

        // La confirmarea comenzii scadem stocul produselor comandate.
        // La anulare din CONFIRMAT, restituim stocul (storno).
        if (statusNou == Comanda.StatusComanda.CONFIRMAT
                && statusCurent == Comanda.StatusComanda.NOU) {
            scadeStocPentruComanda(comanda);
        } else if (statusNou == Comanda.StatusComanda.ANULAT
                && statusCurent == Comanda.StatusComanda.CONFIRMAT) {
            restituieStocPentruComanda(comanda);
        }

        comanda.setStatus(statusNou);
        return comandaRepository.save(comanda);
    }

    /**
     * Scade din stocul fiecarui produs cantitatea comandata.
     * Daca un produs nu mai exista in catalog (a fost dezactivat), sare peste el cu un log.
     * Nu blocheaza confirmarea daca stocul e insuficient (poate fi comanda pe stoc negativ,
     * caz frecvent la micro-antreprenori care fac drop-shipping sau precomanda) --
     * dar alerta de stoc critic va aparea imediat in Dashboard.
     */
    private void scadeStocPentruComanda(Comanda comanda) {
        if (comanda.getItems() == null) return;
        for (com.example.Project.Teza.model.ComandaItem item : comanda.getItems()) {
            produsService.scadeStoc(item.getIdProdus(), item.getCantitate() != null ? item.getCantitate() : 1);
        }
    }

    /**
     * Restituie stocul la anularea unei comenzi deja confirmate (storno).
     */
    private void restituieStocPentruComanda(Comanda comanda) {
        if (comanda.getItems() == null) return;
        for (com.example.Project.Teza.model.ComandaItem item : comanda.getItems()) {
            produsService.adaugaStoc(item.getIdProdus(), item.getCantitate() != null ? item.getCantitate() : 1);
        }
    }

    public Comanda confirmaPlata(Long id, String metoda, Double sumaIncasata) {
        Comanda comanda = gasesteDupaId(id);
        double total = comanda.calculeazaTotal();

        // Determina statusul platii in functie de suma incasata
        if (sumaIncasata != null && sumaIncasata > 0 && sumaIncasata < total) {
            comanda.setStatusPlata(Comanda.StatusPlata.AVANS_PARTIAL);
        } else if ("online".equalsIgnoreCase(metoda) || "card".equalsIgnoreCase(metoda)) {
            comanda.setStatusPlata(Comanda.StatusPlata.PLATIT_ONLINE);
        } else if ("card_fizic".equalsIgnoreCase(metoda)) {
            comanda.setStatusPlata(Comanda.StatusPlata.PLATIT_CU_CARDUL);
        } else {
            comanda.setStatusPlata(Comanda.StatusPlata.PLATIT_RAMBURS);
        }
        comanda.setMetodaPlata(metoda);
        comanda.setSumaIncasata(sumaIncasata);
        comanda.setDataConfirmarePlata(java.time.LocalDateTime.now());
        return comandaRepository.save(comanda);
    }

    public long numaraDupaStatus(Comanda.StatusComanda status) {
        return comandaRepository.findByUtilizatorIdAndStatus(
                utilizatorService.getIdUtilizatorCurent(), status).size();
    }

    public long numaraNeplatite() {
        return toate().stream()
                .filter(c -> !c.esteAchitata()
                        && c.getStatus() != Comanda.StatusComanda.ANULAT)
                .count();
    }

    /** Comenzile eligibile pentru export AWB (doar CONFIRMAT) */
    public List<Comanda> pentruExport() {
        return comandaRepository.findByUtilizatorIdAndStatus(
                utilizatorService.getIdUtilizatorCurent(),
                Comanda.StatusComanda.CONFIRMAT);
    }

    /** Marcheaza comenzile ca EXPEDIAT dupa export CSV */
    public void marcheazaExpediat(List<Long> ids) {
        ids.forEach(id -> {
            try { actualizeazaStatus(id, Comanda.StatusComanda.EXPEDIAT); }
            catch (Exception ignored) { /* deja expediat sau invalid */ }
        });
    }
}