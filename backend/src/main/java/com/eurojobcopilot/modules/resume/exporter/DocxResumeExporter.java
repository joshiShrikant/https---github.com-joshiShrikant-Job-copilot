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
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Generates standards-compliant OpenXML (.docx) A4 resume documents using JDK streaming ZIP/XML
 * without external heavyweight office dependencies, differentiating typography, colors, borders,
 * and section structure across all {@link ResumeTemplate} variants.
 */
@Component
public class DocxResumeExporter {

    private static final String W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String R_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final int A4_WIDTH_TWIPS = 11906;
    private static final int A4_HEIGHT_TWIPS = 16838;
    private static final int MARGIN_TWIPS = 864; // 0.6 in
    private static final int PRINTABLE_RIGHT_TAB_TWIPS = A4_WIDTH_TWIPS - (2 * MARGIN_TWIPS);

    public byte[] export(
            TailoredResumeDataDto data,
            ResumeTemplate template,
            String draftTitle
    ) {
        TailoredResumeDataDto safeData = data != null ? data : TailoredResumeDataDto.builder().build();
        TailoredResumeTemplateSpec spec = TailoredResumeTemplateSpec.forTemplate(template);

        ExternalRelRegistry relRegistry = new ExternalRelRegistry();
        String documentXml = buildDocumentXml(safeData, spec, relRegistry);
        String relsXml = buildDocumentRelsXml(relRegistry);
        String stylesXml = buildStylesXml(spec);
        String footerXml = buildFooterXml(safeData.getHeader(), spec, draftTitle);

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(16384);
            try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
                writeZipEntry(zos, "[Content_Types].xml", buildContentTypesXml());
                writeZipEntry(zos, "_rels/.rels", buildRootRelsXml());
                writeZipEntry(zos, "word/_rels/document.xml.rels", relsXml);
                writeZipEntry(zos, "word/styles.xml", stylesXml);
                writeZipEntry(zos, "word/footer1.xml", footerXml);
                writeZipEntry(zos, "word/document.xml", documentXml);
            }
            return baos.toByteArray();
        } catch (IOException ex) {
            throw new ResumeStorageException("Failed to generate tailored DOCX document", ex);
        }
    }

    private String buildContentTypesXml() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                  <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
                  <Override PartName="/word/footer1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml"/>
                </Types>
                """;
    }

    private String buildRootRelsXml() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                </Relationships>
                """;
    }

    private String buildDocumentRelsXml(ExternalRelRegistry relRegistry) {
        StringBuilder xml = new StringBuilder(512);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        xml.append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n");
        xml.append("  <Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>\n");
        xml.append("  <Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/footer\" Target=\"footer1.xml\"/>\n");
        for (Map.Entry<String, String> entry : relRegistry.entries()) {
            xml.append("  <Relationship Id=\"").append(entry.getValue())
                    .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink\" Target=\"")
                    .append(escapeXml(entry.getKey()))
                    .append("\" TargetMode=\"External\"/>\n");
        }
        xml.append("</Relationships>\n");
        return xml.toString();
    }

    private String buildStylesXml(TailoredResumeTemplateSpec spec) {
        String font = escapeXml(spec.docxFontFamily());
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:styles xmlns:w="%s">
                  <w:docDefaults>
                    <w:rPrDefault>
                      <w:rPr>
                        <w:rFonts w:ascii="%s" w:hAnsi="%s" w:cs="%s"/>
                        <w:color w:val="%s"/>
                        <w:sz w:val="20"/>
                        <w:szCs w:val="20"/>
                      </w:rPr>
                    </w:rPrDefault>
                    <w:pPrDefault>
                      <w:pPr>
                        <w:spacing w:after="60" w:line="264" w:lineRule="auto"/>
                      </w:pPr>
                    </w:pPrDefault>
                  </w:docDefaults>
                  <w:style w:type="paragraph" w:default="1" w:styleId="Normal">
                    <w:name w:val="Normal"/>
                  </w:style>
                  <w:style w:type="character" w:styleId="Hyperlink">
                    <w:name w:val="Hyperlink"/>
                    <w:rPr>
                      <w:color w:val="%s"/>
                      <w:u w:val="single"/>
                    </w:rPr>
                  </w:style>
                </w:styles>
                """.formatted(W_NS, font, font, font, spec.darkInkHex(), spec.accentHex());
    }

    private String buildFooterXml(
            TailoredResumeHeaderDto header,
            TailoredResumeTemplateSpec spec,
            String draftTitle
    ) {
        String name = ResumeExportTextFormatter.cleanInlineText(header != null ? header.getFullName() : null);
        String fallbackTitle = ResumeExportTextFormatter.cleanInlineText(draftTitle);
        String label = !name.isEmpty()
                ? name + " · " + spec.displayName() + " CV"
                : (!fallbackTitle.isEmpty() ? fallbackTitle : spec.displayName() + " Tailored CV");

        StringBuilder xml = new StringBuilder(512);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        xml.append("<w:ftr xmlns:w=\"").append(W_NS).append("\">\n");
        xml.append("  <w:p>\n");
        xml.append("    <w:pPr><w:jc w:val=\"right\"/></w:pPr>\n");
        xml.append("    <w:r><w:rPr><w:color w:val=\"").append(spec.mutedInkHex())
                .append("\"/><w:sz w:val=\"16\"/><w:szCs w:val=\"16\"/></w:rPr>");
        xml.append("<w:t xml:space=\"preserve\">").append(escapeXml(label + " · EuroJob Copilot"))
                .append("</w:t></w:r>\n");
        xml.append("  </w:p>\n");
        xml.append("</w:ftr>\n");
        return xml.toString();
    }

    private String buildDocumentXml(
            TailoredResumeDataDto data,
            TailoredResumeTemplateSpec spec,
            ExternalRelRegistry relRegistry
    ) {
        StringBuilder body = new StringBuilder(8192);
        body.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        body.append("<w:document xmlns:w=\"").append(W_NS).append("\" xmlns:r=\"").append(R_NS).append("\">\n");
        body.append("  <w:body>\n");

        appendHeaderBlock(body, data.getHeader(), spec, relRegistry);
        appendSummarySection(body, data.getTailoredSummary(), spec);
        appendSkillsSection(body, data.getHighlightedSkills(), data.getAdditionalSkills(), spec);
        appendExperienceSection(body, data.getOptimizedExperiences(), spec);
        appendProjectsSection(body, data.getProjects(), spec, relRegistry);
        appendEducationSection(body, data.getEducations(), spec);
        appendLanguagesAndCertificationsSection(body, data.getLanguages(), data.getCertifications(), spec);

        body.append("    <w:sectPr>\n");
        body.append("      <w:footerReference w:type=\"default\" r:id=\"rId2\"/>\n");
        body.append("      <w:pgSz w:w=\"").append(A4_WIDTH_TWIPS).append("\" w:h=\"").append(A4_HEIGHT_TWIPS).append("\"/>\n");
        body.append("      <w:pgMar w:top=\"").append(MARGIN_TWIPS)
                .append("\" w:right=\"").append(MARGIN_TWIPS)
                .append("\" w:bottom=\"").append(MARGIN_TWIPS)
                .append("\" w:left=\"").append(MARGIN_TWIPS)
                .append("\" w:header=\"420\" w:footer=\"420\" w:gutter=\"0\"/>\n");
        body.append("    </w:sectPr>\n");
        body.append("  </w:body>\n");
        body.append("</w:document>\n");
        return body.toString();
    }

    private void appendHeaderBlock(
            StringBuilder body,
            TailoredResumeHeaderDto header,
            TailoredResumeTemplateSpec spec,
            ExternalRelRegistry relRegistry
    ) {
        String name = header != null && !ResumeExportTextFormatter.cleanInlineText(header.getFullName()).isEmpty()
                ? ResumeExportTextFormatter.cleanInlineText(header.getFullName())
                : "Candidate Resume";
        String headline = header != null ? ResumeExportTextFormatter.cleanInlineText(header.getHeadline()) : "";
        List<String> contactSegments = ResumeExportTextFormatter.buildContactSegments(header);
        List<LabeledUrl> safeLinks = ResumeExportTextFormatter.extractSafeHeaderLinks(header);

        String jc = spec.centerHeader() ? "center" : "left";
        String shdTag = spec.showHeaderShading()
                ? "<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"" + spec.tintBgHex() + "\"/>"
                : "";

        if (spec.europassLayout()) {
            body.append("    <w:p><w:pPr>")
                    .append("<w:pBdr><w:top w:val=\"single\" w:sz=\"36\" w:space=\"4\" w:color=\"").append(spec.accentHex()).append("\"/></w:pBdr>")
                    .append(shdTag)
                    .append("<w:spacing w:before=\"0\" w:after=\"40\"/>")
                    .append("<w:jc w:val=\"").append(jc).append("\"/>")
                    .append("</w:pPr>")
                    .append(runXml("EUROPASS CURRICULUM VITAE", true, false, 16, spec.primaryHex()))
                    .append("</w:p>\n");
        }

        // Candidate Full Name
        String nameBorderTag = (!spec.europassLayout() && spec.showTopBanner())
                ? "<w:pBdr><w:top w:val=\"single\" w:sz=\"24\" w:space=\"6\" w:color=\"" + spec.accentHex() + "\"/></w:pBdr>"
                : "";
        body.append("    <w:p><w:pPr>")
                .append(nameBorderTag)
                .append(shdTag)
                .append("<w:spacing w:before=\"40\" w:after=\"40\"/>")
                .append("<w:jc w:val=\"").append(jc).append("\"/>")
                .append("</w:pPr>")
                .append(runXml(name, true, false, 38, spec.darkInkHex()))
                .append("</w:p>\n");

        if (!headline.isEmpty()) {
            body.append("    <w:p><w:pPr>")
                    .append(shdTag)
                    .append("<w:spacing w:before=\"0\" w:after=\"50\"/>")
                    .append("<w:jc w:val=\"").append(jc).append("\"/>")
                    .append("</w:pPr>")
                    .append(runXml(headline, true, false, 23, spec.mutedInkHex()))
                    .append("</w:p>\n");
        }

        boolean hasContact = !contactSegments.isEmpty();
        boolean hasLinks = !safeLinks.isEmpty();

        if (hasContact) {
            body.append("    <w:p><w:pPr>");
            if (!hasLinks) {
                appendHeaderBottomBorder(body, spec);
            }
            body.append(shdTag)
                    .append("<w:spacing w:before=\"0\" w:after=\"").append(hasLinks ? "40" : "120").append("\"/>")
                    .append("<w:jc w:val=\"").append(jc).append("\"/>")
                    .append("</w:pPr>")
                    .append(runXml(String.join("  ·  ", contactSegments), false, false, 18, spec.mutedInkHex()))
                    .append("</w:p>\n");
        }

        if (hasLinks) {
            body.append("    <w:p><w:pPr>");
            appendHeaderBottomBorder(body, spec);
            body.append(shdTag)
                    .append("<w:spacing w:before=\"0\" w:after=\"120\"/>")
                    .append("<w:jc w:val=\"").append(jc).append("\"/>")
                    .append("</w:pPr>");
            for (int i = 0; i < safeLinks.size(); i++) {
                LabeledUrl item = safeLinks.get(i);
                String rId = relRegistry.registerExternalUrl(item.url());
                body.append("<w:hyperlink r:id=\"").append(rId).append("\" w:history=\"1\">")
                        .append("<w:r><w:rPr><w:rStyle w:val=\"Hyperlink\"/>")
                        .append("<w:b/><w:color w:val=\"").append(spec.accentHex()).append("\"/>")
                        .append("<w:sz w:val=\"18\"/><w:szCs w:val=\"18\"/>")
                        .append("</w:rPr><w:t>").append(escapeXml(item.label())).append("</w:t></w:r>")
                        .append("</w:hyperlink>");
                if (i < safeLinks.size() - 1) {
                    body.append(runXml("  ·  ", false, false, 18, spec.mutedInkHex()));
                }
            }
            body.append("</w:p>\n");
        }

        if (!hasContact && !hasLinks) {
            body.append("    <w:p><w:pPr>");
            appendHeaderBottomBorder(body, spec);
            body.append(shdTag)
                    .append("<w:spacing w:before=\"0\" w:after=\"120\"/></w:pPr></w:p>\n");
        }
    }

    private void appendHeaderBottomBorder(StringBuilder body, TailoredResumeTemplateSpec spec) {
        String borderType = spec.doubleHeaderRule() ? "double" : "single";
        int borderSz = spec.europassLayout() ? 16 : 12;
        String borderColor = spec.doubleHeaderRule() ? spec.primaryHex() : spec.accentHex();
        body.append("<w:pBdr><w:bottom w:val=\"").append(borderType)
                .append("\" w:sz=\"").append(borderSz)
                .append("\" w:space=\"8\" w:color=\"").append(borderColor)
                .append("\"/></w:pBdr>");
    }

    private void appendSummarySection(
            StringBuilder body,
            String rawSummary,
            TailoredResumeTemplateSpec spec
    ) {
        String cleanSummary = ResumeExportTextFormatter.cleanMultilineText(rawSummary);
        if (cleanSummary.isEmpty()) {
            return;
        }
        appendSectionHeading(body, spec.summaryHeading(), spec);
        for (String line : cleanSummary.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                body.append("    <w:p><w:pPr><w:spacing w:after=\"80\"/></w:pPr>")
                        .append(runXml(trimmed, false, false, 20, spec.darkInkHex()))
                        .append("</w:p>\n");
            }
        }
    }

    private void appendSkillsSection(
            StringBuilder body,
            List<String> highlightedSkills,
            List<String> additionalSkills,
            TailoredResumeTemplateSpec spec
    ) {
        List<String> highlighted = cleanNonBlankList(highlightedSkills);
        List<String> additional = cleanNonBlankList(additionalSkills);
        if (highlighted.isEmpty() && additional.isEmpty()) {
            return;
        }

        appendSectionHeading(body, spec.skillsHeading(), spec);
        if (!highlighted.isEmpty()) {
            appendLabeledLine(body, "Core Alignment: ", String.join(" · ", highlighted), spec);
        }
        if (!additional.isEmpty()) {
            appendLabeledLine(body, "Additional Skills: ", String.join(" · ", additional), spec);
        }
    }

    private void appendExperienceSection(
            StringBuilder body,
            List<TailoredResumeExperienceDto> experiences,
            TailoredResumeTemplateSpec spec
    ) {
        if (experiences == null || experiences.isEmpty()) {
            return;
        }
        appendSectionHeading(body, spec.experienceHeading(), spec);

        for (TailoredResumeExperienceDto exp : experiences) {
            if (exp == null) {
                continue;
            }
            String designation = ResumeExportTextFormatter.cleanInlineText(exp.getDesignation());
            String company = ResumeExportTextFormatter.cleanInlineText(exp.getCompanyName());
            String location = ResumeExportTextFormatter.cleanInlineText(exp.getLocation());
            String dateRange = ResumeExportTextFormatter.formatDateRange(exp.getStartDate(), exp.getEndDate(), exp.isCurrent());

            String title = designation;
            if (!company.isEmpty()) {
                title = title.isEmpty() ? company : title + " — " + company;
            }
            if (title.isEmpty()) {
                continue;
            }

            appendRightTabbedHeadingParagraph(body, title, dateRange, spec);

            if (!location.isEmpty()) {
                body.append("    <w:p><w:pPr><w:keepNext/><w:spacing w:before=\"0\" w:after=\"40\"/></w:pPr>")
                        .append(runXml(location, false, true, 18, spec.mutedInkHex()))
                        .append("</w:p>\n");
            }

            for (String bullet : cleanNonBlankList(exp.getTailoredBullets())) {
                appendBulletParagraph(body, bullet, spec);
            }
        }
    }

    private void appendProjectsSection(
            StringBuilder body,
            List<TailoredResumeProjectDto> projects,
            TailoredResumeTemplateSpec spec,
            ExternalRelRegistry relRegistry
    ) {
        if (projects == null || projects.isEmpty()) {
            return;
        }
        appendSectionHeading(body, spec.projectsHeading(), spec);

        for (TailoredResumeProjectDto proj : projects) {
            if (proj == null) {
                continue;
            }
            String name = ResumeExportTextFormatter.cleanInlineText(proj.getName());
            if (name.isEmpty()) {
                continue;
            }
            boolean hasSafeUrl = ResumeExportTextFormatter.isSafeHttpUrl(proj.getUrl());

            body.append("    <w:p><w:pPr><w:keepNext/>")
                    .append("<w:tabs><w:tab w:val=\"right\" w:pos=\"").append(PRINTABLE_RIGHT_TAB_TWIPS).append("\"/></w:tabs>")
                    .append("<w:spacing w:before=\"80\" w:after=\"30\"/></w:pPr>")
                    .append(runXml(name, true, false, 21, spec.darkInkHex()));
            if (hasSafeUrl) {
                String rId = relRegistry.registerExternalUrl(proj.getUrl().trim());
                body.append("<w:r><w:tab/></w:r>")
                        .append("<w:hyperlink r:id=\"").append(rId).append("\" w:history=\"1\">")
                        .append("<w:r><w:rPr><w:rStyle w:val=\"Hyperlink\"/>")
                        .append("<w:b/><w:color w:val=\"").append(spec.accentHex()).append("\"/>")
                        .append("<w:sz w:val=\"18\"/><w:szCs w:val=\"18\"/>")
                        .append("</w:rPr><w:t>Repository</w:t></w:r></w:hyperlink>");
            }
            body.append("</w:p>\n");

            String desc = ResumeExportTextFormatter.cleanMultilineText(proj.getDescription());
            if (!desc.isEmpty()) {
                body.append("    <w:p><w:pPr><w:spacing w:after=\"40\"/></w:pPr>")
                        .append(runXml(desc, false, false, 19, spec.darkInkHex()))
                        .append("</w:p>\n");
            }

            List<String> techs = cleanNonBlankList(proj.getTechnologies());
            if (!techs.isEmpty()) {
                appendLabeledLine(body, "Stack: ", String.join(", ", techs), spec);
            }

            for (String bullet : cleanNonBlankList(proj.getTailoredBullets())) {
                appendBulletParagraph(body, bullet, spec);
            }
        }
    }

    private void appendEducationSection(
            StringBuilder body,
            List<TailoredResumeEducationDto> educations,
            TailoredResumeTemplateSpec spec
    ) {
        if (educations == null || educations.isEmpty()) {
            return;
        }
        appendSectionHeading(body, spec.educationHeading(), spec);

        for (TailoredResumeEducationDto edu : educations) {
            if (edu == null) {
                continue;
            }
            String heading = ResumeExportTextFormatter.formatEducationHeading(edu);
            if (heading.isEmpty()) {
                continue;
            }
            String dates = ResumeExportTextFormatter.formatDateRange(edu.getStartDate(), edu.getEndDate(), false);
            appendRightTabbedHeadingParagraph(body, heading, dates, spec);

            String meta = ResumeExportTextFormatter.formatEducationMeta(edu);
            if (!meta.isEmpty()) {
                body.append("    <w:p><w:pPr><w:spacing w:before=\"0\" w:after=\"60\"/></w:pPr>")
                        .append(runXml(meta, false, false, 18, spec.mutedInkHex()))
                        .append("</w:p>\n");
            }
        }
    }

    private void appendLanguagesAndCertificationsSection(
            StringBuilder body,
            List<String> languages,
            List<String> certifications,
            TailoredResumeTemplateSpec spec
    ) {
        List<String> langs = cleanNonBlankList(languages);
        List<String> certs = cleanNonBlankList(certifications);
        if (langs.isEmpty() && certs.isEmpty()) {
            return;
        }
        appendSectionHeading(body, spec.languagesCertsHeading(), spec);
        if (!langs.isEmpty()) {
            appendLabeledLine(body, "Languages: ", String.join(" · ", langs), spec);
        }
        if (!certs.isEmpty()) {
            appendLabeledLine(body, "Certifications: ", String.join(" · ", certs), spec);
        }
    }

    private void appendSectionHeading(StringBuilder body, String heading, TailoredResumeTemplateSpec spec) {
        String leftBorder = spec.showSectionAccentBar()
                ? "<w:left w:val=\"single\" w:sz=\"18\" w:space=\"6\" w:color=\"" + spec.accentHex() + "\"/>"
                : "";
        int bottomSz = spec.europassLayout() ? 10 : 6;
        body.append("    <w:p><w:pPr><w:keepNext/>")
                .append("<w:pBdr>").append(leftBorder)
                .append("<w:bottom w:val=\"single\" w:sz=\"").append(bottomSz)
                .append("\" w:space=\"4\" w:color=\"").append(spec.ruleHex()).append("\"/></w:pBdr>")
                .append("<w:spacing w:before=\"160\" w:after=\"80\"/></w:pPr>")
                .append(runXml(heading, true, false, 21, spec.primaryHex()))
                .append("</w:p>\n");
    }

    private void appendRightTabbedHeadingParagraph(
            StringBuilder body,
            String leftTitle,
            String rightDate,
            TailoredResumeTemplateSpec spec
    ) {
        body.append("    <w:p><w:pPr><w:keepNext/>")
                .append("<w:tabs><w:tab w:val=\"right\" w:pos=\"").append(PRINTABLE_RIGHT_TAB_TWIPS).append("\"/></w:tabs>")
                .append("<w:spacing w:before=\"90\" w:after=\"30\"/></w:pPr>")
                .append(runXml(leftTitle, true, false, 21, spec.darkInkHex()));
        if (!rightDate.isEmpty()) {
            String dateColor = spec.europassLayout() ? spec.primaryHex() : spec.mutedInkHex();
            body.append("<w:r><w:tab/></w:r>")
                    .append(runXml(rightDate, true, false, 18, dateColor));
        }
        body.append("</w:p>\n");
    }

    private void appendLabeledLine(
            StringBuilder body,
            String boldLabel,
            String value,
            TailoredResumeTemplateSpec spec
    ) {
        body.append("    <w:p><w:pPr><w:spacing w:after=\"60\"/></w:pPr>")
                .append(runXml(boldLabel, true, false, 19, spec.darkInkHex()))
                .append(runXml(value, false, false, 19, spec.darkInkHex()))
                .append("</w:p>\n");
    }

    private void appendBulletParagraph(
            StringBuilder body,
            String text,
            TailoredResumeTemplateSpec spec
    ) {
        String clean = ResumeExportTextFormatter.cleanInlineText(text);
        if (clean.isEmpty()) {
            return;
        }
        body.append("    <w:p><w:pPr>")
                .append("<w:spacing w:before=\"0\" w:after=\"45\"/>")
                .append("<w:ind w:left=\"340\" w:hanging=\"180\"/>")
                .append("</w:pPr>")
                .append(runXml("• ", true, false, 19, spec.accentHex()))
                .append(runXml(clean, false, false, 19, spec.darkInkHex()))
                .append("</w:p>\n");
    }

    private String runXml(String text, boolean bold, boolean italic, int halfPoints, String colorHex) {
        StringBuilder rPr = new StringBuilder("<w:rPr>");
        if (bold) {
            rPr.append("<w:b/>");
        }
        if (italic) {
            rPr.append("<w:i/>");
        }
        rPr.append("<w:color w:val=\"").append(colorHex).append("\"/>");
        rPr.append("<w:sz w:val=\"").append(halfPoints).append("\"/>");
        rPr.append("<w:szCs w:val=\"").append(halfPoints).append("\"/>");
        rPr.append("</w:rPr>");

        return "<w:r>" + rPr + "<w:t xml:space=\"preserve\">" + escapeXml(text) + "</w:t></w:r>";
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

    private static void writeZipEntry(ZipOutputStream zos, String name, String content) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    static String escapeXml(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(raw.length() + 16);
        for (int i = 0; i < raw.length(); ) {
            int cp = raw.codePointAt(i);
            i += Character.charCount(cp);

            if (!isValidXml10CodePoint(cp)) {
                continue;
            }
            switch (cp) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&apos;");
                default -> sb.appendCodePoint(cp);
            }
        }
        return sb.toString();
    }

    private static boolean isValidXml10CodePoint(int cp) {
        return cp == 0x9
                || cp == 0xA
                || cp == 0xD
                || (cp >= 0x20 && cp <= 0xD7FF)
                || (cp >= 0xE000 && cp <= 0xFFFD)
                || (cp >= 0x10000 && cp <= 0x10FFFF);
    }

    private static final class ExternalRelRegistry {
        private final Map<String, String> urlToRelId = new LinkedHashMap<>();
        private int nextIndex = 3; // rId1=styles, rId2=footer

        private String registerExternalUrl(String url) {
            return urlToRelId.computeIfAbsent(url, ignored -> "rId" + (nextIndex++));
        }

        private Iterable<Map.Entry<String, String>> entries() {
            return urlToRelId.entrySet();
        }
    }
}
