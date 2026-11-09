package com.eurojobcopilot.modules.sponsorship.catalog;

import com.eurojobcopilot.core.exception.BusinessValidationException;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Authoritative metadata catalog covering the 14 supported European target countries,
 * official government immigration registers/portals, statutory visa pathways, and role-level caveats.
 */
@Component
public class CountrySponsorshipCatalog {

    public static final String NL_IND_REGISTRY_SOURCE = "NL_IND_PUBLIC_REGISTER";
    public static final String DEFAULT_VACANCY_CAVEAT =
            "Recognized sponsor status applies to the employer organization and does NOT guarantee every vacancy "
                    + "offers visa sponsorship. Verify role-level eligibility, salary thresholds, and contract terms directly.";

    private static final Pattern DIACRITICS_PATTERN = Pattern.compile("\\p{M}+");

    private final List<CountrySponsorshipProfile> profiles;
    private final Map<String, CountrySponsorshipProfile> byCanonicalLower;
    private final Map<String, CountrySponsorshipProfile> aliasLookup;

    public CountrySponsorshipCatalog() {
        this.profiles = buildProfiles();
        Map<String, CountrySponsorshipProfile> canonical = new LinkedHashMap<>();
        Map<String, CountrySponsorshipProfile> aliases = new LinkedHashMap<>();

        for (CountrySponsorshipProfile profile : profiles) {
            canonical.put(profile.country().toLowerCase(Locale.ROOT), profile);
            aliases.put(normalizeToken(profile.country()), profile);
            aliases.put(normalizeToken(profile.isoCode()), profile);
        }

        registerExtraAliases(aliases, canonical);
        this.byCanonicalLower = Collections.unmodifiableMap(canonical);
        this.aliasLookup = Collections.unmodifiableMap(aliases);
    }

    public List<CountrySponsorshipProfile> getAllCountries() {
        return profiles;
    }

