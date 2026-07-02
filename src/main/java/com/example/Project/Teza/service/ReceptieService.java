package com.example.Project.Teza.service;

import com.example.Project.Teza.model.*;
import com.example.Project.Teza.repository.ProdusRepository;
import com.example.Project.Teza.repository.ReceptieRepository;
import com.example.Project.Teza.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReceptieService {

    private final ReceptieRepository receptieRepository;
    private final ProdusRepository produsRepository;
    private final FacturaFurnizorService facturaService;
    private final FurnizorService furnizorService;
    private final UtilizatorService utilizatorService;

    public List<Receptie> toate() {
        return receptieRepository.findByUtilizatorIdOrderByDataInregistrareDesc(
                utilizatorService.getIdUtilizatorCurent());
    }

    public List<Receptie> pentruFurnizor(Long furnizorId) {
        return receptieRepository.findByFurnizorIdAndUtilizatorId(
                furnizorId, utilizatorService.getIdUtilizatorCurent());
    }

    // Receptii pe aviz pentru care inca nu s-a primit factura
    public List<Receptie> avizeFaraFactura() {
        return receptieRepository.findByUtilizatorIdAndTipDocumentAndStatusFacturare(
                utilizatorService.getIdUtilizatorCurent(),
                Receptie.TipDocument.AVIZ,
                Receptie.StatusFacturare.NEFACTURATA);
    }

    public long numarAvizeFaraFactura() {
        return avizeFaraFactura().size();
    }

    public Receptie gasesteDupaId(Long id) {
        Receptie r = receptieRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Receptia nu a fost gasita"));
        if (!r.getUtilizator().getId().equals(utilizatorService.getIdUtilizatorCurent()))
            throw new AccesInterzisException("receptie");
        return r;
    }

    /**
     * Returneaza furnizorId fara a initia lazy load pe entitatea Furnizor.
     * Folosit in controller pentru redirect dupa ataseazaFactura.
     */
    @Transactional(readOnly = true)
    public Long getFurnizorId(Long receptieId) {
        Receptie r = receptieRepository.findById(receptieId)
                .orElseThrow(() -> new RuntimeException("Receptia nu a fost gasita"));
        // Accesam furnizor in cadrul tranzactiei - sesiunea e activa
        return r.getFurnizor().getId();
    }

    @Transactional
    public Receptie salveazaReceptie(Long furnizorId,
                                     String tipDocumentStr,
                                     String numarDocument,
                                     String dataScadentaStr,
                                     String note,
                                     List<Long> produsIds,
                                     List<Integer> cantitati,
                                     List<Double> preturi) {

        Furnizor furnizor = furnizorService.gasesteDupaId(furnizorId);
        Receptie.TipDocument tipDoc = Receptie.TipDocument.valueOf(tipDocumentStr);

        Receptie receptie = new Receptie();
        receptie.setFurnizor(furnizor);
        receptie.setTipDocument(tipDoc);
        receptie.setNumarDocument(numarDocument);
        receptie.setNote(note);
        receptie.setUtilizator(utilizatorService.getUtilizatorCurent());

        if (dataScadentaStr != null && !dataScadentaStr.isBlank()) {
            receptie.setDataScadentaFactura(LocalDate.parse(dataScadentaStr));
        }

        // Daca e factura -> deja facturata, daca e aviz -> nefacturata
        receptie.setStatusFacturare(tipDoc == Receptie.TipDocument.FACTURA
                ? Receptie.StatusFacturare.FACTURATA
                : Receptie.StatusFacturare.NEFACTURATA);

        // Adauga items si actualizeaza stocul
        for (int i = 0; i < produsIds.size(); i++) {
            if (produsIds.get(i) == null) continue;
            Produs produs = produsRepository.findById(produsIds.get(i)).orElse(null);
            if (produs == null) continue;

            ReceptieItem item = new ReceptieItem();
            item.setReceptie(receptie);
            item.setProdus(produs);
            item.setCantitate(cantitati.get(i));
            item.setPretAchizitie(preturi.get(i));
            receptie.getItems().add(item);

            // Actualizeaza stoc
            int stocNou = (produs.getStoc() != null ? produs.getStoc() : 0) + cantitati.get(i);
            produs.setStoc(stocNou);
            if (produs.getFurnizor() == null) produs.setFurnizor(furnizor);
            produsRepository.save(produs);
        }

        Receptie salvata = receptieRepository.save(receptie);

        // Daca documentul e FACTURA -> genereaza datoria imediat
        if (tipDoc == Receptie.TipDocument.FACTURA && !receptie.getItems().isEmpty()) {
            creeazaDatorie(salvata, numarDocument, salvata.getDataReceptie(),
                    salvata.getDataScadentaFactura());
        }

        return salvata;
    }

    // Ataseaza factura la o receptie pe aviz -> creeaza datoria
    @Transactional
    public Receptie ataseazaFactura(Long receptieId, String numarFactura,
                                    LocalDate dataFactura, LocalDate dataScadenta) {
        Receptie receptie = gasesteDupaId(receptieId);
        if (receptie.getTipDocument() != Receptie.TipDocument.AVIZ) {
            throw new OperatiuneInvalidaException("Aceasta receptie este deja atasata unei facturi.", "Pentru a modifica, acceseaza direct factura furnizorului.");
        }
        if (receptie.getStatusFacturare() == Receptie.StatusFacturare.FACTURATA) {
            throw new OperatiuneInvalidaException("Factura a fost deja inregistrata pentru acest aviz.", "Nu se poate inregistra o a doua factura pentru acelasi aviz de expeditie.");
        }

        receptie.setNumarFacturaAferenta(numarFactura);
        receptie.setDataFacturaAferenta(dataFactura);
        receptie.setDataScadentaFactura(dataScadenta);
        receptie.setStatusFacturare(Receptie.StatusFacturare.FACTURATA);
        receptieRepository.save(receptie);

        // Acum creeaza datoria
        creeazaDatorie(receptie, numarFactura, dataFactura, dataScadenta);

        return receptie;
    }

    private void creeazaDatorie(Receptie receptie, String numarFactura,
                                LocalDate dataEmitere, LocalDate dataScadenta) {
        FacturaFurnizor factura = new FacturaFurnizor();
        factura.setFurnizor(receptie.getFurnizor());
        factura.setNumarFactura(numarFactura != null ? numarFactura : "REC-" + receptie.getId());
        factura.setDescriere("Receptie #" + receptie.getId()
                + " - " + receptie.getNumarDocument());
        factura.setTotalFactura(receptie.calculeazaTotal());
        factura.setDataEmitere(dataEmitere != null ? dataEmitere : LocalDate.now());
        factura.setDataScadenta(dataScadenta);
        factura.setTip(FacturaFurnizor.TipFactura.DIN_RECEPTIE);
        factura.setUtilizator(receptie.getUtilizator());
        facturaService.salveaza(factura);
    }
}