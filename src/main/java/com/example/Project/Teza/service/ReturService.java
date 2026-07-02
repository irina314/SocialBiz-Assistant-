package com.example.Project.Teza.service;

import com.example.Project.Teza.model.Comanda;
import com.example.Project.Teza.model.Retur;
import com.example.Project.Teza.repository.ComandaRepository;
import com.example.Project.Teza.repository.ReturRepository;
import com.example.Project.Teza.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReturService {

    private final ReturRepository   returRepository;
    private final ComandaRepository comandaRepository;
    private final UtilizatorService utilizatorService;

    // -- CRUD ------------------------------------------------------------------

    public List<Retur> toate() {
        return returRepository.findByUtilizatorId(uid());
    }

    public List<Retur> dupaStatus(Retur.StatusRetur status) {
        return returRepository.findByUtilizatorIdAndStatus(uid(), status);
    }

    public Retur salveaza(Retur retur) {
        if (retur.getUtilizator() == null)
            retur.setUtilizator(utilizatorService.getUtilizatorCurent());
        return returRepository.save(retur);
    }

    public Retur gasesteDupaId(Long id) {
        Retur r = returRepository.findById(id)
                .orElseThrow(() -> new ResursaNegasitaException("Retur", id));
        if (!r.getUtilizator().getId().equals(uid()))
            throw new AccesInterzisException("retur");
        return r;
    }

    public Retur actualizeazaStatus(Long id, Retur.StatusRetur status) {
        Retur r = gasesteDupaId(id);
        r.setStatus(status);
        if (status != Retur.StatusRetur.IN_ASTEPTARE)
            r.setDataRezolvare(LocalDateTime.now());
        return returRepository.save(r);
    }

    public void sterge(Long id) {
        gasesteDupaId(id);
        returRepository.deleteById(id);
    }

    // -- KPI simple ------------------------------------------------------------

    public long numaraDupaStatus(Retur.StatusRetur status) {
        return dupaStatus(status).size();
    }

    /** Valoarea totala rambursata (retururi aprobate) */
    public double totalRambursari() {
        return dupaStatus(Retur.StatusRetur.APROBAT).stream()
                .mapToDouble(Retur::calculeazaTotal).sum();
    }

    /** Costul logistic total al retururilor aprobate (transport retur, manipulare etc.) */
    public double totalCostLogisticRetururi() {
        return dupaStatus(Retur.StatusRetur.APROBAT).stream()
                .mapToDouble(r -> r.getCostLogisticRetur() != null ? r.getCostLogisticRetur() : 0.0)
                .sum();
    }

    /** Rata de retur: nr. retururi / total comenzi x 100 */
    public double rataRetururi(long totalComenzi) {
        if (totalComenzi == 0) return 0.0;
        return (double) toate().size() / totalComenzi * 100.0;
    }

    // -- Impact ROAS - INIMA ARGUMENTULUI DIN TEZA -----------------------------

    /**
     * ROAS REAL = (venit brut - rambursari aprobate - cost logistic retururi) / buget reclame
     *
     * Spre deosebire de ROAS clasic calculat in MarketingService (care nu stie
     * de retururi), acesta scade valoarea retururilor aprobate SI costul logistic
     * al procesarii lor din venit. Demonstreaza ca un ROAS de 5:1 poate fi de fapt
     * mult mai mic dupa retururi.
     */
    public double roasReal(double venitBrut, double bugetReclame) {
        if (bugetReclame == 0) return 0;
        double rambursari   = totalRambursari();
        double costLogistic = totalCostLogisticRetururi();
        double venitNet     = venitBrut - rambursari - costLogistic;
        return Math.round(venitNet / bugetReclame * 10.0) / 10.0;
    }

    /** Diferenta intre ROAS aparent si ROAS real - arata "pierderea ascunsa" */
    public double pierdereROAS(double roasAparent, double bugetReclame) {
        double roasR = roasReal(0, bugetReclame); // va fi recalculat cu venit real
        return Math.round((roasAparent - roasR) * 10.0) / 10.0;
    }

    /**
     * Impact retururi pe sursa de trafic.
     * Arata cate retururi si ce valoare a pierdut fiecare canal (Instagram, TikTok etc.)
     */
    public List<Map<String, Object>> impactPerSursa() {
        List<Retur> aprobate = dupaStatus(Retur.StatusRetur.APROBAT);

        Map<String, List<Retur>> perSursa = aprobate.stream()
                .filter(r -> r.getSursaComanda() != null && !r.getSursaComanda().isBlank())
                .collect(Collectors.groupingBy(Retur::getSursaComanda));

        return perSursa.entrySet().stream()
                .map(e -> {
                    double valoare = e.getValue().stream()
                            .mapToDouble(Retur::calculeazaTotal).sum();
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("sursa",      e.getKey());
                    row.put("nrRetururi", e.getValue().size());
                    row.put("valoare",    Math.round(valoare * 100.0) / 100.0);
                    return row;
                })
                .sorted(Comparator.comparingDouble(
                        m -> -((Number) m.get("valoare")).doubleValue()))
                .collect(Collectors.toList());
    }

    /**
     * Impact retururi per influencer (cod promotional).
     * Arata ce influenceri au generat comenzi cu rata mare de retur.
     */
    public List<Map<String, Object>> impactPerInfluencer() {
        List<Retur> aprobate = dupaStatus(Retur.StatusRetur.APROBAT);

        Map<String, List<Retur>> perCod = aprobate.stream()
                .filter(r -> r.getCodPromotionalComanda() != null
                        && !r.getCodPromotionalComanda().isBlank())
                .collect(Collectors.groupingBy(Retur::getCodPromotionalComanda));

        return perCod.entrySet().stream()
                .map(e -> {
                    double valoare = e.getValue().stream()
                            .mapToDouble(Retur::calculeazaTotal).sum();
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("cod",        e.getKey());
                    row.put("nrRetururi", e.getValue().size());
                    row.put("valoare",    Math.round(valoare * 100.0) / 100.0);
                    return row;
                })
                .sorted(Comparator.comparingDouble(
                        m -> -((Number) m.get("valoare")).doubleValue()))
                .collect(Collectors.toList());
    }

    /**
     * Motive retururi grupate - pentru analiza operationala
     */
    public Map<String, Long> motiveGrupate() {
        return toate().stream()
                .filter(r -> r.getMotivRetur() != null && !r.getMotivRetur().isBlank())
                .collect(Collectors.groupingBy(Retur::getMotivRetur, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .collect(Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));
    }

    private Long uid() { return utilizatorService.getIdUtilizatorCurent(); }
}