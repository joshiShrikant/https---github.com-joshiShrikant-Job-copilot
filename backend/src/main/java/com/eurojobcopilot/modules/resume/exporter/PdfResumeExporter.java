package com.eurojobcopilot.modules.resume.exporter;

import com.eurojobcopilot.modules.resume.dto.TailoredResumeDataDto;
import com.eurojobcopilot.modules.resume.dto.TailoredResumeEducationDto;
import com.eurojobcopilot.modules.resume.dto.TailoredResumeExperienceDto;
import com.eurojobcopilot.modules.resume.dto.TailoredResumeHeaderDto;
import com.eurojobcopilot.modules.resume.dto.TailoredResumeProjectDto;
import com.eurojobcopilot.modules.resume.entity.ResumeTemplate;
import com.eurojobcopilot.modules.resume.exception.ResumeStorageException;
import com.eurojobcopilot.modules.resume.template.ResumeExportTextFormatter;
import com.eurojobcopilot.modules.resume.template.ResumeExportTextFormatter.LabeledUrl;
import com.eurojobcopilot.modules.resume.template.TailoredResumeTemplateSpec;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Renders structured tailored resume drafts into multi-page A4 ATS-readable PDF documents
 * via Apache PDFBox according to the selected {@link ResumeTemplate} layout spec.
 */
@Component
public class PdfResumeExporter {

    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final float MARGIN_X = 44f;
    private static final float MARGIN_TOP = 42f;
    private static final float MARGIN_BOTTOM = 44f;
    private static final float CONTENT_WIDTH = PAGE_WIDTH - (2 * MARGIN_X);
    private static final float MIN_Y = MARGIN_BOTTOM;

    private static final Map<Character, String> EXTRA_TRANSLITERATIONS = Map.ofEntries(
            Map.entry('→', "->"),
            Map.entry('←', "<-"),
            Map.entry('↔', "<->"),
            Map.entry('⇒', "=>"),
            Map.entry('≥', ">="),
            Map.entry('≤', "<="),
            Map.entry('≠', "!="),
            Map.entry('×', "x"),
            Map.entry('÷', "/"),
            Map.entry('−', "-"),
            Map.entry('ł', "l"),
            Map.entry('Ł', "L"),
            Map.entry('đ', "d"),
            Map.entry('Đ', "D"),
            Map.entry('ø', "o"),
            Map.entry('Ø', "O"),
            Map.entry('æ', "ae"),
            Map.entry('Æ', "AE"),
            Map.entry('œ', "oe"),
            Map.entry('Œ', "OE"),
            Map.entry('✓', "*"),
            Map.entry('✔', "*"),
            Map.entry('★', "*"),
            Map.entry('▪', "-"),
            Map.entry('▫', "-"),
            Map.entry('◦', "-"),
            Map.entry('‣', "-")
    );

    public byte[] export(
            TailoredResumeDataDto data,
            ResumeTemplate template,
            String draftTitle
    ) {
        TailoredResumeDataDto safeData = data != null ? data : TailoredResumeDataDto.builder().build();
        TailoredResumeTemplateSpec spec = TailoredResumeTemplateSpec.forTemplate(template);

        try (PDDocument document = new PDDocument()) {
            populateMetadata(document, safeData.getHeader(), spec, draftTitle);

            FontPalette fonts = createFontPalette(spec);
            PdfCursor cursor = new PdfCursor(document, spec, fonts);

            drawHeader(cursor, safeData.getHeader());
            drawSummarySection(cursor, safeData.getTailoredSummary());
            drawSkillsSection(cursor, safeData.getHighlightedSkills(), safeData.getAdditionalSkills());
            drawExperienceSection(cursor, safeData.getOptimizedExperiences());
            drawProjectsSection(cursor, safeData.getProjects());
            drawEducationSection(cursor, safeData.getEducations());
            drawLanguagesAndCertificationsSection(cursor, safeData.getLanguages(), safeData.getCertifications());

            cursor.closeCurrentStream();
            drawPageFooters(document, spec, fonts, safeData.getHeader(), draftTitle);

            ByteArrayOutputStream out = new ByteArrayOutputStream(16384);
            document.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new ResumeStorageException("Failed to generate tailored PDF document", ex);
        }
    }

