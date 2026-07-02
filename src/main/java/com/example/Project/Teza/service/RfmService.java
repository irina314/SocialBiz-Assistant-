package com.example.Project.Teza.service;

import com.example.Project.Teza.model.Client;
import com.example.Project.Teza.model.Comanda;
import com.example.Project.Teza.repository.ComandaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Serviciu de calcul RFM (Recency, Frequency, Monetary)
 * Segmenteaza clientii in 5 categorii pe baza comportamentului de cumparare.
 *
 * Scorul RFM: fiecare dimensiune primeste 1-5 puncte -> total 3-15.
 * Segmente:
 *   VIP (Champion)   - R>=4, F>=4, M>=4  -> clienti fideli, valoare mare
 *   Fidel            - R>=3, F>=3        -> cumpara des, relativ recent
 *   Promitator       - R>=4, F<=2        -> nou sau reactivat recent
 *   La Risc          - R<=2, F>=3        -> cumpara des, acum absent
 *   Inactiv          - R=1, F=1        -> nu a mai cumparat de mult
 *
 * Formula CLV (Customer Lifetime Value):
 *   CLV = Valoare medie per comanda x Frecventa anualizata x Durata estimata (2 ani)
 *   unde Frecventa anualizata = nr. comenzi / max(1, ani_de_activitate)
 *
 * Aceasta formula este o aproximare determinista bazata exclusiv pe datele
 * istorice disponibile in sistem, fara modele probabilistice externe.
 */
@Service
@RequiredArgsConstructor
public class RfmService {

    private final ComandaRepository comandaRepository;
    private final UtilizatorService utilizatorService;

    /** Durata de viata estimata a clientului folosita in calculul CLV (ani). */
    private static final double DURATA_ESTIMATA_ANI = 2.0;

    public static class RfmScore {
        public final Client client;
        public final int    recency;        // zile de la ultima comanda
        public final int    frecventa;      // nr. comenzi totale
        public final double monetary;       // valoare totala RON
        public final int    scoreR;         // 1-5
        public final int    scoreF;         // 1-5
        public final int    scoreM;         // 1-5
        public final int    scorTotal;      // 3-15
        public final String segment;
        public final String segmentCuloare;
        public final String segmentIcon;
        public final double clv;            // Customer Lifetime Value estimat (RON)

        public RfmScore(Client client, int recency, int frecventa, double monetary,
                        int sR, int sF, int sM, double aniiDeActivitate) {
            this.client    = client;
            this.recency   = recency;
            this.frecventa = frecventa;
            this.monetary  = monetary;
            this.scoreR    = sR;
            this.scoreF    = sF;
            this.scoreM    = sM;
            this.scorTotal = sR + sF + sM;

            /*
             * CLV = Valoare_medie_per_comanda x Frecventa_anualizata x Durata_estimata
             *
             * Valoare_medie_per_comanda  = monetary / frecventa
             * Frecventa_anualizata       = frecventa / max(1, ani_de_activitate)
             * Durata_estimata            = DURATA_ESTIMATA_ANI (2 ani)
             *
             * Simplificat: CLV = (monetary / frecventa) x (frecventa / ani) x 2
             *                  = monetary / ani x 2
             * unde "ani" reprezinta perioada de la prima comanda pana in prezent.
             */
            double valMedie           = frecventa > 0 ? monetary / frecventa : 0;
            double aniEfectivi        = Math.max(1.0, aniiDeActivitate);
            double frecventaAnualizata = frecventa / aniEfectivi;
            this.clv = Math.round(valMedie * frecventaAnualizata * DURATA_ESTIMATA_ANI * 100.0) / 100.0;

            // Segmentare pe baza scorurilor R, F, M
            if (sR >= 4 && sF >= 4 && sM >= 4) {
                this.segment        = "VIP";
                this.segmentCuloare = "#6c47ff";
                this.segmentIcon    = "[VIP]";
            } else if (sR >= 3 && sF >= 3) {
                this.segment        = "Fidel";
                this.segmentCuloare = "#1D9E75";
                this.segmentIcon    = "[*]";
            } else if (sR >= 4 && sF <= 2) {
                this.segment        = "Promitator";
                this.segmentCuloare = "#378ADD";
                this.segmentIcon    = "[P]";
            } else if (sR <= 2 && sF >= 3) {
                this.segment        = "La Risc";
                this.segmentCuloare = "#f5a623";
                this.segmentIcon    = "[!]";
            } else {
                this.segment        = "Inactiv";
                this.segmentCuloare = "#E24B4A";
                this.segmentIcon    = "[I]";
            }
        }
    }

