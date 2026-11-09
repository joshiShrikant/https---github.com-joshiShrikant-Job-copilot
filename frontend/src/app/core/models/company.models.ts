import { PageResponse } from './api.models';
import {
  JobSummaryPageResponse,
  SelectFilterOption,
  SponsorConfidenceLevel,
  SponsorshipRegistryRecord
} from './job.models';

export type CompanyPriorityTier = 'TOP_PRIORITY' | 'TARGET' | 'WATCHLIST';

export type CompanySortOption =
  | 'activeJobs,desc'
  | 'verified,desc'
  | 'name,asc'
  | 'savedAt,desc';

export interface CompanySummary {
  readonly id: number;
  readonly name: string;
  readonly headquartersCountry: string | null;
  readonly headquartersCity: string | null;
  readonly industry: string | null;
  readonly companySize: string | null;
  readonly websiteUrl: string | null;
  readonly careersUrl: string | null;
  readonly logoUrl: string | null;
  readonly isVerifiedSponsor: boolean;
  readonly sponsorConfidenceLevel: SponsorConfidenceLevel;
  readonly activeJobsCount: number;
  readonly sponsorshipJobsCount: number;
  readonly remoteJobsCount: number;
  readonly techStack: readonly string[];
  readonly activeCountries: readonly string[];
  readonly registryCountries: readonly string[];
  readonly isSaved: boolean;
  readonly savedAt: string | null;
  readonly priorityTier: CompanyPriorityTier | null;
}

export interface CompanyDetail {
  readonly id: number;
  readonly name: string;
  readonly normalizedName: string;
  readonly description: string | null;
  readonly websiteUrl: string | null;
  readonly careersUrl: string | null;
  readonly logoUrl: string | null;
  readonly headquartersCountry: string | null;
  readonly headquartersCity: string | null;
  readonly industry: string | null;
  readonly companySize: string | null;
  readonly isVerifiedSponsor: boolean;
  readonly sponsorConfidenceLevel: SponsorConfidenceLevel;
  readonly activeJobsCount: number;
  readonly sponsorshipJobsCount: number;
  readonly remoteJobsCount: number;
  readonly techStack: readonly string[];
  readonly activeCountries: readonly string[];
  readonly activeCities: readonly string[];
  readonly vacancyCaveat: string | null;
  readonly countryGuideSummary: string | null;
  readonly officialRegisterUrl: string | null;
  readonly registryRecords: readonly SponsorshipRegistryRecord[];
  readonly isSaved: boolean;
  readonly savedAt: string | null;
  readonly priorityTier: CompanyPriorityTier | null;
  readonly userNotes: string | null;
  readonly createdAt: string | null;
  readonly updatedAt: string | null;
}

export interface PagedCompaniesResponse extends PageResponse<CompanySummary> {
  readonly verifiedSponsorsTotal: number;
  readonly savedCompaniesTotal: number;
}

export type CompanyJobsPageResponse = JobSummaryPageResponse;

export interface SaveCompanyRequest {
  readonly priorityTier?: CompanyPriorityTier | null;
  readonly notes?: string | null;
}

export interface CompanySaveResponse {
  readonly companyId: number;
  readonly companyName: string;
  readonly isSaved: boolean;
  readonly priorityTier: CompanyPriorityTier | null;
  readonly notes: string | null;
  readonly savedAt: string | null;
}

export interface CompanySearchFilters {
  readonly query: string;
  readonly country: string;
  readonly industry: string;
  readonly tech: string;
  readonly verifiedSponsorOnly: boolean;
  readonly savedOnly: boolean;
  readonly withOpenJobsOnly: boolean;
  readonly sort: CompanySortOption;
}

export const DEFAULT_COMPANY_SEARCH_FILTERS: CompanySearchFilters = {
  query: '',
  country: '',
  industry: '',
  tech: '',
  verifiedSponsorOnly: false,
  savedOnly: false,
  withOpenJobsOnly: false,
  sort: 'activeJobs,desc'
};

export function createDefaultCompanySearchFilters(): CompanySearchFilters {
  return { ...DEFAULT_COMPANY_SEARCH_FILTERS };
}

export function countActiveCompanyFilters(filters: CompanySearchFilters): number {
  let count = 0;
  if (filters.query.trim().length > 0) count++;
  if (filters.country.trim().length > 0) count++;
  if (filters.industry.trim().length > 0) count++;
  if (filters.tech.trim().length > 0) count++;
  if (filters.verifiedSponsorOnly) count++;
  if (filters.savedOnly) count++;
  if (filters.withOpenJobsOnly) count++;
  return count;
}

export const COMPANY_PRIORITY_TIER_OPTIONS: readonly {
  readonly value: CompanyPriorityTier;
  readonly label: string;
  readonly tone: 'success' | 'brand' | 'neutral';
  readonly icon: string;
}[] = [
  { value: 'TOP_PRIORITY', label: 'Top Priority', tone: 'success', icon: 'star' },
  { value: 'TARGET', label: 'Target Employer', tone: 'brand', icon: 'bookmark_added' },
  { value: 'WATCHLIST', label: 'Watchlist', tone: 'neutral', icon: 'visibility' }
];

export const COMPANY_PRIORITY_TIER_META: Record<
  CompanyPriorityTier,
  (typeof COMPANY_PRIORITY_TIER_OPTIONS)[number]
> = COMPANY_PRIORITY_TIER_OPTIONS.reduce(
  (acc, item) => ({ ...acc, [item.value]: item }),
  {} as Record<CompanyPriorityTier, (typeof COMPANY_PRIORITY_TIER_OPTIONS)[number]>
);

export const COMPANY_SORT_OPTIONS: readonly SelectFilterOption<CompanySortOption>[] = [
  { value: 'activeJobs,desc', label: 'Most Open Jobs' },
  { value: 'verified,desc', label: 'Verified Sponsors First' },
  { value: 'savedAt,desc', label: 'Recently Bookmarked' },
  { value: 'name,asc', label: 'Company Name (A–Z)' }
];

export function formatCompanyPriorityTierLabel(
  value: CompanyPriorityTier | null | undefined
): string {
  if (!value) {
    return 'Target Employer';
  }
  return COMPANY_PRIORITY_TIER_META[value]?.label ?? value;
}

export function isSafeHttpUrl(rawUrl: string | null | undefined): boolean {
  if (!rawUrl || !rawUrl.trim()) {
    return false;
  }
  try {
    const parsed = new URL(rawUrl.trim());
    return (
      (parsed.protocol === 'https:' || parsed.protocol === 'http:') &&
      parsed.hostname.trim().length > 0
    );
  } catch {
    return false;
  }
}

