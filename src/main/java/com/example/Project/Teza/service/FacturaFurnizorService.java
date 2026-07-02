package com.example.Project.Teza.service;

import com.example.Project.Teza.model.FacturaFurnizor;
import com.example.Project.Teza.repository.FacturaFurnizorRepository;
import com.example.Project.Teza.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FacturaFurnizorService {

    private final FacturaFurnizorRepository facturaRepository;
    private final UtilizatorService utilizatorService;

    public List<FacturaFurnizor> toate() {
        return facturaRepository.findByUtilizatorId(utilizatorService.getIdUtilizatorCurent());
    }

    public List<FacturaFurnizor> pentruFurnizor(Long furnizorId) {
        return facturaRepository.findByFurnizorIdAndUtilizatorId(
                furnizorId, utilizatorService.getIdUtilizatorCurent());
    }

    public List<FacturaFurnizor> dupaStatus(FacturaFurnizor.StatusFactura status) {
        return facturaRepository.findByUtilizatorIdAndStatus(
                utilizatorService.getIdUtilizatorCurent(), status);
    }

    public FacturaFurnizor salveaza(FacturaFurnizor factura) {
        if (factura.getUtilizator() == null) {
            factura.setUtilizator(utilizatorService.getUtilizatorCurent());
        }
        return facturaRepository.save(factura);
    }

    public FacturaFurnizor gasesteDupaId(Long id) {
        FacturaFurnizor f = facturaRepository.findById(id)
                .orElseThrow(() -> new ResursaNegasitaException("Factura furnizor", id));
        if (!f.getUtilizator().getId().equals(utilizatorService.getIdUtilizatorCurent()))
            throw new AccesInterzisException("factura furnizor");
        return f;
    }

    /**
     * Returneaza furnizorId direct din DB fara a incarca entitatea Furnizor (evita LazyInit).
     * Apeleaza INAINTE de confirmaPlata(), nu dupa.
     */
    public Long getFurnizorId(Long facturaId) {
        Long furnizorId = facturaRepository.findFurnizorIdByFacturaId(facturaId);
        if (furnizorId == null)
            throw new ResursaNegasitaException("Factura nu a putut fi gasita sau nu are un furnizor asociat.");
        return furnizorId;
    }

    // Confirma plata cu tip document + numar document
    public FacturaFurnizor confirmaPlata(Long facturaId, double suma,
                                         String tipDocumentPlata, String numarDocumentPlata,
                                         LocalDate dataPlata) {
        FacturaFurnizor factura = gasesteDupaId(facturaId);
        double nouaPlatita = (factura.getSumaPlatita() != null ? factura.getSumaPlatita() : 0.0) + suma;
        factura.setSumaPlatita(nouaPlatita);
        // Stocheaza ca "TipDocument - NumarDocument"
        factura.setMetodaPlata(tipDocumentPlata);
        factura.setDocumentPlata(numarDocumentPlata);
        factura.setDataPlata(dataPlata != null ? dataPlata : LocalDate.now());
        factura.recalculeazaStatus();
        return facturaRepository.save(factura);
    }

    public void sterge(Long id) {
        gasesteDupaId(id);
        facturaRepository.deleteById(id);
    }

    /**
     * Stornare factura - logica ERP corecta.
     * Nu stergem fizic din baza de date (audit trail). In schimb:
     * - Prefixam numarul facturii cu "STORNO-"
     * - Setam suma platita = total (anulam datoria)
     * - Recalculam statusul -> devine PLATITA (stornata)
     * Disponibil doar pentru facturi cu status NEPLATITA.
     */
    public void storneaza(Long facturaId) {
        FacturaFurnizor f = gasesteDupaId(facturaId);
        if (f.getStatus() != FacturaFurnizor.StatusFactura.NEPLATITA) {
            throw new OperatiuneInvalidaException("Stornarea este permisa doar pentru facturi neachitate. " +
                    "Factura " + f.getNumarFactura() + " are status: " + f.getStatus().name());
        }
        if (f.getNumarFactura() != null && !f.getNumarFactura().startsWith("STORNO-")) {
            f.setNumarFactura("STORNO-" + f.getNumarFactura());
        }
        f.setSumaPlatita(f.getTotalFactura()); // anuleaza datoria
        f.recalculeazaStatus();                // -> PLATITA
        facturaRepository.save(f);
    }


    public double totalDatorii() {
        Double total = facturaRepository.totalDatorii(utilizatorService.getIdUtilizatorCurent());
        return total != null ? total : 0.0;
    }

    public double totalDatoriiCatreFurnizor(Long furnizorId) {
        Double total = facturaRepository.totalDatoriiCatreFurnizor(
                furnizorId, utilizatorService.getIdUtilizatorCurent());
        return total != null ? total : 0.0;
    }

    public long numarFacturiScadente() {
        return toate().stream().filter(FacturaFurnizor::esteScadenta).count();
    }

    /**
     * Facturi care expira in urmatoarele `zile` zile (dar nu sunt inca scadente).
     * Folosit pentru alertele preventive din Dashboard.
     */
    public List<FacturaFurnizor> scadenteInCurand(int zile) {
        LocalDate azi   = LocalDate.now();
        LocalDate limit = azi.plusDays(zile);
        return toate().stream()
                .filter(f -> f.getStatus() != FacturaFurnizor.StatusFactura.PLATITA)
                .filter(f -> f.getDataScadenta() != null)
                .filter(f -> !f.getDataScadenta().isBefore(azi))   // nu sunt inca scadente
                .filter(f -> !f.getDataScadenta().isAfter(limit))  // expira in max `zile` zile
                .sorted((a, b) -> a.getDataScadenta().compareTo(b.getDataScadenta()))
                .toList();
    }

    /** Numarul de zile pana la scadenta (negativ = deja scadenta) */
    public static long zilePanaLaScadenta(FacturaFurnizor f) {
        if (f.getDataScadenta() == null) return Long.MAX_VALUE;
        return java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), f.getDataScadenta());
    }
}