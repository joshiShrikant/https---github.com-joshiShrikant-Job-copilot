import { PageResponse } from './api.models';

export type VisaSponsorshipStatus =
  | 'VERIFIED_SPONSOR'
  | 'SPONSORSHIP_MENTIONED'
  | 'RELOCATION_ASSISTANCE_MENTIONED'
  | 'POSSIBLE_UNCONFIRMED'
  | 'NOT_MENTIONED'
  | 'UNAVAILABLE';

export type RegistrySyncStatus = 'SUCCESS' | 'UNAVAILABLE' | 'BLOCKED' | 'FAILED';

export type SponsorConfidenceLevel =
  | 'VERIFIED'
  | 'HIGH'
  | 'MEDIUM'
  | 'LOW'
  | 'UNVERIFIED';

export type SponsorshipRecordStatus =
  | 'RECOGNIZED_SPONSOR'
  | 'VISA_CAPABLE'
  | 'SUSPENDED'
  | 'REVOKED';

export interface VisaSponsorshipStatusMeta {
  readonly status: VisaSponsorshipStatus;
  readonly label: string;
  readonly tone: 'success' | 'brand' | 'warning' | 'danger' | 'neutral';
  readonly icon: string;
  readonly summary: string;
}

export const VISA_SPONSORSHIP_STATUS_OPTIONS: readonly VisaSponsorshipStatusMeta[] = [
  {
    status: 'VERIFIED_SPONSOR',
    label: 'Verified sponsor',
    tone: 'success',
    icon: 'verified_user',
    summary: 'Verified accredited/certified employer record; individual vacancy sponsorship must still be confirmed.'
  },
  {
    status: 'SPONSORSHIP_MENTIONED',
    label: 'Sponsorship explicitly mentioned',
    tone: 'brand',
    icon: 'fact_check',
    summary: 'Vacancy text or structured analysis explicitly confirms visa sponsorship or EU permit support.'
  },
  {
    status: 'RELOCATION_ASSISTANCE_MENTIONED',
    label: 'Relocation assistance mentioned',
    tone: 'brand',
    icon: 'flight_takeoff',
    summary: 'Relocation support is mentioned, which does not automatically guarantee visa sponsorship.'
  },
  {
    status: 'POSSIBLE_UNCONFIRMED',
    label: 'Possible but unconfirmed',
    tone: 'warning',
    icon: 'hourglass_top',
    summary: 'International role signals exist, but sponsorship eligibility requires recruiter verification.'
  },
  {
    status: 'NOT_MENTIONED',
    label: 'Not mentioned',
    tone: 'neutral',
    icon: 'help_outline',
    summary: 'No official register match and no sponsorship or relocation mention detected.'
  },
  {
    status: 'UNAVAILABLE',
    label: 'Unavailable',
    tone: 'danger',
    icon: 'block',
    summary: 'Vacancy declines sponsorship, requires existing authorization, or accreditation is revoked.'
  }
];

export const VISA_SPONSORSHIP_STATUS_META: Record<VisaSponsorshipStatus, VisaSponsorshipStatusMeta> =
  VISA_SPONSORSHIP_STATUS_OPTIONS.reduce(
    (acc, item) => ({ ...acc, [item.status]: item }),
    {} as Record<VisaSponsorshipStatus, VisaSponsorshipStatusMeta>
  );

export interface CountrySponsorshipProfile {
  readonly country: string;
  readonly isoCode: string;
  readonly officialRegisterAvailable: boolean;
  readonly registrySource: string;
  readonly registryName: string;
  readonly primaryAuthority: string;
  readonly officialSourceUrl: string;
  readonly publicRegisterUrl: string;
  readonly verificationMethod: string;
  readonly visaPathways: readonly string[];
  readonly guidanceSummary: string;
  readonly vacancyRuleWarning: string;
  readonly syncedEntriesCount: number;
  readonly lastSyncedAt: string | null;
}

