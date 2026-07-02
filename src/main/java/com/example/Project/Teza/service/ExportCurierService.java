package com.example.Project.Teza.service;

import com.example.Project.Teza.model.Comanda;
import com.example.Project.Teza.model.ExportLinieCurier;
import com.example.Project.Teza.model.Produs;
import com.example.Project.Teza.model.Utilizator;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Genereaza fisiere XLSX de export AWB pentru fiecare curier, cu coloane DIFERITE
 * corespunzatoare formatelor de import in bulk ale fiecarei platforme.
 *
 * IMPORTANT: structura de coloane de mai jos reflecta campurile documentate public
 * de fiecare curier (manuale SelfAWB/WebExpress, FAQ-uri oficiale, integrari plugin).
 * Denumirile exacte ale coloanelor si valorile din liste derulante (ex. cod serviciu)
 * pot varia usor de la un cont la altul - la prima utilizare REALA cu un cont de
 * curier, comparati fisierul generat cu sablonul descarcat direct din contul
 * respectiv (SelfAWB / WebExpress / DPD eClient / Sameday / GLS) si ajustati daca
 * e nevoie denumirile de coloane sau codurile de serviciu.
 *
 * Formate implementate:
 *   FAN_COURIER - import AWB Fan Courier / SelfAWB (Tip Plata: R = Ramburs, PP = Platit)
 *   CARGUS      - import Cargus / WebExpress (Cod Postal OBLIGATORIU; Serviciu: Cont24/Cont48)
 *   DPD         - import DPD Romania eClient (Payment Type: 1 = ramburs, 0 = expeditor plateste)
 *   SAMEDAY     - import Sameday (Serviciu: 2 = curierat la adresa, 3 = Easybox/Locker)
 *   GLS         - import GLS Romania (CountryCode: RO fix, Cod Postal recomandat pentru tarifare)
 */
@Service
@RequiredArgsConstructor
public class ExportCurierService {

    private final ProdusService produsService;

    private static final Map<String, String> LOCALITATE_JUDET = Map.ofEntries(
            Map.entry("bucuresti",   "B"),
            Map.entry("cluj-napoca", "CJ"), Map.entry("cluj napoca", "CJ"), Map.entry("cluj", "CJ"),
            Map.entry("timisoara",   "TM"),
            Map.entry("iasi",        "IS"),
            Map.entry("constanta",   "CT"),
            Map.entry("craiova",     "DJ"),
            Map.entry("brasov",      "BV"),
            Map.entry("galati",      "GL"),
            Map.entry("ploiesti",    "PH"),
            Map.entry("oradea",      "BH"),
            Map.entry("braila",      "BR"),
            Map.entry("arad",        "AR"),
            Map.entry("pitesti",     "AG"),
            Map.entry("sibiu",       "SB"),
            Map.entry("bacau",       "BC"),
            Map.entry("targu mures", "MS"), Map.entry("targu-mures", "MS"),
            Map.entry("baia mare",   "MM"), Map.entry("baia-mare",   "MM"),
            Map.entry("buzau",       "BZ"),
            Map.entry("botosani",    "BT"),
            Map.entry("satu mare",   "SM"), Map.entry("satu-mare",   "SM"),
            Map.entry("ramnicu valcea", "VL"), Map.entry("rm. valcea", "VL"),
            Map.entry("drobeta turnu severin", "MH"),
            Map.entry("suceava",     "SV"),
            Map.entry("piatra neamt","NT"),
            Map.entry("targoviste",  "DB"),
            Map.entry("deva",        "HD"),
            Map.entry("resita",      "CS"),
            Map.entry("zalau",       "SJ"),
            Map.entry("slobozia",    "IL"),
            Map.entry("alexandria",  "TR"),
            Map.entry("giurgiu",     "GR"),
            Map.entry("calarasi",    "CL"),
            Map.entry("tulcea",      "TL"),
            Map.entry("vaslui",      "VS"),
            Map.entry("focsani",     "VN"),
            Map.entry("alba iulia",  "AB"), Map.entry("alba-iulia", "AB"),
            Map.entry("sfantu gheorghe", "CV"),
            Map.entry("dej",         "CJ"),
            Map.entry("bistrita",    "BN"),
            Map.entry("turda",       "CJ")
    );