    public List<RfmScore> calculeazaRFM() {
        Long uid = utilizatorService.getIdUtilizatorCurent();
        List<Comanda> toateComenzi = comandaRepository.findByUtilizatorId(uid).stream()
                .filter(c -> c.getStatus() != Comanda.StatusComanda.ANULAT)
                .collect(Collectors.toList());

        // Grupam comenzile dupa telefon (identificatorul natural al clientului)
        Map<String, List<Comanda>> comenziPerClient = toateComenzi.stream()
                .filter(c -> c.getTelefon() != null && !c.getTelefon().isBlank())
                .collect(Collectors.groupingBy(Comanda::getTelefon));

        if (comenziPerClient.isEmpty()) return List.of();

        LocalDateTime acum = LocalDateTime.now();

        record ValoriBrute(String telefon, String nume,
                           int zileDeLaUltima, int frecventa,
                           double monetary, double aniiDeActivitate) {}

        List<ValoriBrute> valori = comenziPerClient.entrySet().stream()
                .map(e -> {
                    String tel       = e.getKey();
                    List<Comanda> cc = e.getValue();

                    // Recency: zile de la ultima comanda
                    int zile = (int) cc.stream()
                            .filter(c -> c.getData() != null)
                            .mapToLong(c -> ChronoUnit.DAYS.between(c.getData(), acum))
                            .min().orElse(999L);

                    // Frequency
                    int freq = cc.size();

                    // Monetary
                    double mon = cc.stream()
                            .mapToDouble(Comanda::calculeazaTotal).sum();

                    // Ani de activitate: de la prima comanda pana acum
                    double ani = cc.stream()
                            .filter(c -> c.getData() != null)
                            .mapToLong(c -> ChronoUnit.DAYS.between(c.getData(), acum))
                            .max()
                            .orElse(365L) / 365.0;

                    String nume = cc.stream()
                            .filter(c -> c.getNumeClient() != null)
                            .map(Comanda::getNumeClient)
                            .findFirst().orElse(tel);

                    return new ValoriBrute(tel, nume, zile, freq, mon, ani);
                })
                .collect(Collectors.toList());

        if (valori.isEmpty()) return List.of();

        // Calculam percentilele pentru scoring 1-5 pe baza distributiei reale
        int[]    recencyArr = valori.stream().mapToInt(v -> v.zileDeLaUltima()).sorted().toArray();
        int[]    frecvArr   = valori.stream().mapToInt(v -> v.frecventa()).sorted().toArray();
        double[] monArr     = valori.stream().mapToDouble(v -> v.monetary()).sorted().toArray();

        List<RfmScore> rezultate = new ArrayList<>();
        for (ValoriBrute v : valori) {
            int sR = scoreInvers(v.zileDeLaUltima(), recencyArr);
            int sF = scoreDirect(v.frecventa(),      frecvArr);
            int sM = scoreDirect((int) v.monetary(), toIntArray(monArr));

            Client clientVirtual = new Client();
            clientVirtual.setNume(v.nume());
            clientVirtual.setTelefon(v.telefon());

            rezultate.add(new RfmScore(
                    clientVirtual,
                    v.zileDeLaUltima(),
                    v.frecventa(),
                    v.monetary(),
                    sR, sF, sM,
                    v.aniiDeActivitate()
            ));
        }

        rezultate.sort((a, b) -> Integer.compare(b.scorTotal, a.scorTotal));
        return rezultate;
    }

    /** Rezumat pe segmente pentru dashboard */
    public Map<String, Long> rezumatSegmente() {
        return calculeazaRFM().stream()
                .collect(Collectors.groupingBy(r -> r.segment, Collectors.counting()));
    }

    // Scor 1-5 pentru valori unde MAI MIC = MAI BUN (recency)
    private int scoreInvers(int valoare, int[] sortedArr) {
        if (sortedArr.length == 0) return 3;
        double pct = percentila(valoare, sortedArr);
        if (pct <= 20) return 5;
        if (pct <= 40) return 4;
        if (pct <= 60) return 3;
        if (pct <= 80) return 2;
        return 1;
    }

    // Scor 1-5 pentru valori unde MAI MARE = MAI BUN (frecventa, monetary)
    private int scoreDirect(int valoare, int[] sortedArr) {
        if (sortedArr.length == 0) return 3;
        double pct = percentila(valoare, sortedArr);
        if (pct >= 80) return 5;
        if (pct >= 60) return 4;
        if (pct >= 40) return 3;
        if (pct >= 20) return 2;
        return 1;
    }

    private double percentila(double val, int[] sorted) {
        int n = sorted.length;
        if (n == 1) return 50.0;
        int rank = 0;
        for (int x : sorted) if (x <= val) rank++;
        return (double) rank / n * 100.0;
    }

    private int[] toIntArray(double[] d) {
        int[] r = new int[d.length];
        for (int i = 0; i < d.length; i++) r[i] = (int) d[i];
        return r;
    }
}