export interface SponsorshipVerificationHistoryItem {
  readonly id: number;
  readonly companyId: number | null;
  readonly jobId: number | null;
  readonly companyName: string;
  readonly normalizedCompanyName: string;
  readonly country: string;
  readonly countryIsoCode: string;
  readonly status: VisaSponsorshipStatus;
  readonly statusLabel: string | null;
  readonly vacancyStatus: VisaSponsorshipStatus | null;
  readonly vacancySponsorshipConfirmed: boolean;
  readonly vacancySponsorshipAssumed: boolean;
  readonly confidenceLevel: SponsorConfidenceLevel;
  readonly registrySource: string | null;
  readonly registryIdentifier: string | null;
  readonly officialRegisteredName: string | null;
  readonly evidenceSummary: string | null;
  readonly evidenceSignals: readonly string[];
  readonly sourceUrl: string | null;
  readonly vacancyCaveat: string | null;
  readonly verifiedAt: string;
}

export interface SponsorshipVerificationResponse {
  readonly verificationId: number;
  readonly companyId: number | null;
  readonly jobId: number | null;
  readonly companyName: string;
  readonly normalizedCompanyName: string;
  readonly country: string;
  readonly countryIsoCode: string;
  readonly status: VisaSponsorshipStatus;
  readonly statusLabel: string;
  readonly vacancyStatus: VisaSponsorshipStatus;
  readonly vacancyStatusLabel: string;
  readonly vacancySponsorshipConfirmed: boolean;
  readonly vacancySponsorshipAssumed: boolean;
  readonly companyRecognizedSponsor: boolean;
  readonly confidenceLevel: SponsorConfidenceLevel;
  readonly registrySource: string | null;
  readonly registryIdentifier: string | null;
  readonly officialName: string | null;
  readonly evidenceNotes: string | null;
  readonly evidenceSignals: readonly string[];
  readonly sourceUrl: string | null;
  readonly vacancyCaveat: string | null;
  readonly verifiedAt: string;
  readonly countryProfile: CountrySponsorshipProfile | null;
  readonly recentHistory: readonly SponsorshipVerificationHistoryItem[];
}

export type PagedSponsorshipHistoryResponse = PageResponse<SponsorshipVerificationHistoryItem>;

export interface OfficialSponsorRegistryEntry {
  readonly id: number;
  readonly country: string;
  readonly registrySource: string;
  readonly registryIdentifier: string | null;
  readonly officialName: string;
  readonly normalizedName: string;
  readonly status: SponsorshipRecordStatus;
  readonly sourceUrl: string | null;
  readonly active: boolean;
  readonly syncedAt: string;
}

export interface RegistrySyncStatusInfo {
  readonly registrySource: string;
  readonly country: string;
  readonly status: RegistrySyncStatus;
  readonly sourceUrl: string | null;
  readonly activeEntriesCount: number;
  readonly entriesParsed: number;
  readonly entriesUpserted: number;
  readonly companiesLinked: number;
  readonly errorMessage: string | null;
  readonly syncedAt: string | null;
}

export interface PagedIndRegistryResponse extends PageResponse<OfficialSponsorRegistryEntry> {
  readonly lastSyncStatus: RegistrySyncStatusInfo | null;
}

export interface IndSyncResult {
  readonly registrySource: string;
  readonly country: string;
  readonly status: RegistrySyncStatus;
  readonly sourceUrl: string | null;
  readonly entriesParsed: number;
  readonly entriesUpserted: number;
  readonly entriesDeactivated: number;
  readonly companiesLinked: number;
  readonly executionDurationMs: number;
  readonly message: string | null;
  readonly syncedAt: string;
}

export interface VerifySponsorshipParams {
  readonly companyName: string;
  readonly country: string;
  readonly jobId?: number | null;
  readonly postingText?: string | null;
}

export function isSafeHttpUrl(raw: string | null | undefined): boolean {
  if (!raw || !raw.trim()) {
    return false;
  }
  try {
    const parsed = new URL(raw.trim());
    return (
      (parsed.protocol === 'https:' || parsed.protocol === 'http:') &&
      parsed.hostname.trim().length > 0
    );
  } catch {
    return false;
  }
}

export function formatVisaSponsorshipStatusLabel(
  value: VisaSponsorshipStatus | null | undefined
): string {
  if (!value) {
    return 'Not mentioned';
  }
  return VISA_SPONSORSHIP_STATUS_META[value]?.label ?? value;
}