    /**
     * Curierul (si eventualele controale vamale/de continut) nu au nicio nevoie
     * de codul nostru de gestiune intern (SKU) - le e relevanta o descriere
     * INTELIGIBILA a continutului coletului. De aceea, daca idProdus din comanda
     * corespunde unui SKU real din catalog, folosim DENUMIREA produsului in
     * descrierea trimisa curierului; codul SKU ramane doar uz intern.
     * Campul e oricum editabil manual in interfata, inainte de export.
     */
    public String continutImplicit(Comanda c) {
        if (c.getItems() == null || c.getItems().isEmpty()) return "Produse diverse";

        Map<String, String> denumireDupaSKU = new HashMap<>();
        for (Produs p : produsService.toateInclusivinactive()) {
            if (p.getCodSKU() != null && !p.getCodSKU().isBlank()) {
                denumireDupaSKU.put(p.getCodSKU().trim().toLowerCase(), p.getNume());
            }
        }

        StringBuilder sb = new StringBuilder();
        for (var item : c.getItems()) {
            if (sb.length() > 0) sb.append(", ");
            String idProdus = item.getIdProdus();
            String nume;
            if (idProdus != null && !idProdus.isBlank()) {
                String denumireGasita = denumireDupaSKU.get(idProdus.trim().toLowerCase());
                // Daca idProdus e un SKU cunoscut, folosim denumirea reala a produsului.
                // Altfel (text liber introdus manual), il folosim asa cum este.
                nume = denumireGasita != null ? denumireGasita : idProdus;
            } else {
                nume = "produs";
            }
            int cant = item.getCantitate() != null ? item.getCantitate() : 1;
            sb.append(nume).append(" x").append(cant);
        }
        String rezultat = sb.toString();
        return rezultat.length() > 120 ? rezultat.substring(0, 117) + "..." : rezultat;
    }