    public Optional<CountrySponsorshipProfile> findCountryProfile(String rawCountry) {
        if (rawCountry == null || rawCountry.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(aliasLookup.get(normalizeToken(rawCountry)));
    }

    public CountrySponsorshipProfile requireSupportedCountry(String rawCountry) {
        if (rawCountry == null || rawCountry.isBlank()) {
            throw new BusinessValidationException("Country parameter is required");
        }
        return findCountryProfile(rawCountry).orElseThrow(() -> new BusinessValidationException(
                "Unsupported country '" + rawCountry.trim()
                        + "'. Supported countries: "
                        + String.join(", ", profiles.stream().map(CountrySponsorshipProfile::country).toList())
        ));
    }

    public boolean isOfficialRegisterCountry(String country) {
        return findCountryProfile(country)
                .map(CountrySponsorshipProfile::officialRegisterAvailable)
                .orElse(false);
    }

    private List<CountrySponsorshipProfile> buildProfiles() {
        return List.of(
                new CountrySponsorshipProfile(
                        "Germany",
                        "DE",
                        false,
                        "DE_MAKE_IT_IN_GERMANY",
                        "Make it in Germany & Federal Employment Agency (BA) Skilled Migration Portal",
                        "Federal Office for Migration and Refugees (BAMF) & Bundesagentur fuer Arbeit (BA)",
                        "https://www.make-it-in-germany.com/en/visa-residence/types/eu-blue-card",
                        "https://www.make-it-in-germany.com/en/visa-residence/types/work-qualified-professionals",
                        "STATUTORY_PATHWAY_AND_JOB_EVIDENCE",
                        List.of("EU Blue Card (Blaue Karte EU)", "Skilled Worker Residence Permit (§18b AufenthG)", "Opportunity Card (Chancenkarte)"),
                        "Germany does not rely on a pre-licensed employer sponsor list; eligible German entities can hire via EU Blue Card or §18b permits when salary thresholds and degree/experience recognition criteria are met.",
                        "Even when an employer hires internationally under EU Blue Card rules, confirm per vacancy whether relocation timeline and German/English team requirements allow third-country sponsorship."
                ),
                new CountrySponsorshipProfile(
                        "Netherlands",
                        "NL",
                        true,
                        NL_IND_REGISTRY_SOURCE,
                        "IND Public Register of Recognised Sponsors (Regular Labour & Highly Skilled Migrants)",
                        "Immigration and Naturalisation Service (IND)",
                        "https://ind.nl/en/public-register-recognised-sponsors/public-register-regular-labour-and-highly-skilled-migrants",
                        "https://ind.nl/en/public-register-recognised-sponsors/public-register-regular-labour-and-highly-skilled-migrants",
                        "OFFICIAL_PUBLIC_REGISTER_AND_VACANCY_CHECK",
                        List.of("Highly Skilled Migrant (Kennismigrant)", "EU Blue Card", "30% Ruling Assessment Guidance"),
                        "Employers hiring Highly Skilled Migrants (Kennismigranten) must hold active Recognised Sponsor (erkend referent) status in the official IND Public Register and meet monthly IND salary criteria.",
                        DEFAULT_VACANCY_CAVEAT
                ),
                new CountrySponsorshipProfile(
                        "Luxembourg",
                        "LU",
                        false,
                        "LU_GUICHET_THIRD_COUNTRY",
                        "Guichet.lu Authorisations of Stay & ADEM Labour Market Portal",
                        "Directorate of Immigration (Ministry of Home Affairs) & ADEM",
                        "https://guichet.public.lu/en/citoyens/immigration/plus-3-mois/ressortissant-tiers/hautement-qualifie/salarie-hautement-qualifie.html",
                        "https://guichet.public.lu/en/entreprises/ressources-humaines/recrutement/ressortissant-pays-tiers.html",
                        "STATUTORY_PATHWAY_AND_JOB_EVIDENCE",
                        List.of("EU Blue Card (Travailleur hautement qualifie)", "Salaried Worker Residence Permit"),
                        "Luxembourg employers apply per hire through ADEM/Directorate of Immigration rather than holding a static public sponsor whitelist; EU Blue Card roles are exempt from priority labour-market testing when threshold criteria are satisfied.",
                        "Confirm whether the specific role meets Luxembourg EU Blue Card salary thresholds and whether ADEM declaration steps are supported for this opening."
                ),
                new CountrySponsorshipProfile(
                        "Switzerland",
                        "CH",
                        false,
                        "CH_SEM_WORK_PERMIT",
                        "State Secretariat for Migration (SEM) & Cantonal Work Permit Authorities",
                        "State Secretariat for Migration (SEM)",
                        "https://www.sem.admin.ch/sem/en/home/themen/arbeit/nicht-eu_efta-angehoerige.html",
                        "https://www.sem.admin.ch/sem/en/home/themen/arbeit.html",
                        "STATUTORY_PATHWAY_AND_JOB_EVIDENCE",
                        List.of("Cantonal B / L Residence & Work Permit (Non-EU/EFTA Quota)", "EU/EFTA Free Movement Notification"),
                        "Non-EU/EFTA permits in Switzerland are subject to strict annual federal/cantonal quotas and proof of specialist priority; no universal employer sponsor register exists.",
                        "Many Swiss employers only sponsor EU/EFTA nationals or senior specialists due to cantonal permit quotas; confirm non-EU quota sponsorship explicitly per job."
                ),
                new CountrySponsorshipProfile(
                        "Ireland",
                        "IE",
                        true,
                        "IE_DETE_EMPLOYMENT_PERMITS",
                        "DETE Trusted Partner Initiative & Employment Permits Portal",
                        "Department of Enterprise, Trade and Employment (DETE)",
                        "https://enterprise.gov.ie/en/what-we-do/workplace-and-skills/employment-permits/permit-types/critical-skills-employment-permit/",
                        "https://enterprise.gov.ie/en/what-we-do/workplace-and-skills/employment-permits/",
                        "OFFICIAL_GUIDANCE_AND_JOB_EVIDENCE",
                        List.of("Critical Skills Employment Permit", "General Employment Permit", "Trusted Partner Scheme"),
                        "Irish employers sponsor eligible technology roles primarily under Critical Skills or General Employment Permits; accredited employers may hold DETE Trusted Partner status for streamlined filing.",
                        "Trusted Partner registration or Critical Skills eligibility does not confirm sponsorship on every vacancy, especially short-term contracts or Stamp 4-only roles."
                ),
                new CountrySponsorshipProfile(
                        "Austria",
                        "AT",
                        false,
                        "AT_MIGRATION_RWR_CARD",
                        "Migration.gv.at Red-White-Red Card & EU Blue Card Official Portal",
                        "Austrian Federal Government & Public Employment Service (AMS)",
                        "https://www.migration.gv.at/en/types-of-immigration/permanent-immigration/",
                        "https://www.migration.gv.at/en/types-of-immigration/permanent-immigration/eubluecard/",
                        "STATUTORY_PATHWAY_AND_JOB_EVIDENCE",
                        List.of("Red-White-Red Card (Rot-Weiss-Rot - Karte)", "EU Blue Card Austria", "Red-White-Red Card Plus"),
                        "Austria evaluates points-based specialist permits (Red-White-Red Card) and EU Blue Card applications per candidate and employer offer via AMS rather than a public employer sponsor license list.",
                        "Verify employer willingness to submit the Employer Declaration (Arbeitgebererklaerung) for the specific position."
                ),
                new CountrySponsorshipProfile(
                        "Belgium",
                        "BE",
                        false,
                        "BE_SINGLE_PERMIT_PORTALS",
                        "Belgian Immigration Office & Regional Single Permit Authorities (Flanders/Brussels/Wallonia)",
                        "Immigration Office (IBZ) & Regional Economic Migration Services",
                        "https://dofi.ibz.be/en/themes/ressortissants-dun-pays-tiers/travail",
                        "https://www.vlaanderen.be/en/working-in-flanders/work-permits",
                        "STATUTORY_PATHWAY_AND_JOB_EVIDENCE",
                        List.of("Regional Single Permit (Highly Skilled)", "EU Blue Card Belgium"),
                        "Belgium administers Combined Single Permits regionally (Flanders, Brussels-Capital, Wallonia) per employment contract rather than via a centralized public sponsor registry.",
                        "Check per opening whether the regional single permit lead time (8-16 weeks) and local contract language requirements apply."
                ),
                new CountrySponsorshipProfile(
                        "Sweden",
                        "SE",
                        true,
                        "SE_MIGRATIONSVERKET_PERMIT",
                        "Swedish Migration Agency (Migrationsverket) Work Permits & Certified Employers Framework",
                        "Swedish Migration Agency (Migrationsverket)",
                        "https://www.migrationsverket.se/English/Private-individuals/Working-in-Sweden.html",
                        "https://www.migrationsverket.se/English/Other-operators/Employers/Certified-employers.html",
                        "OFFICIAL_GUIDANCE_AND_JOB_EVIDENCE",
                        List.of("Sweden Work Permit", "EU Blue Card Sweden", "Certified Employer Fast-Track"),
                        "Swedish Migration Agency certifies high-volume employers for streamlined permit processing and enforces union-aligned salary/insurance terms per vacancy.",
                        "Certified employer status does not oblige the company to sponsor relocation or work permits for every posted role."
                ),
                new CountrySponsorshipProfile(
                        "Denmark",
                        "DK",
                        true,
                        "DK_SIRI_FAST_TRACK",
                        "SIRI New to Denmark – Fast-Track Scheme & Pay Limit / Positive List Register",
                        "Danish Agency for International Recruitment and Integration (SIRI)",
                        "https://www.nyidanmark.dk/en-GB/Applying/Work/Fast%20track",
                        "https://www.nyidanmark.dk/en-GB/You-want-to-apply/Work",
                        "OFFICIAL_PUBLIC_REGISTER_AND_VACANCY_CHECK",
                        List.of("Fast-Track Scheme", "Pay Limit Scheme", "Positive List for People with a Higher Education"),
                        "SIRI maintains certified Fast-Track company status enabling rapid work start in Denmark alongside Pay Limit and Positive List pathways.",
                        DEFAULT_VACANCY_CAVEAT
                ),
                new CountrySponsorshipProfile(
                        "Finland",
                        "FI",
                        true,
                        "FI_MIGRI_SPECIALIST",
                        "Finnish Immigration Service (Migri) – Specialist & EU Blue Card Certified Employers",
                        "Finnish Immigration Service (Migri)",
                        "https://migri.fi/en/specialist",
                        "https://migri.fi/en/certification-of-employers",
                        "OFFICIAL_GUIDANCE_AND_JOB_EVIDENCE",
                        List.of("Specialist Residence Permit", "EU Blue Card Finland", "Employer Certification Fast-Track"),
                        "Migri offers specialist and EU Blue Card residence permits plus employer certification to accelerate verification for frequent international hirers.",
                        "Employer certification or international tech hiring does not guarantee sponsorship support for every vacancy."
                ),
                new CountrySponsorshipProfile(
                        "France",
                        "FR",
                        false,
                        "FR_ANEF_PASSEPORT_TALENT",
                        "Welcome to France / ANEF Passeport Talent & Work Authorization Portal",
                        "Ministry of the Interior (ANEF) & DREETS",
                        "https://www.welcometofrance.com/en/fiche/talent-passport-qualified-employee-or-innovative-company",
                        "https://administration-etrangers-en-france.interieur.gouv.fr/",
                        "STATUTORY_PATHWAY_AND_JOB_EVIDENCE",
                        List.of("Passeport Talent (Salarie qualifie / Entreprise innovante)", "EU Blue Card France", "Autorisation de travail"),
                        "France grants Passeport Talent and EU Blue Card permits based on role salary, degree criteria, or JEI/innovative company status rather than a single public sponsor whitelist.",
                        "Verify French language expectations and whether the employer sponsors Passeport Talent / DREETS work authorization on the specific posting."
                ),
                new CountrySponsorshipProfile(
                        "Poland",
                        "PL",
                        false,
                        "PL_MOS_WORK_PERMIT",
                        "Moduly Obslugi Spraw (MOS) Foreigners Work Permit & EU Blue Card Portal",
                        "Office for Foreigners (UDSC) & Voivodeship Offices",
                        "https://www.gov.pl/web/udsc-en",
                        "https://mos.cudzoziemcy.gov.pl/en",
                        "STATUTORY_PATHWAY_AND_JOB_EVIDENCE",
                        List.of("EU Blue Card Poland (Niebieska Karta)", "Type A Work Permit", "Temporary Residence & Work Permit"),
                        "Polish Voivodeship offices authorize Type A work permits and EU Blue Cards per job offer rather than publishing an employer accreditation whitelist.",
                        "Many B2B/contractor roles in Poland require existing local company registration or residency; confirm salaried employment permit sponsorship."
                ),
                new CountrySponsorshipProfile(
                        "Portugal",
                        "PT",
                        true,
                        "PT_IAPMEI_TECH_VISA",
                        "IAPMEI Tech Visa Certified Companies & AIMA Highly Qualified D3 Portal",
                        "Agency for Integration, Migration and Asylum (AIMA) & IAPMEI",
                        "https://www.iapmei.pt/PRODUTOS-E-SERVICOS/Empreendedorismo-Inovacao/Empreendedorismo-(1)/Tech-Visa.aspx",
                        "https://aima.gov.pt/",
                        "OFFICIAL_PUBLIC_REGISTER_AND_VACANCY_CHECK",
                        List.of("Portugal Tech Visa (IAPMEI Certified)", "D3 Highly Qualified Activity Visa", "EU Blue Card Portugal"),
                        "IAPMEI certifies Portuguese technology employers under the Tech Visa program to issue fast-track Terms of Responsibility alongside statutory D3 / EU Blue Card routes.",
                        DEFAULT_VACANCY_CAVEAT
                ),
                new CountrySponsorshipProfile(
                        "Spain",
                        "ES",
                        false,
                        "ES_UGE_LARGE_COMPANIES",
                        "Spanish Ministry of Inclusion / UGE-CE Highly Qualified Professional (Ley 14/2013)",
                        "Unidad de Grandes Empresas y Colectivos Estrategicos (UGE-CE)",
                        "https://www.inclusion.gob.es/en/web/migraciones/trabajar-en-espana",
                        "https://prie.comercio.gob.es/en-us/Paginas/Profesionales-altamente-cualificados.aspx",
                        "STATUTORY_PATHWAY_AND_JOB_EVIDENCE",
                        List.of("Highly Qualified Professional Permit (PAC - UGE-CE)", "EU Blue Card Spain", "Digital Nomad / Telework Visa"),
                        "Spain processes fast-track PAC (20 working day SLA) and EU Blue Card authorizations through UGE-CE per qualified employer offer rather than via an open sponsor registry.",
                        "Confirm whether the Spanish entity meets UGE-CE PAC eligibility or requires an NIE/existing Spanish work permit."
                )
        );
    }

    private void registerExtraAliases(
            Map<String, CountrySponsorshipProfile> aliases,
            Map<String, CountrySponsorshipProfile> canonical
    ) {
        mapAlias(aliases, canonical, "deu", "germany");
        mapAlias(aliases, canonical, "deutschland", "germany");
        mapAlias(aliases, canonical, "nld", "netherlands");
        mapAlias(aliases, canonical, "the netherlands", "netherlands");
        mapAlias(aliases, canonical, "nederland", "netherlands");
        mapAlias(aliases, canonical, "holland", "netherlands");
        mapAlias(aliases, canonical, "lux", "luxembourg");
        mapAlias(aliases, canonical, "luxemburg", "luxembourg");
        mapAlias(aliases, canonical, "che", "switzerland");
        mapAlias(aliases, canonical, "schweiz", "switzerland");
        mapAlias(aliases, canonical, "suisse", "switzerland");
        mapAlias(aliases, canonical, "svizzera", "switzerland");
        mapAlias(aliases, canonical, "irl", "ireland");
        mapAlias(aliases, canonical, "eire", "ireland");
        mapAlias(aliases, canonical, "aut", "austria");
        mapAlias(aliases, canonical, "osterreich", "austria");
        mapAlias(aliases, canonical, "bel", "belgium");
        mapAlias(aliases, canonical, "belgie", "belgium");
        mapAlias(aliases, canonical, "belgique", "belgium");
        mapAlias(aliases, canonical, "swe", "sweden");
        mapAlias(aliases, canonical, "sverige", "sweden");
        mapAlias(aliases, canonical, "dnk", "denmark");
        mapAlias(aliases, canonical, "danmark", "denmark");
        mapAlias(aliases, canonical, "fin", "finland");
        mapAlias(aliases, canonical, "suomi", "finland");
        mapAlias(aliases, canonical, "fra", "france");
        mapAlias(aliases, canonical, "pol", "poland");
        mapAlias(aliases, canonical, "polska", "poland");
        mapAlias(aliases, canonical, "prt", "portugal");
        mapAlias(aliases, canonical, "esp", "spain");
        mapAlias(aliases, canonical, "espana", "spain");
    }

    private void mapAlias(
            Map<String, CountrySponsorshipProfile> aliases,
            Map<String, CountrySponsorshipProfile> canonical,
            String alias,
            String canonicalKey
    ) {
        CountrySponsorshipProfile profile = canonical.get(canonicalKey);
        if (profile != null) {
            aliases.put(normalizeToken(alias), profile);
        }
    }

    private String normalizeToken(String raw) {
        String decomposed = Normalizer.normalize(raw.trim(), Normalizer.Form.NFKD);
        return DIACRITICS_PATTERN.matcher(decomposed)
                .replaceAll("")
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }
}

