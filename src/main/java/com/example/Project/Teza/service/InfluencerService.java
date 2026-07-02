package com.example.Project.Teza.service;

import com.example.Project.Teza.model.Comanda;
import com.example.Project.Teza.model.Influencer;
import com.example.Project.Teza.repository.ComandaRepository;
import com.example.Project.Teza.repository.InfluencerRepository;
import com.example.Project.Teza.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InfluencerService {

    private final InfluencerRepository influencerRepository;
    private final ComandaRepository    comandaRepository;
    private final UtilizatorService    utilizatorService;

    // -- CRUD ----------------------------------------------

    public List<Influencer> toti() {
        return influencerRepository.findByUtilizatorId(uid());
    }

    public Influencer gaseste(Long id) {
        Influencer inf = influencerRepository.findById(id)
                .orElseThrow(() -> new ResursaNegasitaException("Influencer", id));
        if (!inf.getUtilizator().getId().equals(uid()))
            throw new AccesInterzisException("influencer");
        return inf;
    }

    public Influencer salveaza(Influencer inf) {
        if (inf.getUtilizator() == null)
            inf.setUtilizator(utilizatorService.getUtilizatorCurent());
        return influencerRepository.save(inf);
    }

    public void sterge(Long id) {
        gaseste(id);
        influencerRepository.deleteById(id);
    }

    // -- Statistici per influencer --------------------------

    /** DTO complet cu metrici calculate */
    public static class InfluencerStats {
        public final Influencer influencer;
        public final long nrComenzi;
        public final double venitTotal;
        public final double valoareMedieComanda;
        public final double comisionDatorat;
        public final String platformaLabel;

        public InfluencerStats(Influencer inf, long nrComenzi, double venit) {
            this.influencer           = inf;
            this.nrComenzi            = nrComenzi;
            this.venitTotal           = Math.round(venit * 100.0) / 100.0;
            this.valoareMedieComanda  = nrComenzi > 0
                    ? Math.round(venit / nrComenzi * 100.0) / 100.0 : 0;
            double proc               = inf.getComisionProcent() != null
                    ? inf.getComisionProcent() : 0.0;
            this.comisionDatorat      = Math.round(venit * proc / 100.0 * 100.0) / 100.0;
            this.platformaLabel       = inf.getPlatforma() != null ? inf.getPlatforma() : "-";
        }
    }

    public List<InfluencerStats> statisticiToate() {
        List<Comanda> comenzi = comenziNeanulate();
        List<Influencer> influenceri = toti();

        return influenceri.stream().map(inf -> {
                    String cod = inf.getCodPromotional();
                    List<Comanda> aleInfluencerului = comenzi.stream()
                            .filter(c -> cod != null
                                    && cod.equalsIgnoreCase(c.getCodPromotional()))
                            .collect(Collectors.toList());
                    double venit = aleInfluencerului.stream()
                            .mapToDouble(Comanda::calculeazaTotal).sum();
                    return new InfluencerStats(inf, aleInfluencerului.size(), venit);
                })
                .sorted(Comparator.comparingDouble((InfluencerStats s) -> s.venitTotal).reversed())
                .collect(Collectors.toList());
    }

    /** Sumar global */
    public double venitTotalGenerat() {
        return statisticiToate().stream()
                .mapToDouble(s -> s.venitTotal).sum();
    }

    public long comenziTotaleGenerate() {
        return statisticiToate().stream()
                .mapToLong(s -> s.nrComenzi).sum();
    }

    public double comisionTotalDatorat() {
        return statisticiToate().stream()
                .mapToDouble(s -> s.comisionDatorat).sum();
    }

    public long nrActivi() {
        return toti().stream()
                .filter(i -> i.getStatus() == Influencer.Status.ACTIV).count();
    }

    // -- Helper ----------------------------------------------

    private Long uid() { return utilizatorService.getIdUtilizatorCurent(); }

    private List<Comanda> comenziNeanulate() {
        return comandaRepository.findByUtilizatorId(uid()).stream()
                .filter(c -> c.getStatus() != com.example.Project.Teza.model.Comanda.StatusComanda.ANULAT)
                .collect(Collectors.toList());
    }
}