    /**
     * Export CSV pentru Fan Courier - SelfAWB importa exclusiv fisiere .csv, in formatul
     * lor OFICIAL de import in masa (47 de coloane, cu blocuri expeditor_/destinatar_).
     * Structura de mai jos respecta EXACT antetul descarcat din contul SelfAWB
     * (model_fisier_awb_intern.csv), NU un format simplificat.
     *
     * Limitari cunoscute ale datelor noastre (campuri lasate goale in CSV, completate
     * manual de utilizator daca SelfAWB le cere la import):
     *  - adresele (expeditor_strada / destinatar_strada) sunt pastrate ca text liber
     *    (strada + nr + bloc/etc. impreuna), pentru ca aplicatia nu retine separat
     *    nr/bloc/scara/etaj/apartament -> acele coloane individuale raman goale.
     *  - "Profil Firma" nu are momentan camp de cod postal -> expeditor_cod postal gol.
     *  - dimensiunile coletului (inaltime/latime/lungime) nu sunt urmarite -> goale.
     *  - cod_UIT (relevant doar pentru transport international/produse accizabile) -> gol.
     */
    public byte[] genereazaCsvFanCourier(List<ExportLinieCurier> linii, Utilizator firma) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(bos, java.nio.charset.StandardCharsets.UTF_8);
             org.apache.commons.csv.CSVPrinter printer = new org.apache.commons.csv.CSVPrinter(writer,
                     org.apache.commons.csv.CSVFormat.DEFAULT.builder()
                             .setHeader(
                                     "expeditor_nume", "expeditor_persoana_contact", "expeditor_telefon",
                                     "expeditor_fax", "expeditor_email", "expeditor_judet", "expeditor_localitatea",
                                     "expeditor_strada", "expeditor_Nr", "expeditor_cod postal", "expeditor_bloc",
                                     "expeditor_scara", "expeditor_etaj", "expeditor_apartament",
                                     "destinatar_nume", "destinatar_persoana_contact", "destinatar_telefon",
                                     "destinatar_fax", "destinatar_email", "destinatar_judet", "destinatar_localitatea",
                                     "destinatar_strada", "destinatar_nr", "destinatar_cod postal", "destinatar_bloc",
                                     "destinatar_scara", "destinatar_etaj", "destinatar_apartament",
                                     "tip_serviciu", "banca", "IBAN", "nr_plicuri", "nr_colete", "greutate",
                                     "plata_expeditie", "ramburs", "plata_ramburs_la", "valoare_declarata",
                                     "observatii", "continut", "inaltime_pachet", "latime_pachet", "lungime_pachet",
                                     "restituire", "optiuni", "expeditor_dropoff", "cod_UIT")
                             .build())) {

            // Adresa expeditorului (firma) - se repeta identic pe fiecare linie.
            // Preferam campurile noi, dedicate AWB-ului (judetFirma/localitateFirma/...),
            // completate manual de utilizator in Profil; daca nu sunt completate,
            // facem fallback la vechea metoda (parsare din adresaFirma, text liber).
            String judetExpeditor, localitateExpeditor, stradaExpeditor, numarExpeditor,
                    codPostalExpeditor, blocExpeditor, scaraExpeditor, etajExpeditor, apartamentExpeditor;
            if (firma != null && firma.getJudetFirma() != null && !firma.getJudetFirma().isBlank()) {
                judetExpeditor       = nvl(firma.getJudetFirma());
                localitateExpeditor  = nvl(firma.getLocalitateFirma());
                stradaExpeditor      = nvl(firma.getStradaFirma());
                numarExpeditor       = nvl(firma.getNumarFirma());
                codPostalExpeditor   = nvl(firma.getCodPostalFirma());
                blocExpeditor        = nvl(firma.getBlocFirma());
                scaraExpeditor       = nvl(firma.getScaraFirma());
                etajExpeditor        = nvl(firma.getEtajFirma());
                apartamentExpeditor  = nvl(firma.getApartamentFirma());
            } else {
                String[] ljExpeditor = extrageLocalitateJudet(nvl(firma != null ? firma.getAdresaFirma() : ""));
                localitateExpeditor  = ljExpeditor[0];
                judetExpeditor       = ljExpeditor[1];
                stradaExpeditor      = strip(adresaFaraLocalitate(nvl(firma != null ? firma.getAdresaFirma() : "")));
                numarExpeditor = codPostalExpeditor = blocExpeditor = scaraExpeditor = etajExpeditor = apartamentExpeditor = "";
            }

            for (ExportLinieCurier l : linii) {
                Comanda c = l.getComanda();
                String[] ljDestinatar = extrageLocalitateJudet(nvl(c.getAdresa()));
                boolean ramburs = c.getStatusPlata() == Comanda.StatusPlata.RAMBURS;

                // Parsam adresa destinatarului in componente separate Fan Courier
                String adresaDestinatar = nvl(c.getAdresa());
                // Eliminam mai intai orasul/judetul din adresa bruta, ca sa nu ramana in strada
                if (!ljDestinatar[0].isEmpty()) {
                    adresaDestinatar = adresaDestinatar.replaceAll("(?i),?\\s*" +
                            java.util.regex.Pattern.quote(ljDestinatar[0]) + "\\s*,?", " ").trim();
                }
                String[] adresaComp = parseazaAdresaRomaneasca(strip(adresaDestinatar));
                String stradaDest      = adresaComp[0];
                String nrDest          = adresaComp[1];
                String blocDest        = adresaComp[2];
                String scaraDest       = adresaComp[3];
                String etajDest        = adresaComp[4];
                String apartamentDest  = adresaComp[5];

                printer.printRecord(
                        // -- expeditor (firma ta) --
                        strip(firma != null ? nvl(firma.getNumeFirma()) : ""),
                        strip(firma != null ? nvl(firma.getPersoanaContactFirma()) : ""),
                        firma != null ? nvl(firma.getTelefonFirma()) : "",
                        "",
                        firma != null ? nvl(firma.getEmailFirma()) : "",
                        judetExpeditor,
                        localitateExpeditor,
                        stradaExpeditor,
                        numarExpeditor,
                        codPostalExpeditor,
                        blocExpeditor,
                        scaraExpeditor,
                        etajExpeditor,
                        apartamentExpeditor,
                        // -- destinatar (clientul) --
                        strip(c.getNumeClient()),
                        "",
                        nvl(c.getTelefon()),
                        "",
                        "",
                        ljDestinatar[1],
                        ljDestinatar[0],
                        stradaDest,
                        nrDest,
                        nvl(l.getCodPostal()),
                        blocDest, scaraDest, etajDest, apartamentDest,
                        // -- serviciu / plata --
                        "Standard",
                        firma != null ? nvl(firma.getBanca()) : "",
                        firma != null ? nvl(firma.getIban()) : "",
                        0,
                        l.getNrColete(),
                        l.getGreutateKg(),
                        "expeditor",
                        ramburs ? c.calculeazaTotal() : 0,
                        ramburs ? "Cont" : "",
                        c.calculeazaTotal(),
                        strip(nvl(c.getNote())),
                        strip(nvl(l.getContinut())),
                        "", "", "",
                        "Nu",
                        "",
                        "Nu",
                        ""
                );
            }
            printer.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Eroare la generarea fisierului CSV pentru Fan Courier", e);
        }
    }

    public byte[] genereazaExcel(List<ExportLinieCurier> linii, String curier) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            switch (curier) {
                case "FAN_COURIER" -> sheetFanCourier(wb, linii);
                case "CARGUS"      -> sheetCargus(wb, linii);
                case "DPD"         -> sheetDpd(wb, linii);
                case "SAMEDAY"     -> sheetSameday(wb, linii);
                case "GLS"         -> sheetGls(wb, linii);
                default            -> sheetGeneric(wb, linii);
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Eroare la generarea fisierului Excel de export curier", e);
        }
    }

    private void sheetFanCourier(XSSFWorkbook wb, List<ExportLinieCurier> linii) {
        ExcelSheetBuilder b = new ExcelSheetBuilder(wb, "Import AWB FanCourier", new String[]{
                "Nume Destinatar", "Telefon", "Judet", "Localitate", "Strada", "Cod Postal",
                "Plicuri", "Colete", "Greutate(Kg)", "Tip Serviciu", "Tip Plata", "Ramburs(Lei)",
                "Continut", "Referinta", "Observatii"
        });
        for (ExportLinieCurier l : linii) {
            Comanda c = l.getComanda();
            String[] lj = extrageLocalitateJudet(nvl(c.getAdresa()));
            boolean ramburs = c.getStatusPlata() == Comanda.StatusPlata.RAMBURS;
            b.randNou()
                    .text(strip(c.getNumeClient())).text(nvl(c.getTelefon()))
                    .text(lj[1]).text(lj[0])
                    .text(strip(adresaFaraLocalitate(nvl(c.getAdresa()))))
                    .text(nvl(l.getCodPostal()))
                    .number(0).number(l.getNrColete()).number(l.getGreutateKg())
                    .text("Standard").text(ramburs ? "R" : "PP")
                    .number(ramburs ? c.calculeazaTotal() : 0)
                    .text(strip(nvl(l.getContinut())))
                    .text(String.valueOf(c.getId()))
                    .text(strip(nvl(c.getNote())));
        }
        b.autoSize();
    }

    private void sheetCargus(XSSFWorkbook wb, List<ExportLinieCurier> linii) {
        ExcelSheetBuilder b = new ExcelSheetBuilder(wb, "Import Cargus", new String[]{
                "Recipient Name", "Phone", "County", "City", "Street", "Postal Code (OBLIGATORIU)",
                "Parcels", "Weight(Kg)", "Service", "Payment", "COD Amount", "Content", "Reference", "Notes"
        });
        for (ExportLinieCurier l : linii) {
            Comanda c = l.getComanda();
            String[] lj = extrageLocalitateJudet(nvl(c.getAdresa()));
            boolean ramburs = c.getStatusPlata() == Comanda.StatusPlata.RAMBURS;
            b.randNou()
                    .text(strip(c.getNumeClient())).text(nvl(c.getTelefon()))
                    .text(lj[1]).text(lj[0])
                    .text(strip(adresaFaraLocalitate(nvl(c.getAdresa()))))
                    .text(nvl(l.getCodPostal()))
                    .number(l.getNrColete()).number(l.getGreutateKg())
                    .text("Cont24").text(ramburs ? "Cash" : "Card")
                    .number(ramburs ? c.calculeazaTotal() : 0)
                    .text(strip(nvl(l.getContinut())))
                    .text(String.valueOf(c.getId()))
                    .text(strip(nvl(c.getNote())));
        }
        b.avertismentCodPostal();
        b.autoSize();
    }

    private void sheetDpd(XSSFWorkbook wb, List<ExportLinieCurier> linii) {
        ExcelSheetBuilder b = new ExcelSheetBuilder(wb, "Import DPD", new String[]{
                "Name", "Phone", "State", "City", "Street", "ZIP", "Parcels", "Weight(Kg)",
                "Service", "Payment Type", "COD Amount", "Content", "Reference", "Notes"
        });
        for (ExportLinieCurier l : linii) {
            Comanda c = l.getComanda();
            String[] lj = extrageLocalitateJudet(nvl(c.getAdresa()));
            boolean ramburs = c.getStatusPlata() == Comanda.StatusPlata.RAMBURS;
            b.randNou()
                    .text(strip(c.getNumeClient())).text(nvl(c.getTelefon()))
                    .text(lj[1]).text(lj[0])
                    .text(strip(adresaFaraLocalitate(nvl(c.getAdresa()))))
                    .text(nvl(l.getCodPostal()))
                    .number(l.getNrColete()).number(l.getGreutateKg())
                    .text("1").text(ramburs ? "1" : "0")
                    .number(ramburs ? c.calculeazaTotal() : 0)
                    .text(strip(nvl(l.getContinut())))
                    .text(String.valueOf(c.getId()))
                    .text(strip(nvl(c.getNote())));
        }
        b.autoSize();
    }

    private void sheetSameday(XSSFWorkbook wb, List<ExportLinieCurier> linii) {
        ExcelSheetBuilder b = new ExcelSheetBuilder(wb, "Import Sameday", new String[]{
                "Destinatar", "Telefon", "Judet", "Oras", "Strada", "Cod Postal",
                "Nr. Colete", "Greutate(Kg)", "Serviciu", "Valoare COD", "Continut",
                "Referinta", "Observatii"
        });
        for (ExportLinieCurier l : linii) {
            Comanda c = l.getComanda();
            String[] lj = extrageLocalitateJudet(nvl(c.getAdresa()));
            boolean ramburs = c.getStatusPlata() == Comanda.StatusPlata.RAMBURS;
            b.randNou()
                    .text(strip(c.getNumeClient())).text(nvl(c.getTelefon()))
                    .text(lj[1]).text(lj[0])
                    .text(strip(adresaFaraLocalitate(nvl(c.getAdresa()))))
                    .text(nvl(l.getCodPostal()))
                    .number(l.getNrColete()).number(l.getGreutateKg())
                    .text("2")
                    .number(ramburs ? c.calculeazaTotal() : 0)
                    .text(strip(nvl(l.getContinut())))
                    .text(String.valueOf(c.getId()))
                    .text(strip(nvl(c.getNote())));
        }
        b.autoSize();
    }

    private void sheetGls(XSSFWorkbook wb, List<ExportLinieCurier> linii) {
        ExcelSheetBuilder b = new ExcelSheetBuilder(wb, "Import GLS", new String[]{
                "Contact Name", "Phone", "Street", "City", "CountryCode", "ZipCode",
                "Parcels", "Weight(Kg)", "COD Amount", "Content", "Reference", "Notes"
        });
        for (ExportLinieCurier l : linii) {
            Comanda c = l.getComanda();
            String[] lj = extrageLocalitateJudet(nvl(c.getAdresa()));
            boolean ramburs = c.getStatusPlata() == Comanda.StatusPlata.RAMBURS;
            b.randNou()
                    .text(strip(c.getNumeClient())).text(nvl(c.getTelefon()))
                    .text(strip(adresaFaraLocalitate(nvl(c.getAdresa()))))
                    .text(lj[0]).text("RO")
                    .text(nvl(l.getCodPostal()))
                    .number(l.getNrColete()).number(l.getGreutateKg())
                    .number(ramburs ? c.calculeazaTotal() : 0)
                    .text(strip(nvl(l.getContinut())))
                    .text(String.valueOf(c.getId()))
                    .text(strip(nvl(c.getNote())));
        }
        b.autoSize();
    }

    private void sheetGeneric(XSSFWorkbook wb, List<ExportLinieCurier> linii) {
        ExcelSheetBuilder b = new ExcelSheetBuilder(wb, "Export Generic", new String[]{
                "Nume", "Telefon", "Judet", "Oras", "Strada", "Cod Postal",
                "Nr. Colete", "Greutate(Kg)", "Tip Plata", "Valoare COD", "Continut", "Note"
        });
        for (ExportLinieCurier l : linii) {
            Comanda c = l.getComanda();
            String[] lj = extrageLocalitateJudet(nvl(c.getAdresa()));
            boolean ramburs = c.getStatusPlata() == Comanda.StatusPlata.RAMBURS;
            b.randNou()
                    .text(strip(c.getNumeClient())).text(nvl(c.getTelefon()))
                    .text(lj[1]).text(lj[0])
                    .text(strip(adresaFaraLocalitate(nvl(c.getAdresa()))))
                    .text(nvl(l.getCodPostal()))
                    .number(l.getNrColete()).number(l.getGreutateKg())
                    .text(ramburs ? "Ramburs" : "Platit")
                    .number(ramburs ? c.calculeazaTotal() : 0)
                    .text(strip(nvl(l.getContinut())))
                    .text(strip(nvl(c.getNote())));
        }
        b.autoSize();
    }

    private String adresaFaraLocalitate(String adresa) {
        if (adresa == null || adresa.isBlank()) return "";
        String[] lj = extrageLocalitateJudet(adresa);
        if (lj[0].isEmpty()) return adresa;
        return adresa.replaceAll("(?i),?\\s*" + java.util.regex.Pattern.quote(lj[0]) + "\\s*,?", " ").trim();
    }

    private String[] extrageLocalitateJudet(String adresa) {
        if (adresa == null || adresa.isBlank()) return new String[]{"", ""};
        String adresaNorm = stripDiacritice(adresa).toLowerCase().trim();
        if (adresaNorm.contains("bucuresti")) {
            return new String[]{"Bucuresti", "B"};
        }
        String gasit = LOCALITATE_JUDET.keySet().stream()
                .sorted((a, b) -> b.length() - a.length())
                .filter(adresaNorm::contains)
                .findFirst()
                .orElse(null);
        if (gasit != null) {
            return new String[]{toTitleCase(gasit), LOCALITATE_JUDET.get(gasit)};
        }
        String[] tokeni = adresaNorm.split("[,\\s]+");
        String localitate = "";
        for (int i = tokeni.length - 1; i >= 0; i--) {
            String t = tokeni[i].replaceAll("[^a-z]", "");
            if (!t.isBlank() && !t.matches("nr|bl|ap|et|sc|sector|sectorul|str|bd|bdul|calea|aleea|\\d+")) {
                localitate = toTitleCase(t);
                break;
            }
        }
        return new String[]{localitate, ""};
    }

    public static String stripDiacritice(String input) {
        if (input == null) return "";
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{Mn}", "");
    }

    private String strip(String s) {
        return stripDiacritice(nvl(s));
    }

    private String toTitleCase(String s) {
        if (s == null || s.isBlank()) return s;
        String[] words = s.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isBlank())
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase()).append(" ");
        }
        return sb.toString().trim();
    }

    /**
     * Parseaza o adresa romaneasca in componente separate necesare AWB-ului Fan Courier.
     * Returneaza String[6]: [strada, nr, bloc, scara, etaj, apartament].
     *
     * Recunoaste urmatoarele formate frecvente:
     *   "Str. Victoriei nr. 10, bl. 3A, sc. 2, et. 4, ap. 12"
     *   "Strada Lalelelor nr. 14, bl. 3, ap. 8"
     *   "Str. Cuza Voda 45  700036"   (nr. lipit de strada, cod postal la sfarsit)
     *   "str. Tineretului 68, Iasi"   (nr inainte de virgula cu oras)
     *   "Calea Mosilor 190"            (fara bl/sc/et/ap)
     */
    public static String[] parseazaAdresaRomaneasca(String adresa) {
        String strada = "", nr = "", bloc = "", scara = "", etaj = "", apartament = "";
        if (adresa == null || adresa.isBlank()) return new String[]{strada, nr, bloc, scara, etaj, apartament};

        // Eliminam codul postal (6 cifre) daca apare in adresa
        String a = adresa.replaceAll("\\b\\d{6}\\b", "").trim();

        // Extragem bl/bloc
        java.util.regex.Matcher mBloc = java.util.regex.Pattern
                .compile("(?i)\\b(?:bl\\.?|bloc)\\s*([A-Z0-9]+)")
                .matcher(a);
        if (mBloc.find()) { bloc = mBloc.group(1).trim(); a = a.replace(mBloc.group(0), ""); }

        // Extragem sc/scara
        java.util.regex.Matcher mSc = java.util.regex.Pattern
                .compile("(?i)\\b(?:sc\\.?|scara)\\s*(\\d+)")
                .matcher(a);
        if (mSc.find()) { scara = mSc.group(1).trim(); a = a.replace(mSc.group(0), ""); }

        // Extragem et/etaj
        java.util.regex.Matcher mEt = java.util.regex.Pattern
                .compile("(?i)\\b(?:et\\.?|etaj)\\s*(\\d+)")
                .matcher(a);
        if (mEt.find()) { etaj = mEt.group(1).trim(); a = a.replace(mEt.group(0), ""); }

        // Extragem ap/apartament
        java.util.regex.Matcher mAp = java.util.regex.Pattern
                .compile("(?i)\\b(?:ap\\.?|apart\\.?|apartament)\\s*(\\d+)")
                .matcher(a);
        if (mAp.find()) { apartament = mAp.group(1).trim(); a = a.replace(mAp.group(0), ""); }

        // Extragem nr/numar explicit ("nr. 45") sau nr inainte de virgula/spatiu+oras ("68, Iasi")
        java.util.regex.Matcher mNrExplicit = java.util.regex.Pattern
                .compile("(?i)\\b(?:nr\\.?|numarul?)\\s*(\\d+[A-Za-z]?)")
                .matcher(a);
        if (mNrExplicit.find()) {
            nr = mNrExplicit.group(1).trim();
            a = a.replace(mNrExplicit.group(0), "");
        } else {
            // nr lipit la sfarsit inainte de virgula sau sfarsit de sir: "Str. X 45, Oras" sau "Str. X 45"
            java.util.regex.Matcher mNrVirgula = java.util.regex.Pattern
                    .compile("\\s+(\\d+[A-Za-z]?)\\s*(?:,|$)")
                    .matcher(a);
            if (mNrVirgula.find()) {
                nr = mNrVirgula.group(1).trim();
                a = a.replace(mNrVirgula.group(0), " ");
            }
        }

        // Ce ramane e strada - curatam virgule/spatii multiple
        strada = a.replaceAll("[,;]+", " ").replaceAll("\\s{2,}", " ").trim();

        return new String[]{strada, nr, bloc, scara, etaj, apartament};
    }

    private String nvl(String s) { return s != null ? s : ""; }

    private static class ExcelSheetBuilder {
        private final Sheet sheet;
        private final CellStyle headerStyle;
        private final CellStyle avertismentStyle;
        private final int nrColoane;
        private int randCurent = 1;
        private Row randInLucru;
        private int colCurenta;

        ExcelSheetBuilder(XSSFWorkbook wb, String numeSheet, String[] headere) {
            this.sheet = wb.createSheet(numeSheet);
            this.nrColoane = headere.length;

            Font fontHeader = wb.createFont();
            fontHeader.setBold(true);
            fontHeader.setColor(IndexedColors.WHITE.getIndex());
            this.headerStyle = wb.createCellStyle();
            headerStyle.setFont(fontHeader);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_50_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            Font fontAvertisment = wb.createFont();
            fontAvertisment.setBold(true);
            fontAvertisment.setColor(IndexedColors.DARK_RED.getIndex());
            this.avertismentStyle = wb.createCellStyle();
            avertismentStyle.setFont(fontAvertisment);

            Row header = sheet.createRow(0);
            header.setHeightInPoints(20);
            for (int i = 0; i < headere.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headere[i]);
                cell.setCellStyle(headerStyle);
            }
            sheet.createFreezePane(0, 1);
        }

        ExcelSheetBuilder randNou() {
            randInLucru = sheet.createRow(randCurent++);
            colCurenta = 0;
            return this;
        }

        ExcelSheetBuilder text(String valoare) {
            randInLucru.createCell(colCurenta++).setCellValue(valoare != null ? valoare : "");
            return this;
        }

        ExcelSheetBuilder number(double valoare) {
            randInLucru.createCell(colCurenta++).setCellValue(valoare);
            return this;
        }

        void avertismentCodPostal() {
            boolean lipsesteVreunul = false;
            for (int r = 1; r < randCurent; r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                Cell cell = row.getCell(5);
                if (cell == null || cell.getStringCellValue().isBlank()) { lipsesteVreunul = true; break; }
            }
            if (lipsesteVreunul) {
                Row avert = sheet.createRow(randCurent++ + 1);
                Cell c = avert.createCell(0);
                c.setCellValue("ATENTIE: cel putin o comanda nu are Cod Postal completat - Cargus respinge AWB-ul fara el!");
                c.setCellStyle(avertismentStyle);
            }
        }

        void autoSize() {
            for (int i = 0; i < nrColoane; i++) {
                sheet.autoSizeColumn(i);
                int latimeCurenta = sheet.getColumnWidth(i);
                sheet.setColumnWidth(i, Math.min(Math.max(latimeCurenta, 2200), 9000));
            }
        }
    }
}