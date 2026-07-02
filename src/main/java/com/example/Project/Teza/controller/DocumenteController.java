package com.example.Project.Teza.controller;

import com.example.Project.Teza.exception.AplicatieException;
import com.example.Project.Teza.model.Comanda;
import com.example.Project.Teza.model.ComandaItem;
import com.example.Project.Teza.model.Utilizator;
import com.example.Project.Teza.service.ComandaService;
import com.example.Project.Teza.service.UtilizatorService;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.*;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/comenzi")
@RequiredArgsConstructor
public class DocumenteController {

    private final ComandaService    comandaService;
    private final UtilizatorService utilizatorService;

    private static final DateTimeFormatter FMT_DATA = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    // Culori
    private static final Color ALBASTRU    = new Color(24, 95, 165);
    private static final Color ALB         = Color.WHITE;
    private static final Color GRI_DESCHIS = new Color(245, 247, 250);
    private static final Color GRI_LINIE   = new Color(220, 225, 232);
    private static final Color ALB_ALBASTRU = new Color(180, 210, 240);

    // Fonturi TTF embedate (Unicode) - necesare pentru diacritice romanesti (a a i s t).
    // Fonturile standard PDF (Helvetica + CP1252) NU pot reprezenta aceste caractere.
    private static final String FONT_REGULAR = "fonts/DejaVuSans.ttf";
    private static final String FONT_BOLD    = "fonts/DejaVuSans-Bold.ttf";

    private BaseFont creeazaFont(String caleClasspath) throws Exception {
        try (InputStream is = new ClassPathResource(caleClasspath).getInputStream()) {
            byte[] fontBytes = is.readAllBytes();
            return BaseFont.createFont(caleClasspath, BaseFont.IDENTITY_H, BaseFont.EMBEDDED,
                    true, fontBytes, null);
        }
    }

    // =========================================================================
    //  AVIZ DE EXPEDITIE
    // =========================================================================
    @GetMapping("/{id}/aviz-pdf")
    public ResponseEntity<byte[]> avizPdf(@PathVariable Long id) throws Exception {

        Comanda    comanda = comandaService.gasesteDupaId(id);
        Utilizator firma   = utilizatorService.getUtilizatorCurent();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 40, 40, 50, 50);
        PdfWriter.getInstance(doc, baos);
        doc.open();

        BaseFont bf     = creeazaFont(FONT_REGULAR);
        BaseFont bfBold = creeazaFont(FONT_BOLD);

        Font fTitlu  = new Font(bfBold, 18, Font.NORMAL, ALB);
        Font fTabelH = new Font(bfBold,  9, Font.NORMAL, ALB);
        Font fTabelR = new Font(bf,       9, Font.NORMAL, Color.BLACK);
        Font fTotal  = new Font(bfBold,  11, Font.NORMAL, ALBASTRU);
        Font fMic    = new Font(bf,       8, Font.NORMAL, Color.GRAY);

        // -- Header --------------------------------------------------------
        PdfPTable header = new PdfPTable(2);
        header.setWidthPercentage(100);
        header.setWidths(new float[]{60, 40});

        PdfPCell cFirma = new PdfPCell();
        cFirma.setBorder(Rectangle.NO_BORDER);
        cFirma.setBackgroundColor(ALBASTRU);
        cFirma.setPadding(14);
        Paragraph pFirma = new Paragraph(safe(firma.getNumeFirma(), "Firma mea"), fTitlu);
        if (firma.getAdresaFirma() != null)
            pFirma.add(new Chunk("\n" + firma.getAdresaFirma(),
                    new Font(bf, 9, Font.NORMAL, ALB_ALBASTRU)));
        cFirma.addElement(pFirma);
        header.addCell(cFirma);