    private void populateMetadata(
            PDDocument document,
            TailoredResumeHeaderDto header,
            TailoredResumeTemplateSpec spec,
            String draftTitle
    ) {
        PDDocumentInformation info = document.getDocumentInformation();
        String fullName = ResumeExportTextFormatter.cleanInlineText(header != null ? header.getFullName() : null);
        String title = !fullName.isEmpty()
                ? fullName + " — " + spec.displayName() + " CV"
                : (!ResumeExportTextFormatter.cleanInlineText(draftTitle).isEmpty()
                ? ResumeExportTextFormatter.cleanInlineText(draftTitle)
                : "Tailored CV");
        info.setTitle(title);
        if (!fullName.isEmpty()) {
            info.setAuthor(fullName);
        }
        String headline = ResumeExportTextFormatter.cleanInlineText(header != null ? header.getHeadline() : null);
        if (!headline.isEmpty()) {
            info.setSubject(headline);
        }
        info.setCreator("EuroJob Copilot Resume Studio");
    }

    private FontPalette createFontPalette(TailoredResumeTemplateSpec spec) {
        if (spec.serifFont()) {
            return new FontPalette(
                    new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN),
                    new PDType1Font(Standard14Fonts.FontName.TIMES_BOLD),
                    new PDType1Font(Standard14Fonts.FontName.TIMES_ITALIC)
            );
        }
        return new FontPalette(
                new PDType1Font(Standard14Fonts.FontName.HELVETICA),
                new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD),
                new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE)
        );
    }

    private void drawHeader(PdfCursor cursor, TailoredResumeHeaderDto header) throws IOException {
        TailoredResumeTemplateSpec spec = cursor.spec;
        FontPalette fonts = cursor.fonts;

        if (spec.showTopBanner()) {
            float bannerHeight = spec.europassLayout() ? 7f : 4.5f;
            cursor.fillRect(0f, PAGE_HEIGHT - bannerHeight, PAGE_WIDTH, bannerHeight, spec.accentHex());
        }

        String name = header != null && !ResumeExportTextFormatter.cleanInlineText(header.getFullName()).isEmpty()
                ? ResumeExportTextFormatter.cleanInlineText(header.getFullName())
                : "Candidate Resume";
        String headline = header != null ? ResumeExportTextFormatter.cleanInlineText(header.getHeadline()) : "";
        List<String> contactSegments = ResumeExportTextFormatter.buildContactSegments(header);
        List<LabeledUrl> safeLinks = ResumeExportTextFormatter.extractSafeHeaderLinks(header);

        List<String> contactLines = contactSegments.isEmpty()
                ? List.of()
                : wrapText(fonts.regular, 9f, String.join("  ·  ", contactSegments), CONTENT_WIDTH - (spec.showHeaderShading() ? 20f : 0f));

        List<String> headlineLines = headline.isEmpty()
                ? List.of()
                : wrapText(fonts.bold, 11.5f, headline, CONTENT_WIDTH - (spec.showHeaderShading() ? 20f : 0f));

        // Measure header block height if shaded panel is desired
        if (spec.showHeaderShading()) {
            float estimatedHeight = 14f;
            if (spec.europassLayout()) {
                estimatedHeight += 13f;
            }
            estimatedHeight += 24f;
            estimatedHeight += headlineLines.size() * 14f;
            estimatedHeight += contactLines.size() * 12.5f;
            estimatedHeight += safeLinks.isEmpty() ? 0f : 14f;
            estimatedHeight += 8f;

            float panelTop = cursor.y + 8f;
            float panelBottom = panelTop - estimatedHeight;
            cursor.fillRect(MARGIN_X - 8f, panelBottom, CONTENT_WIDTH + 16f, estimatedHeight, spec.tintBgHex());
        }

        float padX = spec.showHeaderShading() ? MARGIN_X + 2f : MARGIN_X;
        float rightEdge = MARGIN_X + CONTENT_WIDTH;

        if (spec.europassLayout()) {
            cursor.drawText("EUROPASS CURRICULUM VITAE", padX, cursor.y, fonts.bold, 8f, spec.primaryHex());
            cursor.y -= 13f;
        }

        if (spec.centerHeader()) {
            cursor.drawCenteredText(name, PAGE_WIDTH / 2f, cursor.y, fonts.bold, 20f, spec.darkInkHex());
        } else {
            cursor.drawText(name, padX, cursor.y, fonts.bold, 20f, spec.darkInkHex());
        }
        cursor.y -= 18f;

        for (String hl : headlineLines) {
            if (spec.centerHeader()) {
                cursor.drawCenteredText(hl, PAGE_WIDTH / 2f, cursor.y, fonts.bold, 11.2f, spec.mutedInkHex());
            } else {
                cursor.drawText(hl, padX, cursor.y, fonts.bold, 11.2f, spec.mutedInkHex());
            }
            cursor.y -= 14f;
        }

        for (String cl : contactLines) {
            if (spec.centerHeader()) {
                cursor.drawCenteredText(cl, PAGE_WIDTH / 2f, cursor.y, fonts.regular, 9f, spec.mutedInkHex());
            } else {
                cursor.drawText(cl, padX, cursor.y, fonts.regular, 9f, spec.mutedInkHex());
            }
            cursor.y -= 12.5f;
        }

        if (!safeLinks.isEmpty()) {
            drawHeaderLinksLine(cursor, safeLinks, fonts.bold, 9f, spec.centerHeader(), padX, rightEdge);
            cursor.y -= 13f;
        }

        cursor.y -= 4f;
        if (spec.doubleHeaderRule()) {
            cursor.drawLine(MARGIN_X, cursor.y, rightEdge, cursor.y, 1.4f, spec.primaryHex());
            cursor.drawLine(MARGIN_X, cursor.y - 3f, rightEdge, cursor.y - 3f, 0.7f, spec.ruleHex());
            cursor.y -= 15f;
        } else {
            cursor.drawLine(MARGIN_X, cursor.y, rightEdge, cursor.y, spec.europassLayout() ? 1.8f : 1.2f, spec.accentHex());
            cursor.y -= 13f;
        }
    }

    private void drawHeaderLinksLine(
            PdfCursor cursor,
            List<LabeledUrl> links,
            PDFont font,
            float fontSize,
            boolean centered,
            float leftEdge,
            float rightEdge
    ) throws IOException {
        String separator = "   ·   ";
        float sepWidth = cursor.measureWidth(font, fontSize, separator);
        float totalWidth = 0f;
        for (int i = 0; i < links.size(); i++) {
            totalWidth += cursor.measureWidth(font, fontSize, links.get(i).label());
            if (i < links.size() - 1) {
                totalWidth += sepWidth;
            }
        }
        float x = centered ? Math.max(leftEdge, (PAGE_WIDTH - totalWidth) / 2f) : leftEdge;
        for (int i = 0; i < links.size(); i++) {
            LabeledUrl item = links.get(i);
            String safeLabel = sanitizeForFont(font, item.label());
            float w = cursor.measureWidth(font, fontSize, safeLabel);
            if (x + w > rightEdge) {
                cursor.y -= 12f;
                x = leftEdge;
            }
            cursor.drawText(safeLabel, x, cursor.y, font, fontSize, cursor.spec.accentHex());
            cursor.drawLine(x, cursor.y - 1.5f, x + w, cursor.y - 1.5f, 0.5f, cursor.spec.accentHex());
            cursor.addUriAnnotation(x, cursor.y - 2f, w, fontSize + 3f, item.url());
            x += w;
            if (i < links.size() - 1) {
                cursor.drawText(separator, x, cursor.y, font, fontSize, cursor.spec.mutedInkHex());
                x += sepWidth;
            }
        }
    }

    private void drawSummarySection(PdfCursor cursor, String rawSummary) throws IOException {
        String cleanSummary = ResumeExportTextFormatter.cleanMultilineText(rawSummary);
        if (cleanSummary.isEmpty()) {
            return;
        }
        List<String> lines = wrapParagraphs(cursor.fonts.regular, 9.8f, cleanSummary, CONTENT_WIDTH);
        if (lines.isEmpty()) {
            return;
        }
        drawSectionHeading(cursor, cursor.spec.summaryHeading(), 38f);
        for (String line : lines) {
            cursor.ensureSpace(14f);
            cursor.drawText(line, MARGIN_X, cursor.y, cursor.fonts.regular, 9.8f, cursor.spec.darkInkHex());
            cursor.y -= 13.5f;
        }
        cursor.y -= 6f;
    }

    private void drawSkillsSection(
            PdfCursor cursor,
            List<String> highlightedSkills,
            List<String> additionalSkills
    ) throws IOException {
        List<String> highlighted = cleanNonBlankList(highlightedSkills);
        List<String> additional = cleanNonBlankList(additionalSkills);
        if (highlighted.isEmpty() && additional.isEmpty()) {
            return;
        }

        drawSectionHeading(cursor, cursor.spec.skillsHeading(), 36f);

        if (!highlighted.isEmpty()) {
            drawLabeledInlineList(cursor, "Core Alignment: ", String.join("  ·  ", highlighted));
        }
        if (!additional.isEmpty()) {
            drawLabeledInlineList(cursor, "Additional Skills: ", String.join("  ·  ", additional));
        }
        cursor.y -= 6f;
    }

    private void drawExperienceSection(
            PdfCursor cursor,
            List<TailoredResumeExperienceDto> experiences
    ) throws IOException {
        if (experiences == null || experiences.isEmpty()) {
            return;
        }

        drawSectionHeading(cursor, cursor.spec.experienceHeading(), 60f);

        for (int i = 0; i < experiences.size(); i++) {
            TailoredResumeExperienceDto exp = experiences.get(i);
            if (exp == null) {
                continue;
            }
            String designation = ResumeExportTextFormatter.cleanInlineText(exp.getDesignation());
            String company = ResumeExportTextFormatter.cleanInlineText(exp.getCompanyName());
            String location = ResumeExportTextFormatter.cleanInlineText(exp.getLocation());
            String dateRange = ResumeExportTextFormatter.formatDateRange(exp.getStartDate(), exp.getEndDate(), exp.isCurrent());

            String titleLine = designation;
            if (!company.isEmpty()) {
                titleLine = titleLine.isEmpty() ? company : titleLine + " — " + company;
            }
            if (titleLine.isEmpty()) {
                continue;
            }

            List<String> bullets = cleanNonBlankList(exp.getTailoredBullets());
            float minExpHeight = 30f + (!location.isEmpty() ? 12f : 0f) + (!bullets.isEmpty() ? 14f : 0f);
            cursor.ensureSpace(minExpHeight);

            float dateWidth = dateRange.isEmpty() ? 0f : cursor.measureWidth(cursor.fonts.bold, 9f, dateRange);
            float maxTitleWidth = dateWidth > 0f ? CONTENT_WIDTH - dateWidth - 14f : CONTENT_WIDTH;
            List<String> titleWrapped = wrapText(cursor.fonts.bold, 10.3f, titleLine, Math.max(160f, maxTitleWidth));

            for (int l = 0; l < titleWrapped.size(); l++) {
                if (l > 0) {
                    cursor.ensureSpace(14f);
                }
                cursor.drawText(titleWrapped.get(l), MARGIN_X, cursor.y, cursor.fonts.bold, 10.3f, cursor.spec.darkInkHex());
                if (l == 0 && !dateRange.isEmpty()) {
                    String dateColor = cursor.spec.europassLayout() ? cursor.spec.primaryHex() : cursor.spec.mutedInkHex();
                    cursor.drawRightAlignedText(dateRange, MARGIN_X + CONTENT_WIDTH, cursor.y, cursor.fonts.bold, 8.8f, dateColor);
                }
                cursor.y -= 13.5f;
            }

            if (!location.isEmpty()) {
                cursor.ensureSpace(12f);
                cursor.drawText(location, MARGIN_X, cursor.y, cursor.fonts.italic, 8.9f, cursor.spec.mutedInkHex());
                cursor.y -= 12f;
            }

            for (String bullet : bullets) {
                drawBulletParagraph(cursor, bullet);
            }

            cursor.y -= (i < experiences.size() - 1) ? 7f : 4f;
        }
        cursor.y -= 4f;
    }

    private void drawProjectsSection(
            PdfCursor cursor,
            List<TailoredResumeProjectDto> projects
    ) throws IOException {
        if (projects == null || projects.isEmpty()) {
            return;
        }

        drawSectionHeading(cursor, cursor.spec.projectsHeading(), 54f);

        for (int i = 0; i < projects.size(); i++) {
            TailoredResumeProjectDto proj = projects.get(i);
            if (proj == null) {
                continue;
            }
            String name = ResumeExportTextFormatter.cleanInlineText(proj.getName());
            if (name.isEmpty()) {
                continue;
            }
            String desc = ResumeExportTextFormatter.cleanMultilineText(proj.getDescription());
            List<String> techs = cleanNonBlankList(proj.getTechnologies());
            List<String> bullets = cleanNonBlankList(proj.getTailoredBullets());
            boolean hasSafeUrl = ResumeExportTextFormatter.isSafeHttpUrl(proj.getUrl());

            cursor.ensureSpace(34f);

            float linkReserve = hasSafeUrl ? 62f : 0f;
            List<String> nameWrapped = wrapText(cursor.fonts.bold, 10.1f, name, Math.max(180f, CONTENT_WIDTH - linkReserve));
            for (int l = 0; l < nameWrapped.size(); l++) {
                if (l > 0) {
                    cursor.ensureSpace(13f);
                }
                cursor.drawText(nameWrapped.get(l), MARGIN_X, cursor.y, cursor.fonts.bold, 10.1f, cursor.spec.darkInkHex());
                if (l == 0 && hasSafeUrl) {
                    String linkLabel = "Repository";
                    float lw = cursor.measureWidth(cursor.fonts.bold, 8.8f, linkLabel);
                    float lx = MARGIN_X + CONTENT_WIDTH - lw;
                    cursor.drawText(linkLabel, lx, cursor.y, cursor.fonts.bold, 8.8f, cursor.spec.accentHex());
                    cursor.drawLine(lx, cursor.y - 1.5f, lx + lw, cursor.y - 1.5f, 0.5f, cursor.spec.accentHex());
                    cursor.addUriAnnotation(lx, cursor.y - 2f, lw, 11.5f, proj.getUrl().trim());
                }
                cursor.y -= 13.5f;
            }

            if (!desc.isEmpty()) {
                for (String line : wrapParagraphs(cursor.fonts.regular, 9.4f, desc, CONTENT_WIDTH)) {
                    cursor.ensureSpace(13f);
                    cursor.drawText(line, MARGIN_X, cursor.y, cursor.fonts.regular, 9.4f, cursor.spec.darkInkHex());
                    cursor.y -= 12.5f;
                }
            }

            if (!techs.isEmpty()) {
                drawLabeledInlineList(cursor, "Stack: ", String.join(", ", techs));
            }

            for (String bullet : bullets) {
                drawBulletParagraph(cursor, bullet);
            }

            cursor.y -= (i < projects.size() - 1) ? 6f : 3f;
        }
        cursor.y -= 4f;
    }

    private void drawEducationSection(
            PdfCursor cursor,
            List<TailoredResumeEducationDto> educations
    ) throws IOException {
        if (educations == null || educations.isEmpty()) {
            return;
        }

        drawSectionHeading(cursor, cursor.spec.educationHeading(), 46f);

        for (int i = 0; i < educations.size(); i++) {
            TailoredResumeEducationDto edu = educations.get(i);
            if (edu == null) {
                continue;
            }
            String heading = ResumeExportTextFormatter.formatEducationHeading(edu);
            if (heading.isEmpty()) {
                continue;
            }
            String dates = ResumeExportTextFormatter.formatDateRange(edu.getStartDate(), edu.getEndDate(), false);
            String meta = ResumeExportTextFormatter.formatEducationMeta(edu);

            cursor.ensureSpace(!meta.isEmpty() ? 28f : 16f);

            float dateWidth = dates.isEmpty() ? 0f : cursor.measureWidth(cursor.fonts.bold, 8.8f, dates);
            List<String> headingLines = wrapText(
                    cursor.fonts.bold,
                    9.8f,
                    heading,
                    Math.max(160f, CONTENT_WIDTH - (dateWidth > 0f ? dateWidth + 14f : 0f))
            );
            for (int l = 0; l < headingLines.size(); l++) {
                if (l > 0) {
                    cursor.ensureSpace(13f);
                }
                cursor.drawText(headingLines.get(l), MARGIN_X, cursor.y, cursor.fonts.bold, 9.8f, cursor.spec.darkInkHex());
                if (l == 0 && !dates.isEmpty()) {
                    cursor.drawRightAlignedText(dates, MARGIN_X + CONTENT_WIDTH, cursor.y, cursor.fonts.bold, 8.8f, cursor.spec.mutedInkHex());
                }
                cursor.y -= 13f;
            }

            if (!meta.isEmpty()) {
                cursor.ensureSpace(12f);
                cursor.drawText(meta, MARGIN_X, cursor.y, cursor.fonts.regular, 9f, cursor.spec.mutedInkHex());
                cursor.y -= 12f;
            }
            cursor.y -= 3f;
        }
        cursor.y -= 4f;
    }

    private void drawLanguagesAndCertificationsSection(
            PdfCursor cursor,
            List<String> languages,
            List<String> certifications
    ) throws IOException {
        List<String> langs = cleanNonBlankList(languages);
        List<String> certs = cleanNonBlankList(certifications);
        if (langs.isEmpty() && certs.isEmpty()) {
            return;
        }

        drawSectionHeading(cursor, cursor.spec.languagesCertsHeading(), 34f);

        if (!langs.isEmpty()) {
            drawLabeledInlineList(cursor, "Languages: ", String.join("  ·  ", langs));
        }
        if (!certs.isEmpty()) {
            drawLabeledInlineList(cursor, "Certifications: ", String.join("  ·  ", certs));
        }
    }

    private void drawSectionHeading(PdfCursor cursor, String title, float minFollowingSpace) throws IOException {
        cursor.ensureSpace(22f + minFollowingSpace);

        float textX = MARGIN_X;
        if (cursor.spec.showSectionAccentBar()) {
            cursor.fillRect(MARGIN_X, cursor.y - 1.5f, 3.5f, 11.5f, cursor.spec.accentHex());
            textX = MARGIN_X + 8f;
        }

        cursor.drawText(title, textX, cursor.y, cursor.fonts.bold, 10.2f, cursor.spec.primaryHex());
        cursor.y -= 5f;
        cursor.drawLine(MARGIN_X, cursor.y, MARGIN_X + CONTENT_WIDTH, cursor.y,
                cursor.spec.europassLayout() ? 1.1f : 0.7f, cursor.spec.ruleHex());
        cursor.y -= 13f;
    }

    private void drawLabeledInlineList(PdfCursor cursor, String label, String value) throws IOException {
        String safeLabel = label.trim() + " ";
        String combined = safeLabel + value;
        List<String> lines = wrapText(cursor.fonts.regular, 9.5f, combined, CONTENT_WIDTH);
        for (int i = 0; i < lines.size(); i++) {
            cursor.ensureSpace(13f);
            String line = lines.get(i);
            if (i == 0 && line.startsWith(safeLabel)) {
                cursor.drawText(safeLabel, MARGIN_X, cursor.y, cursor.fonts.bold, 9.5f, cursor.spec.darkInkHex());
                float labelW = cursor.measureWidth(cursor.fonts.bold, 9.5f, safeLabel);
                cursor.drawText(line.substring(safeLabel.length()), MARGIN_X + labelW, cursor.y, cursor.fonts.regular, 9.5f, cursor.spec.darkInkHex());
            } else {
                cursor.drawText(line, MARGIN_X, cursor.y, cursor.fonts.regular, 9.5f, cursor.spec.darkInkHex());
            }
            cursor.y -= 13f;
        }
    }

    private void drawBulletParagraph(PdfCursor cursor, String bulletText) throws IOException {
        String cleanBullet = ResumeExportTextFormatter.cleanInlineText(bulletText);
        if (cleanBullet.isEmpty()) {
            return;
        }
        float indent = 13f;
        List<String> lines = wrapText(cursor.fonts.regular, 9.4f, cleanBullet, CONTENT_WIDTH - indent);
        for (int i = 0; i < lines.size(); i++) {
            cursor.ensureSpace(13f);
            if (i == 0) {
                cursor.drawText("•", MARGIN_X + 3f, cursor.y, cursor.fonts.bold, 9.5f, cursor.spec.accentHex());
            }
            cursor.drawText(lines.get(i), MARGIN_X + indent, cursor.y, cursor.fonts.regular, 9.4f, cursor.spec.darkInkHex());
            cursor.y -= 12.6f;
        }
    }

    private void drawPageFooters(
            PDDocument document,
            TailoredResumeTemplateSpec spec,
            FontPalette fonts,
            TailoredResumeHeaderDto header,
            String draftTitle
    ) throws IOException {
        int totalPages = document.getNumberOfPages();
        String name = ResumeExportTextFormatter.cleanInlineText(header != null ? header.getFullName() : null);
        String fallbackTitle = ResumeExportTextFormatter.cleanInlineText(draftTitle);
        String leftFooter = !name.isEmpty()
                ? name + " · " + spec.displayName() + " CV"
                : (!fallbackTitle.isEmpty() ? fallbackTitle : spec.displayName() + " Tailored CV");

        float[] mutedRgb = spec.rgbComponents(spec.mutedInkHex());
        float[] ruleRgb = spec.rgbComponents(spec.ruleHex());

        for (int i = 0; i < totalPages; i++) {
            PDPage page = document.getPage(i);
            String pageLabel = "Page " + (i + 1) + " of " + totalPages;
            try (PDPageContentStream footerStream = new PDPageContentStream(
                    document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                footerStream.setStrokingColor(ruleRgb[0], ruleRgb[1], ruleRgb[2]);
                footerStream.setLineWidth(0.5f);
                footerStream.moveTo(MARGIN_X, 30f);
                footerStream.lineTo(MARGIN_X + CONTENT_WIDTH, 30f);
                footerStream.stroke();

                footerStream.setNonStrokingColor(mutedRgb[0], mutedRgb[1], mutedRgb[2]);
                String safeLeft = sanitizeForFont(fonts.regular, leftFooter);
                footerStream.beginText();
                footerStream.setFont(fonts.regular, 8f);
                footerStream.newLineAtOffset(MARGIN_X, 19f);
                footerStream.showText(safeLeft);
                footerStream.endText();

                String safeRight = sanitizeForFont(fonts.regular, pageLabel);
                float rightWidth = fonts.regular.getStringWidth(safeRight) / 1000f * 8f;
                footerStream.beginText();
                footerStream.setFont(fonts.regular, 8f);
                footerStream.newLineAtOffset(MARGIN_X + CONTENT_WIDTH - rightWidth, 19f);
                footerStream.showText(safeRight);
                footerStream.endText();
            }
        }
    }

    private List<String> wrapParagraphs(PDFont font, float fontSize, String text, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\n")) {
            String trimmed = paragraph.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            lines.addAll(wrapText(font, fontSize, trimmed, maxWidth));
        }
        return lines;
    }

    private List<String> wrapText(PDFont font, float fontSize, String text, float maxWidth) throws IOException {
        String safe = sanitizeForFont(font, text);
        List<String> lines = new ArrayList<>();
        if (safe.isEmpty()) {
            return lines;
        }

        StringBuilder currentLine = new StringBuilder();
        String[] words = safe.split("\\s+");
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (currentLine.isEmpty()) {
                if (measureWidth(font, fontSize, word) <= maxWidth) {
                    currentLine.append(word);
                } else {
                    appendLongTokenWrapped(font, fontSize, word, maxWidth, lines, currentLine);
                }
            } else {
                String candidate = currentLine + " " + word;
                if (measureWidth(font, fontSize, candidate) <= maxWidth) {
                    currentLine.append(' ').append(word);
                } else {
                    lines.add(currentLine.toString());
                    currentLine.setLength(0);
                    if (measureWidth(font, fontSize, word) <= maxWidth) {
                        currentLine.append(word);
                    } else {
                        appendLongTokenWrapped(font, fontSize, word, maxWidth, lines, currentLine);
                    }
                }
            }
        }
        if (!currentLine.isEmpty()) {
            lines.add(currentLine.toString());
        }
        return lines;
    }

    private void appendLongTokenWrapped(
            PDFont font,
            float fontSize,
            String token,
            float maxWidth,
            List<String> lines,
            StringBuilder currentLine
    ) throws IOException {
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            String next = currentLine.toString() + c;
            if (!currentLine.isEmpty() && measureWidth(font, fontSize, next) > maxWidth) {
                lines.add(currentLine.toString());
                currentLine.setLength(0);
            }
            currentLine.append(c);
        }
    }

    private float measureWidth(PDFont font, float fontSize, String text) throws IOException {
        if (text == null || text.isEmpty()) {
            return 0f;
        }
        return font.getStringWidth(text) / 1000f * fontSize;
    }

    private static List<String> cleanNonBlankList(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String item : raw) {
            String cleaned = ResumeExportTextFormatter.cleanInlineText(item);
            if (!cleaned.isEmpty()) {
                out.add(cleaned);
            }
        }
        return out;
    }

    public static String sanitizeForFont(PDFont font, String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); ) {
            int cp = raw.codePointAt(i);
            i += Character.charCount(cp);

            if (cp == '\n' || cp == '\r' || cp == '\t') {
                out.append(' ');
                continue;
            }
            if (cp < 0x20 || cp == 0x7F) {
                continue;
            }
            if (Character.charCount(cp) == 1) {
                String mapped = EXTRA_TRANSLITERATIONS.get((char) cp);
                if (mapped != null) {
                    out.append(mapped);
                    continue;
                }
            }

            String chStr = new String(Character.toChars(cp));
            if (canEncode(font, chStr)) {
                out.append(chStr);
                continue;
            }

            String decomposed = Normalizer.normalize(chStr, Normalizer.Form.NFD)
                    .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
            if (!decomposed.isEmpty() && canEncode(font, decomposed)) {
                out.append(decomposed);
            }
        }
        return out.toString();
    }

    private static boolean canEncode(PDFont font, String text) {
        try {
            font.encode(text);
            return true;
        } catch (IllegalArgumentException | IOException ex) {
            return false;
        }
    }

    private record FontPalette(PDFont regular, PDFont bold, PDFont italic) {
    }

    private static final class PdfCursor {
        private final PDDocument document;
        private final TailoredResumeTemplateSpec spec;
        private final FontPalette fonts;
        private PDPage currentPage;
        private PDPageContentStream stream;
        private float y;

        private PdfCursor(PDDocument document, TailoredResumeTemplateSpec spec, FontPalette fonts) throws IOException {
            this.document = document;
            this.spec = spec;
            this.fonts = fonts;
            openNewPage();
        }

        private void openNewPage() throws IOException {
            closeCurrentStream();
            currentPage = new PDPage(PDRectangle.A4);
            document.addPage(currentPage);
            stream = new PDPageContentStream(document, currentPage);
            y = PAGE_HEIGHT - MARGIN_TOP;
        }

        private void ensureSpace(float neededHeight) throws IOException {
            if (y - neededHeight < MIN_Y) {
                openNewPage();
            }
        }

        private float measureWidth(PDFont font, float fontSize, String text) throws IOException {
            String safe = sanitizeForFont(font, text);
            if (safe.isEmpty()) {
                return 0f;
            }
            return font.getStringWidth(safe) / 1000f * fontSize;
        }

        private void drawText(String text, float x, float yPos, PDFont font, float fontSize, String hexColor) throws IOException {
            String safe = sanitizeForFont(font, text);
            if (safe.isEmpty()) {
                return;
            }
            float[] rgb = spec.rgbComponents(hexColor);
            stream.setNonStrokingColor(rgb[0], rgb[1], rgb[2]);
            stream.beginText();
            stream.setFont(font, fontSize);
            stream.newLineAtOffset(x, yPos);
            stream.showText(safe);
            stream.endText();
        }

        private void drawCenteredText(String text, float centerX, float yPos, PDFont font, float fontSize, String hexColor) throws IOException {
            String safe = sanitizeForFont(font, text);
            if (safe.isEmpty()) {
                return;
            }
            float width = font.getStringWidth(safe) / 1000f * fontSize;
            drawText(safe, Math.max(MARGIN_X, centerX - (width / 2f)), yPos, font, fontSize, hexColor);
        }

        private void drawRightAlignedText(String text, float rightX, float yPos, PDFont font, float fontSize, String hexColor) throws IOException {
            String safe = sanitizeForFont(font, text);
            if (safe.isEmpty()) {
                return;
            }
            float width = font.getStringWidth(safe) / 1000f * fontSize;
            drawText(safe, rightX - width, yPos, font, fontSize, hexColor);
        }

        private void drawLine(float x1, float y1, float x2, float y2, float lineWidth, String hexColor) throws IOException {
            float[] rgb = spec.rgbComponents(hexColor);
            stream.setStrokingColor(rgb[0], rgb[1], rgb[2]);
            stream.setLineWidth(lineWidth);
            stream.moveTo(x1, y1);
            stream.lineTo(x2, y2);
            stream.stroke();
        }

        private void fillRect(float x, float bottomY, float width, float height, String hexColor) throws IOException {
            float[] rgb = spec.rgbComponents(hexColor);
            stream.setNonStrokingColor(rgb[0], rgb[1], rgb[2]);
            stream.addRect(x, bottomY, width, height);
            stream.fill();
        }

        private void addUriAnnotation(float x, float bottomY, float width, float height, String url) throws IOException {
            PDAnnotationLink link = new PDAnnotationLink();
            link.setRectangle(new PDRectangle(x, bottomY, Math.max(12f, width), Math.max(10f, height)));
            PDBorderStyleDictionary borderStyle = new PDBorderStyleDictionary();
            borderStyle.setWidth(0f);
            link.setBorderStyle(borderStyle);

            PDActionURI uriAction = new PDActionURI();
            uriAction.setURI(url);
            link.setAction(uriAction);
            currentPage.getAnnotations().add(link);
        }

        private void closeCurrentStream() throws IOException {
            if (stream != null) {
                stream.close();
                stream = null;
            }
        }
    }
}