        PdfPCell cDoc = new PdfPCell();
        cDoc.setBorder(Rectangle.NO_BORDER);
        cDoc.setBackgroundColor(ALBASTRU);
        cDoc.setPadding(14);
        Paragraph pDoc = new Paragraph("AVIZ DE EXPEDITIE",
                new Font(bfBold, 14, Font.NORMAL, ALB));
        pDoc.setAlignment(Element.ALIGN_RIGHT);
        pDoc.add(new Chunk("\nNr. AV-" + String.format("%05d", comanda.getId()),
                new Font(bf, 10, Font.NORMAL, ALB_ALBASTRU)));
        pDoc.add(new Chunk("\nData: " +
                (comanda.getData() != null ? comanda.getData().format(FMT_DATA) : "-"),
                new Font(bf, 9, Font.NORMAL, ALB_ALBASTRU)));
        cDoc.addElement(pDoc);
        header.addCell(cDoc);
        doc.add(header);
        doc.add(new Paragraph(" "));

        // -- Expeditor / Destinatar ----------------------------------------
        PdfPTable adrese = new PdfPTable(2);
        adrese.setWidthPercentage(100);
        adrese.setWidths(new float[]{50, 50});
        adrese.addCell(blocAdresa(bfBold, bf, "EXPEDITOR", new String[]{
                safe(firma.getNumeFirma(), "-"),
                firma.getCui()          != null ? "CUI: "      + firma.getCui()          : null,
                firma.getAdresaFirma()  != null ? firma.getAdresaFirma()                 : null,
                firma.getTelefonFirma() != null ? firma.getTelefonFirma()                : null,
                firma.getEmailFirma()   != null ? firma.getEmailFirma()                  : null
        }));
        adrese.addCell(blocAdresa(bfBold, bf, "DESTINATAR", new String[]{
                safe(comanda.getNumeClient(), "-"),
                comanda.getTelefon() != null ? "Tel: " + comanda.getTelefon() : null,
                comanda.getAdresa()  != null ? comanda.getAdresa()            : null
        }));
        doc.add(adrese);
        doc.add(new Paragraph(" "));

        // -- Info comanda --------------------------------------------------
        PdfPTable info = new PdfPTable(4);
        info.setWidthPercentage(100);
        addInfoCell(info, "Comanda nr.",   "#" + comanda.getId(),                             bfBold, bf);
        addInfoCell(info, "Status",        comanda.getStatus() != null ? comanda.getStatus().name() : "-", bfBold, bf);
        addInfoCell(info, "Plata",         safe(comanda.getMetodaPlata(), "-"),               bfBold, bf);
        addInfoCell(info, "Sursa",         safe(comanda.getSursa(), "-"),                     bfBold, bf);
        doc.add(info);
        doc.add(new Paragraph(" "));

        // -- Tabel produse -------------------------------------------------
        PdfPTable tabel = new PdfPTable(5);
        tabel.setWidthPercentage(100);
        tabel.setWidths(new float[]{8, 38, 14, 16, 18});
        for (String col : new String[]{"Nr.", "Produs", "Cant.", "Pret unit.", "Total"}) {
            PdfPCell c = new PdfPCell(new Phrase(col, fTabelH));
            c.setBackgroundColor(ALBASTRU);
            c.setPadding(6);
            c.setBorder(Rectangle.NO_BORDER);
            c.setHorizontalAlignment(Element.ALIGN_CENTER);
            tabel.addCell(c);
        }

        int nr = 1;
        double total = 0;
        for (ComandaItem item : comanda.getItems()) {
            Color bg = (nr % 2 == 0) ? GRI_DESCHIS : ALB;
            double subtotal = item.calculeazaTotal();
            total += subtotal;
            addRandTabel(tabel, String.valueOf(nr++),                              bg, fTabelR, Element.ALIGN_CENTER);
            addRandTabel(tabel, safe(item.getIdProdus(), "(produs)"),              bg, fTabelR, Element.ALIGN_LEFT);
            addRandTabel(tabel, String.valueOf(item.getCantitate()),               bg, fTabelR, Element.ALIGN_CENTER);
            addRandTabel(tabel, String.format("%.2f lei", item.getPretUnitar()),   bg, fTabelR, Element.ALIGN_RIGHT);
            addRandTabel(tabel, String.format("%.2f lei", subtotal),               bg, fTabelR, Element.ALIGN_RIGHT);
        }
        doc.add(tabel);

        // -- Total ---------------------------------------------------------
        PdfPTable totalTabel = new PdfPTable(2);
        totalTabel.setWidthPercentage(38);
        totalTabel.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totalTabel.setSpacingBefore(4);
        PdfPCell cTL = new PdfPCell(new Phrase("TOTAL DE PLATA:", fTotal));
        cTL.setBorder(Rectangle.TOP); cTL.setBorderColor(ALBASTRU);
        cTL.setPaddingTop(6); cTL.setPaddingBottom(6);
        totalTabel.addCell(cTL);
        PdfPCell cTV = new PdfPCell(new Phrase(String.format("%.2f lei", total), fTotal));
        cTV.setBorder(Rectangle.TOP); cTV.setBorderColor(ALBASTRU);
        cTV.setHorizontalAlignment(Element.ALIGN_RIGHT);
        cTV.setPaddingTop(6); cTV.setPaddingBottom(6);
        totalTabel.addCell(cTV);
        doc.add(totalTabel);

        // -- Note + footer -------------------------------------------------
        if (comanda.getNote() != null && !comanda.getNote().isBlank()) {
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("Observatii: " + comanda.getNote(),
                    new Font(bf, 9, Font.ITALIC, Color.DARK_GRAY)));
        }
        doc.add(new Paragraph(" "));
        Paragraph footer = new Paragraph(
                "Document generat automat de SocialBiz Assistant - " +
                        java.time.LocalDate.now().format(FMT_DATA), fMic);
        footer.setAlignment(Element.ALIGN_CENTER);
        doc.add(footer);
        doc.close();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"aviz-expeditie-" + comanda.getId() + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(baos.toByteArray());
    }

    // =========================================================================
    //  FACTURA PROFORMA
    // =========================================================================
    @GetMapping("/{id}/proforma-pdf")
    public ResponseEntity<byte[]> proformaPdf(@PathVariable Long id) throws Exception {

        Comanda    comanda = comandaService.gasesteDupaId(id);
        Utilizator firma   = utilizatorService.getUtilizatorCurent();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 40, 40, 50, 50);
        PdfWriter.getInstance(doc, baos);
        doc.open();

        BaseFont bf     = creeazaFont(FONT_REGULAR);
        BaseFont bfBold = creeazaFont(FONT_BOLD);

        Font fTitlu  = new Font(bfBold, 18, Font.NORMAL, ALB);
        Font fTabelH = new Font(bfBold,  9, Font.NORMAL, ALB);
        Font fTabelR = new Font(bf,       9, Font.NORMAL, Color.BLACK);
        Font fTotal  = new Font(bfBold,  11, Font.NORMAL, ALBASTRU);
        Font fMic    = new Font(bf,       8, Font.NORMAL, Color.GRAY);

        // -- Header --------------------------------------------------------
        PdfPTable header = new PdfPTable(2);
        header.setWidthPercentage(100);
        header.setWidths(new float[]{60, 40});

        PdfPCell cFirma = new PdfPCell();
        cFirma.setBorder(Rectangle.NO_BORDER);
        cFirma.setBackgroundColor(ALBASTRU);
        cFirma.setPadding(14);
        Paragraph pFirma = new Paragraph(safe(firma.getNumeFirma(), "Firma mea"), fTitlu);
        if (firma.getCui() != null)
            pFirma.add(new Chunk("\nCUI: " + firma.getCui(),
                    new Font(bf, 9, Font.NORMAL, ALB_ALBASTRU)));
        if (firma.getNrRegCom() != null)
            pFirma.add(new Chunk("  |  Reg.Com: " + firma.getNrRegCom(),
                    new Font(bf, 9, Font.NORMAL, ALB_ALBASTRU)));
        cFirma.addElement(pFirma);
        header.addCell(cFirma);

        PdfPCell cDoc = new PdfPCell();
        cDoc.setBorder(Rectangle.NO_BORDER);
        cDoc.setBackgroundColor(ALBASTRU);
        cDoc.setPadding(14);
        Paragraph pDoc = new Paragraph("FACTURA PROFORMA",
                new Font(bfBold, 13, Font.NORMAL, ALB));
        pDoc.setAlignment(Element.ALIGN_RIGHT);
        pDoc.add(new Chunk("\nNr. PRF-" + String.format("%05d", comanda.getId()),
                new Font(bf, 10, Font.NORMAL, ALB_ALBASTRU)));
        pDoc.add(new Chunk("\nData: " +
                (comanda.getData() != null ? comanda.getData().format(FMT_DATA) : "-"),
                new Font(bf, 9, Font.NORMAL, ALB_ALBASTRU)));
        String scadenta = comanda.getData() != null
                ? comanda.getData().plusDays(14).format(FMT_DATA) : "-";
        pDoc.add(new Chunk("\nScadenta: " + scadenta,
                new Font(bf, 9, Font.NORMAL, new Color(255, 200, 100))));
        cDoc.addElement(pDoc);
        header.addCell(cDoc);
        doc.add(header);
        doc.add(new Paragraph(" "));

        // -- Furnizor / Cumparator -----------------------------------------
        PdfPTable adrese = new PdfPTable(2);
        adrese.setWidthPercentage(100);
        adrese.setWidths(new float[]{50, 50});
        adrese.addCell(blocAdresa(bfBold, bf, "FURNIZOR", new String[]{
                safe(firma.getNumeFirma(), "-"),
                firma.getCui()          != null ? "CUI: "       + firma.getCui()         : null,
                firma.getNrRegCom()     != null ? "Reg.Com: "   + firma.getNrRegCom()    : null,
                firma.getAdresaFirma()  != null ? firma.getAdresaFirma()                 : null,
                firma.getTelefonFirma() != null ? firma.getTelefonFirma()                : null,
                firma.getEmailFirma()   != null ? firma.getEmailFirma()                  : null
        }));
        adrese.addCell(blocAdresa(bfBold, bf, "CUMPARATOR", new String[]{
                safe(comanda.getNumeClient(), "-"),
                comanda.getTelefon() != null ? "Tel: " + comanda.getTelefon() : null,
                comanda.getAdresa()  != null ? comanda.getAdresa()            : null
        }));
        doc.add(adrese);
        doc.add(new Paragraph(" "));

        // -- Info plata ----------------------------------------------------
        PdfPTable info = new PdfPTable(3);
        info.setWidthPercentage(100);
        addInfoCell(info, "Metoda plata", safe(comanda.getMetodaPlata(), "-"), bfBold, bf);
        addInfoCell(info, "Banca",        safe(firma.getBanca(), "-"),         bfBold, bf);
        addInfoCell(info, "IBAN",         safe(firma.getIban(),  "-"),         bfBold, bf);
        doc.add(info);
        doc.add(new Paragraph(" "));

        // -- Tabel produse -------------------------------------------------
        PdfPTable tabel = new PdfPTable(6);
        tabel.setWidthPercentage(100);
        tabel.setWidths(new float[]{7, 30, 12, 14, 10, 17});
        for (String col : new String[]{"Nr.", "Produs", "Cant.", "Pret unit.", "TVA%", "Total c/TVA"}) {
            PdfPCell c = new PdfPCell(new Phrase(col, fTabelH));
            c.setBackgroundColor(ALBASTRU);
            c.setPadding(6);
            c.setBorder(Rectangle.NO_BORDER);
            c.setHorizontalAlignment(Element.ALIGN_CENTER);
            tabel.addCell(c);
        }

        int nr = 1;
        double totalFara = 0, totalCu = 0;
        for (ComandaItem item : comanda.getItems()) {
            Color bg = (nr % 2 == 0) ? GRI_DESCHIS : ALB;
            double tvaRate   = item.getCotaTVA() != null ? item.getCotaTVA().getValoare() : 0;
            double subtotalF = (item.getPretUnitar() != null ? item.getPretUnitar() : 0)
                    * (item.getCantitate()  != null ? item.getCantitate()  : 1);
            double subtotalC = item.calculeazaTotal();
            totalFara += subtotalF;
            totalCu   += subtotalC;

            addRandTabel(tabel, String.valueOf(nr++),                                        bg, fTabelR, Element.ALIGN_CENTER);
            addRandTabel(tabel, safe(item.getIdProdus(), "(produs)"),                        bg, fTabelR, Element.ALIGN_LEFT);
            addRandTabel(tabel, String.valueOf(item.getCantitate()),                          bg, fTabelR, Element.ALIGN_CENTER);
            addRandTabel(tabel, String.format("%.2f lei", item.getPretUnitar()),             bg, fTabelR, Element.ALIGN_RIGHT);
            addRandTabel(tabel, String.format("%.0f%%",  tvaRate * 100),                    bg, fTabelR, Element.ALIGN_CENTER);
            addRandTabel(tabel, String.format("%.2f lei", subtotalC),                        bg, fTabelR, Element.ALIGN_RIGHT);
        }
        doc.add(tabel);

        // -- Sumar TVA -----------------------------------------------------
        PdfPTable sumar = new PdfPTable(2);
        sumar.setWidthPercentage(36);
        sumar.setHorizontalAlignment(Element.ALIGN_RIGHT);
        sumar.setSpacingBefore(4);
        addLinieSumar(sumar, "Subtotal fara TVA:", String.format("%.2f lei", totalFara), bf, bfBold, false);
        addLinieSumar(sumar, "TVA:",               String.format("%.2f lei", totalCu - totalFara), bf, bfBold, false);
        addLinieSumar(sumar, "TOTAL DE PLATA:",    String.format("%.2f lei", totalCu),  bf, bfBold, true);
        doc.add(sumar);

        // -- Mentiune + footer ---------------------------------------------
        doc.add(new Paragraph(" "));
        Paragraph mentiune = new Paragraph(
                "[!] Aceasta este o FACTURA PROFORMA si nu constituie document fiscal. " +
                        "Factura fiscala va fi emisa dupa confirmarea platii.",
                new Font(bf, 8, Font.ITALIC, new Color(160, 120, 0)));
        mentiune.setAlignment(Element.ALIGN_CENTER);
        doc.add(mentiune);

        if (comanda.getNote() != null && !comanda.getNote().isBlank()) {
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("Observatii: " + comanda.getNote(),
                    new Font(bf, 9, Font.ITALIC, Color.DARK_GRAY)));
        }
        doc.add(new Paragraph(" "));
        Paragraph footer = new Paragraph(
                "Document generat automat de SocialBiz Assistant - " +
                        java.time.LocalDate.now().format(FMT_DATA), fMic);
        footer.setAlignment(Element.ALIGN_CENTER);
        doc.add(footer);
        doc.close();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"proforma-" + comanda.getId() + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(baos.toByteArray());
    }

    // =========================================================================
    //  FACTURA FISCALA (pentru comenzi deja achitate)
    // =========================================================================
    @GetMapping("/{id}/factura-pdf")
    public ResponseEntity<byte[]> facturaPdf(@PathVariable Long id) throws Exception {

        Comanda    comanda = comandaService.gasesteDupaId(id);
        Utilizator firma   = utilizatorService.getUtilizatorCurent();

        if (!comanda.esteAchitata()) {
            throw new AplicatieException(HttpStatus.BAD_REQUEST,
                    "Comanda nu este achitata",
                    "Nu se poate emite factura fiscala pentru o comanda neachitata.",
                    "Foloseste factura proforma pana la confirmarea platii, sau marcheaza " +
                            "comanda drept achitata din pagina de comenzi.");
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 40, 40, 50, 50);
        PdfWriter.getInstance(doc, baos);
        doc.open();

        BaseFont bf     = creeazaFont(FONT_REGULAR);
        BaseFont bfBold = creeazaFont(FONT_BOLD);

        Font fTitlu  = new Font(bfBold, 18, Font.NORMAL, ALB);
        Font fTabelH = new Font(bfBold,  9, Font.NORMAL, ALB);
        Font fTabelR = new Font(bf,       9, Font.NORMAL, Color.BLACK);
        Font fTotal  = new Font(bfBold,  11, Font.NORMAL, ALBASTRU);
        Font fMic    = new Font(bf,       8, Font.NORMAL, Color.GRAY);

        // -- Header --------------------------------------------------------
        PdfPTable header = new PdfPTable(2);
        header.setWidthPercentage(100);
        header.setWidths(new float[]{60, 40});

        PdfPCell cFirma = new PdfPCell();
        cFirma.setBorder(Rectangle.NO_BORDER);
        cFirma.setBackgroundColor(ALBASTRU);
        cFirma.setPadding(14);
        Paragraph pFirma = new Paragraph(safe(firma.getNumeFirma(), "Firma mea"), fTitlu);
        if (firma.getCui() != null)
            pFirma.add(new Chunk("\nCUI: " + firma.getCui(),
                    new Font(bf, 9, Font.NORMAL, ALB_ALBASTRU)));
        if (firma.getNrRegCom() != null)
            pFirma.add(new Chunk("  |  Reg.Com: " + firma.getNrRegCom(),
                    new Font(bf, 9, Font.NORMAL, ALB_ALBASTRU)));
        cFirma.addElement(pFirma);
        header.addCell(cFirma);

        PdfPCell cDoc = new PdfPCell();
        cDoc.setBorder(Rectangle.NO_BORDER);
        cDoc.setBackgroundColor(ALBASTRU);
        cDoc.setPadding(14);
        Paragraph pDoc = new Paragraph("FACTURA FISCALA",
                new Font(bfBold, 13, Font.NORMAL, ALB));
        pDoc.setAlignment(Element.ALIGN_RIGHT);
        pDoc.add(new Chunk("\nNr. FAC-" + String.format("%05d", comanda.getId()),
                new Font(bf, 10, Font.NORMAL, ALB_ALBASTRU)));
        pDoc.add(new Chunk("\nData: " +
                (comanda.getData() != null ? comanda.getData().format(FMT_DATA) : "-"),
                new Font(bf, 9, Font.NORMAL, ALB_ALBASTRU)));
        pDoc.add(new Chunk("\nStatus: ACHITATA",
                new Font(bf, 9, Font.NORMAL, new Color(140, 220, 170))));
        cDoc.addElement(pDoc);
        header.addCell(cDoc);
        doc.add(header);
        doc.add(new Paragraph(" "));

        // -- Furnizor / Cumparator -----------------------------------------
        PdfPTable adrese = new PdfPTable(2);
        adrese.setWidthPercentage(100);
        adrese.setWidths(new float[]{50, 50});
        adrese.addCell(blocAdresa(bfBold, bf, "FURNIZOR", new String[]{
                safe(firma.getNumeFirma(), "-"),
                firma.getCui()          != null ? "CUI: "       + firma.getCui()         : null,
                firma.getNrRegCom()     != null ? "Reg.Com: "   + firma.getNrRegCom()    : null,
                firma.getAdresaFirma()  != null ? firma.getAdresaFirma()                 : null,
                firma.getTelefonFirma() != null ? firma.getTelefonFirma()                : null,
                firma.getEmailFirma()   != null ? firma.getEmailFirma()                  : null
        }));
        adrese.addCell(blocAdresa(bfBold, bf, "CUMPARATOR", new String[]{
                safe(comanda.getNumeClient(), "-"),
                comanda.getTelefon() != null ? "Tel: " + comanda.getTelefon() : null,
                comanda.getAdresa()  != null ? comanda.getAdresa()            : null
        }));
        doc.add(adrese);
        doc.add(new Paragraph(" "));

        // -- Info plata ----------------------------------------------------
        PdfPTable info = new PdfPTable(3);
        info.setWidthPercentage(100);
        addInfoCell(info, "Metoda plata", safe(comanda.getMetodaPlata(), "-"), bfBold, bf);
        addInfoCell(info, "Banca",        safe(firma.getBanca(), "-"),         bfBold, bf);
        addInfoCell(info, "IBAN",         safe(firma.getIban(),  "-"),         bfBold, bf);
        doc.add(info);
        doc.add(new Paragraph(" "));

        // -- Tabel produse -------------------------------------------------
        PdfPTable tabel = new PdfPTable(6);
        tabel.setWidthPercentage(100);
        tabel.setWidths(new float[]{7, 30, 12, 14, 10, 17});
        for (String col : new String[]{"Nr.", "Produs", "Cant.", "Pret unit.", "TVA%", "Total c/TVA"}) {
            PdfPCell c = new PdfPCell(new Phrase(col, fTabelH));
            c.setBackgroundColor(ALBASTRU);
            c.setPadding(6);
            c.setBorder(Rectangle.NO_BORDER);
            c.setHorizontalAlignment(Element.ALIGN_CENTER);
            tabel.addCell(c);
        }

        int nr = 1;
        double totalFara = 0, totalCu = 0;
        for (ComandaItem item : comanda.getItems()) {
            Color bg = (nr % 2 == 0) ? GRI_DESCHIS : ALB;
            double tvaRate   = item.getCotaTVA() != null ? item.getCotaTVA().getValoare() : 0;
            double subtotalF = (item.getPretUnitar() != null ? item.getPretUnitar() : 0)
                    * (item.getCantitate()  != null ? item.getCantitate()  : 1);
            double subtotalC = item.calculeazaTotal();
            totalFara += subtotalF;
            totalCu   += subtotalC;

            addRandTabel(tabel, String.valueOf(nr++),                                        bg, fTabelR, Element.ALIGN_CENTER);
            addRandTabel(tabel, safe(item.getIdProdus(), "(produs)"),                        bg, fTabelR, Element.ALIGN_LEFT);
            addRandTabel(tabel, String.valueOf(item.getCantitate()),                          bg, fTabelR, Element.ALIGN_CENTER);
            addRandTabel(tabel, String.format("%.2f lei", item.getPretUnitar()),             bg, fTabelR, Element.ALIGN_RIGHT);
            addRandTabel(tabel, String.format("%.0f%%",  tvaRate * 100),                    bg, fTabelR, Element.ALIGN_CENTER);
            addRandTabel(tabel, String.format("%.2f lei", subtotalC),                        bg, fTabelR, Element.ALIGN_RIGHT);
        }
        doc.add(tabel);

        // -- Sumar TVA -----------------------------------------------------
        PdfPTable sumar = new PdfPTable(2);
        sumar.setWidthPercentage(36);
        sumar.setHorizontalAlignment(Element.ALIGN_RIGHT);
        sumar.setSpacingBefore(4);
        addLinieSumar(sumar, "Subtotal fara TVA:", String.format("%.2f lei", totalFara), bf, bfBold, false);
        addLinieSumar(sumar, "TVA:",               String.format("%.2f lei", totalCu - totalFara), bf, bfBold, false);
        addLinieSumar(sumar, "TOTAL ACHITAT:",     String.format("%.2f lei", totalCu),  bf, bfBold, true);
        doc.add(sumar);

        // -- Mentiune + footer ---------------------------------------------
        doc.add(new Paragraph(" "));
        Paragraph mentiune = new Paragraph(
                "Factura achitata integral. Document cu valoare fiscala.",
                new Font(bf, 8, Font.ITALIC, new Color(60, 140, 90)));
        mentiune.setAlignment(Element.ALIGN_CENTER);
        doc.add(mentiune);

        if (comanda.getNote() != null && !comanda.getNote().isBlank()) {
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("Observatii: " + comanda.getNote(),
                    new Font(bf, 9, Font.ITALIC, Color.DARK_GRAY)));
        }
        doc.add(new Paragraph(" "));
        Paragraph footer = new Paragraph(
                "Document generat automat de SocialBiz Assistant - " +
                        java.time.LocalDate.now().format(FMT_DATA), fMic);
        footer.setAlignment(Element.ALIGN_CENTER);
        doc.add(footer);
        doc.close();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"factura-" + comanda.getId() + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(baos.toByteArray());
    }

    // =========================================================================
    //  HELPERS
    // =========================================================================
    private String safe(String val, String fallback) {
        return (val != null && !val.isBlank()) ? val : fallback;
    }

    private PdfPCell blocAdresa(BaseFont bfBold, BaseFont bf, String titlu, String[] linii) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(GRI_LINIE);
        cell.setBackgroundColor(GRI_DESCHIS);
        cell.setPadding(10);
        Paragraph p = new Paragraph();
        p.add(new Chunk(titlu + "\n", new Font(bfBold, 9, Font.NORMAL, ALBASTRU)));
        for (String linie : linii) {
            if (linie != null && !linie.isBlank())
                p.add(new Chunk(linie + "\n", new Font(bf, 10, Font.NORMAL, Color.BLACK)));
        }
        cell.addElement(p);
        return cell;
    }

    private void addInfoCell(PdfPTable t, String label, String val,
                             BaseFont bfBold, BaseFont bf) {
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.BOX);
        c.setBorderColor(GRI_LINIE);
        c.setPadding(7);
        Paragraph p = new Paragraph();
        p.add(new Chunk(label + "\n", new Font(bf, 8, Font.NORMAL, Color.GRAY)));
        p.add(new Chunk(val,          new Font(bfBold, 10, Font.NORMAL, Color.BLACK)));
        c.addElement(p);
        t.addCell(c);
    }

    private void addRandTabel(PdfPTable t, String text, Color bg, Font f, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text, f));
        c.setBackgroundColor(bg);
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(GRI_LINIE);
        c.setPadding(5);
        c.setHorizontalAlignment(align);
        t.addCell(c);
    }

    private void addLinieSumar(PdfPTable t, String label, String val,
                               BaseFont bf, BaseFont bfBold, boolean accent) {
        Font fL = accent ? new Font(bfBold, 10, Font.NORMAL, ALBASTRU)
                : new Font(bf,      9, Font.NORMAL, Color.DARK_GRAY);
        Font fV = accent ? new Font(bfBold, 10, Font.NORMAL, ALBASTRU)
                : new Font(bf,      9, Font.NORMAL, Color.BLACK);
        int border = accent ? Rectangle.TOP | Rectangle.BOTTOM : Rectangle.NO_BORDER;

        PdfPCell cL = new PdfPCell(new Phrase(label, fL));
        cL.setBorder(border); cL.setBorderColor(ALBASTRU); cL.setPadding(4);
        t.addCell(cL);

        PdfPCell cV = new PdfPCell(new Phrase(val, fV));
        cV.setBorder(border); cV.setBorderColor(ALBASTRU);
        cV.setHorizontalAlignment(Element.ALIGN_RIGHT); cV.setPadding(4);
        t.addCell(cV);
    }
}