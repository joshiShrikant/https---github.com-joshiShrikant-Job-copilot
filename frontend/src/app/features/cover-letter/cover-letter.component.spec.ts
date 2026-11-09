import { HttpHeaders, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import {
  CoverLetterResponse,
  CoverLetterSummaryDto
} from '../../core/models/cover-letter.models';
import { JobDetail, JobSummaryPageResponse } from '../../core/models/job.models';
import { UserProfileResponse } from '../../core/models/profile.models';
import { TailoredResumeSummaryDto } from '../../core/models/tailored-resume.models';
import { CoverLetterComponent } from './cover-letter.component';

describe('CoverLetterComponent', () => {
  let fixture: ComponentFixture<CoverLetterComponent>;
  let httpMock: HttpTestingController;
  let queryParamSubject: BehaviorSubject<ReturnType<typeof convertToParamMap>>;

  const verifiedProfile: UserProfileResponse = {
    uuid: 'user-uuid-1',
    firstName: 'Elena',
    lastName: 'Weber',
    fullName: 'Elena Weber',
    email: 'elena@eurojob.eu',
    currentDesignation: 'Senior Java Backend Engineer',
    yearsOfExperience: 6.5,
    currentCountry: 'Germany',
    currentCity: 'Berlin',
    expectedSalaryMin: 85000,
    expectedSalaryCurrency: 'EUR',
    noticePeriodDays: 30,
    workAuthorizationStatus: 'NEEDS_SPONSORSHIP',
    relocationPreference: 'WILLING_TO_RELOCATE',
    linkedinUrl: 'https://linkedin.com/in/elena',
    githubUrl: null,
    portfolioUrl: null,
    bio: 'Java 21 & Spring Boot architect building SEPA payment rails.',
    preferredJobTitles: ['Staff Java Engineer'],
    preferredCountries: ['Netherlands', 'Germany'],
    skills: [],
    educations: [],
    experiences: [],
    createdAt: '2026-10-01T10:00:00Z',
    updatedAt: '2026-10-05T10:00:00Z'
  };

  const jobsPage: JobSummaryPageResponse = {
    content: [
      {
        id: 101,
        title: 'Staff Java Clearing Architect',
        companyName: 'Adyen N.V.',
        location: 'Amsterdam, Netherlands',
        city: 'Amsterdam',
        country: 'Netherlands',
        workArrangement: 'HYBRID',
        salaryMin: 90000,
        salaryMax: 120000,
        salaryCurrency: 'EUR',
        sponsorStatus: 'VERIFIED_SPONSOR',
        matchPercentage: 88,
        sourceProvider: 'ARBEITNOW',
        postedAt: '2026-11-08T10:00:00Z',
        isSaved: true,
        isInterested: true
      }
    ],
    pageNumber: 0,
    pageSize: 50,
    totalElements: 1,
    totalPages: 1,
    isFirst: true,
    isLast: true,
    hasNext: false,
    hasPrevious: false
  };

  const deepJob501: JobDetail = {
    id: 501,
    title: 'Principal JVM Engineer',
    companyName: 'Booking.com',
    country: 'Netherlands',
    city: 'Amsterdam',
    location: 'Amsterdam, Netherlands',
    workArrangement: 'HYBRID',
    employmentType: 'FULL_TIME',
    salaryMin: 100000,
    salaryMax: 130000,
    salaryCurrency: 'EUR',
    description: 'Design resilient high-volume booking platform services.',
    skills: ['Java 21', 'Spring Boot', 'Kafka'],
    sponsorStatus: 'VERIFIED_SPONSOR',
    matchPercentage: 91,
    originalUrl: 'https://careers.booking.com/501',
    postedAt: '2026-11-08T12:00:00Z'
  };

  const tailoredV1: TailoredResumeSummaryDto = {
    uuid: 'tailored-v1',
    masterResumeUuid: 'master-uuid-1',
    masterResumeTitle: 'Master Backend CV',
    jobId: 501,
    jobTitle: 'Principal JVM Engineer',
    companyName: 'Booking.com',
    jobCountry: 'Netherlands',
    title: 'Principal JVM Engineer · Booking.com (v1)',
    versionNumber: 1,
    templateName: 'MODERN',
    matchScore: 91,
    atsKeywordCoverage: 85,
    aiProvider: 'GEMINI',
    aiModelUsed: 'gemini-2.5-flash',
    editedByUser: false,
    hasPdfExport: false,
    hasDocxExport: false,
    generatedAt: '2026-11-09T08:00:00Z',
    createdAt: '2026-11-09T08:00:00Z',
    updatedAt: '2026-11-09T08:00:00Z'
  };

  const summaryV1: CoverLetterSummaryDto = {
    uuid: 'letter-uuid-1',
    jobId: 501,
    jobTitle: 'Principal JVM Engineer',
    companyName: 'Booking.com',
    jobCountry: 'Netherlands',
    tailoredResumeUuid: 'tailored-v1',
    tailoredResumeTitle: 'Principal JVM Engineer · Booking.com (v1)',
    title: 'Cover Letter · Principal JVM Engineer · Booking.com (v1)',
    tone: 'CONFIDENT',
    versionNumber: 1,
    wordCount: 38,
    aiProvider: 'GEMINI',
    aiModelUsed: 'gemini-2.5-flash',
    editedByUser: false,
    generatedAt: '2026-11-09T09:00:00Z',
    createdAt: '2026-11-09T09:00:00Z',
    updatedAt: '2026-11-09T09:00:00Z'
  };

  const detailV1: CoverLetterResponse = {
    ...summaryV1,
    personalNotes: 'Focus on distributed JVM resilience and Amsterdam relocation readiness.',
    content:
      'Dear Hiring Team at Booking.com,\n\nWith over six years architecting resilient Java 21 systems, I am eager to join Booking.com in Amsterdam.\n\nRelocating from Berlin with a 30-day notice period, I welcome Booking.com sponsorship support.\n\nSincerely,\nElena Weber',
    metadata: {
      salutation: 'Dear Hiring Team at Booking.com,',
      signOff: 'Sincerely,\nElena Weber',
      candidateFullName: 'Elena Weber',
      candidateEmail: 'elena@eurojob.eu',
      candidateLocation: 'Berlin, Germany',
      candidateHeadline: 'Senior Java Backend Engineer',
      workAuthorizationSummary: 'Requires Visa Sponsorship · Open to relocation · 30-day notice',
      relocationReadinessNote:
        'Open to relocating to Netherlands · Booking.com is a verified sponsor in Netherlands',
      companyConnectionSummary:
        'Directly aligns Java 21 high-throughput architecture with Booking.com core engineering in Amsterdam.',
      keyAlignmentPoints: [
        '6.5 years Java 21 and Spring Boot distributed systems ownership',
        'Verified relocation readiness within 30 days'
      ],
      highlightedSkills: ['Java 21', 'Spring Boot'],
      groundingWarnings: ["Grounding notice: draft text referenced unverified JD skill 'Kafka'."],
      wordCount: 38
    }
  };

  function setupTestBed(initialParams: Record<string, string> = {}): void {
    queryParamSubject = new BehaviorSubject(convertToParamMap(initialParams));

    TestBed.configureTestingModule({
      imports: [CoverLetterComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: {
            queryParamMap: queryParamSubject.asObservable()
          }
        }
      ]
    });

    fixture = TestBed.createComponent(CoverLetterComponent);
    httpMock = TestBed.inject(HttpTestingController);
  }

  afterEach(() => {
    httpMock.verify();
  });

  it('should display non-blocking career evidence banner when profile and resumes are missing', () => {
    setupTestBed();
    fixture.detectChanges();

    httpMock.expectOne('/api/v1/jobs?page=0&size=50&sort=matchScore,desc').flush(jobsPage);
    httpMock.expectOne('/api/v1/tailored-documents/resumes').flush([]);
    httpMock.expectOne('/api/v1/resumes').flush([]);
    httpMock.expectOne('/api/v1/profile').flush('Not Found', { status: 404, statusText: 'Not Found' });
    httpMock.expectOne('/api/v1/tailored-documents/cover-letters').flush([]);

    httpMock.expectOne('/api/v1/profile/skills').flush([]);
    httpMock.expectOne('/api/v1/profile/education').flush([]);
    httpMock.expectOne('/api/v1/profile/experience').flush([]);
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('[data-cl-no-evidence]')?.textContent).toContain(
      'Add Career Evidence First'
    );
    expect(host.querySelector('[data-cl-workspace-empty]')?.textContent).toContain(
      'Ready to compose a company-aligned European cover letter'
    );
  });

  it('should preselect deep-linked jobId & tailoredResumeUuid, generate, edit & save, preview A4 sheet, copy text, filter, and delete', async () => {
    setupTestBed({ jobId: '501', tailoredResumeUuid: 'tailored-v1' });
    fixture.detectChanges();

    httpMock.expectOne('/api/v1/jobs?page=0&size=50&sort=matchScore,desc').flush(jobsPage);
    httpMock.expectOne('/api/v1/tailored-documents/resumes').flush([tailoredV1]);
    httpMock.expectOne('/api/v1/resumes').flush([]);
    httpMock.expectOne('/api/v1/profile').flush(verifiedProfile);
    httpMock.expectOne('/api/v1/tailored-documents/cover-letters').flush([]);
    httpMock.expectOne('/api/v1/jobs/501').flush(deepJob501);
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    const jobSelect = host.querySelector<HTMLSelectElement>('[data-cl-job-select]')!;
    expect(jobSelect.value).toBe('501');

    const tailoredSelect = host.querySelector<HTMLSelectElement>('[data-cl-tailored-select]')!;
    expect(tailoredSelect.value).toBe('tailored-v1');

    const toneSelect = host.querySelector<HTMLSelectElement>('[data-cl-tone-select]')!;
    toneSelect.value = 'CONFIDENT';
    toneSelect.dispatchEvent(new Event('change'));

    const notesInput = host.querySelector<HTMLTextAreaElement>('[data-cl-notes-input]')!;
    notesInput.value = 'Focus on distributed JVM resilience and Amsterdam relocation readiness.';
    notesInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    host.querySelector<HTMLButtonElement>('[data-cl-action="generate"]')?.click();
    fixture.detectChanges();

    const genReq = httpMock.expectOne('/api/v1/tailored-documents/cover-letter/generate');
    expect(genReq.request.method).toBe('POST');
    expect(genReq.request.body).toEqual({
      jobId: 501,
      tailoredResumeUuid: 'tailored-v1',
      tone: 'CONFIDENT',
      personalNotes: 'Focus on distributed JVM resilience and Amsterdam relocation readiness.',
      forceRefresh: true
    });
    genReq.flush(detailV1);
    fixture.detectChanges();

    expect(host.querySelector('[data-cl-work-auth]')?.textContent).toContain('Requires Visa Sponsorship');
    expect(host.querySelector('[data-cl-relocation]')?.textContent).toContain('verified sponsor in Netherlands');
    expect(host.querySelector('[data-cl-company-connection]')?.textContent).toContain('Booking.com');
    expect(host.querySelector('[data-cl-alignment-points]')?.textContent).toContain('Java 21 and Spring Boot');
    expect(host.querySelector('[data-cl-highlighted-skills]')?.textContent).toContain('Java 21');
    expect(host.querySelector('[data-cl-grounding-warnings]')?.textContent).toContain('Kafka');
    expect(host.querySelector('[data-cl-linked-cv-link]')?.getAttribute('href')).toContain(
      '/resume-builder?jobId=501&tailoredUuid=tailored-v1'
    );

    // Edit title, tone to ACADEMIC, and content -> verify dirty badge and live word count
    const titleInput = host.querySelector<HTMLInputElement>('[data-cl-draft-title]')!;
    titleInput.value = 'Custom Booking Principal Cover Letter';
    titleInput.dispatchEvent(new Event('input'));

    const draftToneSelect = host.querySelector<HTMLSelectElement>('[data-cl-draft-tone]')!;
    draftToneSelect.value = 'ACADEMIC';
    draftToneSelect.dispatchEvent(new Event('change'));

    const bodyInput = host.querySelector<HTMLTextAreaElement>('[data-cl-draft-content]')!;
    bodyInput.value = 'Dear Hiring Team at Booking.com,\n\nOne two three four five six seven.\n\nSincerely,\nElena Weber';
    bodyInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(host.querySelector('[data-cl-dirty-badge]')?.textContent).toContain('Unsaved Changes');
    expect(host.querySelector('[data-cl-char-word-count]')?.textContent).toContain('15 words');

    // Save changes via PUT
    host.querySelector<HTMLButtonElement>('[data-cl-action="save"]')?.click();
    const putReq = httpMock.expectOne('/api/v1/tailored-documents/cover-letter/letter-uuid-1');
    expect(putReq.request.method).toBe('PUT');
    expect(putReq.request.body).toEqual({
      title: 'Custom Booking Principal Cover Letter',
      tone: 'ACADEMIC',
      personalNotes: 'Focus on distributed JVM resilience and Amsterdam relocation readiness.',
      content: 'Dear Hiring Team at Booking.com,\n\nOne two three four five six seven.\n\nSincerely,\nElena Weber'
    });
    putReq.flush({
      ...detailV1,
      title: 'Custom Booking Principal Cover Letter',
      tone: 'ACADEMIC',
      editedByUser: true,
      wordCount: 15,
      content: 'Dear Hiring Team at Booking.com,\n\nOne two three four five six seven.\n\nSincerely,\nElena Weber'
    });
    fixture.detectChanges();

    expect(host.querySelector('[data-cl-edited-badge]')?.textContent).toContain('Manually Edited');

    // Switch to preview tab and check academic serif layout
    host.querySelector<HTMLButtonElement>('[data-cl-tab="preview"]')?.click();
    fixture.detectChanges();
    const sheet = host.querySelector('[data-cover-letter-sheet]');
    expect(sheet?.classList.contains('is-academic')).toBeTrue();
    expect(sheet?.textContent).toContain('Elena Weber');
    expect(sheet?.textContent).toContain('Re: Application for Principal JVM Engineer');
    expect(sheet?.textContent).toContain('One two three four five six seven.');

    // Copy to clipboard
    if (navigator.clipboard && 'writeText' in navigator.clipboard) {
      spyOn(navigator.clipboard, 'writeText').and.resolveTo();
    } else {
      spyOn(document, 'execCommand').and.returnValue(true);
    }
    host.querySelector<HTMLButtonElement>('[data-cl-action="copy"]')?.click();
    await new Promise(resolve => setTimeout(resolve, 0));
    fixture.detectChanges();
    expect(host.querySelector('[data-cl-action="copy"]')?.textContent).toContain('Copied!');

    // Filter saved versions by job
    host.querySelector<HTMLButtonElement>('[data-cl-filter="job"]')?.click();
    const filterReq = httpMock.expectOne('/api/v1/tailored-documents/cover-letters?jobId=501');
    expect(filterReq.request.method).toBe('GET');
    filterReq.flush([{ ...summaryV1, title: 'Custom Booking Principal Cover Letter' }]);
    fixture.detectChanges();

    // Delete cover letter version via ConfirmDialog
    host.querySelector<HTMLButtonElement>('[data-cl-delete-uuid="letter-uuid-1"]')?.click();
    fixture.detectChanges();
    host.querySelector<HTMLButtonElement>('[data-dialog-action="confirm"]')?.click();

    const deleteReq = httpMock.expectOne('/api/v1/tailored-documents/cover-letter/letter-uuid-1');
    expect(deleteReq.request.method).toBe('DELETE');
    deleteReq.flush({ success: true, timestamp: '2026-11-09T10:00:00Z' });
    fixture.detectChanges();

    expect(host.querySelector('[data-cl-empty-versions]')?.textContent).toContain('No cover letters yet');
  });

  it('should auto-save unsaved edits before exporting DOCX/TXT and surface Blob JSON export errors', async () => {
    setupTestBed();
    fixture.detectChanges();

    httpMock.expectOne('/api/v1/jobs?page=0&size=50&sort=matchScore,desc').flush(jobsPage);
    httpMock.expectOne('/api/v1/tailored-documents/resumes').flush([]);
    httpMock.expectOne('/api/v1/resumes').flush([]);
    httpMock.expectOne('/api/v1/profile').flush(verifiedProfile);
    httpMock.expectOne('/api/v1/tailored-documents/cover-letters').flush([summaryV1]);
    httpMock.expectOne('/api/v1/tailored-documents/cover-letter/letter-uuid-1').flush(detailV1);
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;

    // Make unsaved edit in prose
    const bodyInput = host.querySelector<HTMLTextAreaElement>('[data-cl-draft-content]')!;
    bodyInput.value = 'Dear Hiring Team at Booking.com,\n\nUpdated paragraph ready for export.\n\nSincerely,\nElena Weber';
    bodyInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    spyOn(URL, 'createObjectURL').and.returnValue('blob:cl-docx');
    spyOn(URL, 'revokeObjectURL');
    const clickSpy = spyOn(HTMLAnchorElement.prototype, 'click');

    // Click export DOCX while dirty -> triggers PUT then GET .../export?format=docx
    host.querySelector<HTMLButtonElement>('[data-cl-action="export-docx"]')?.click();
    const putReq = httpMock.expectOne('/api/v1/tailored-documents/cover-letter/letter-uuid-1');
    expect(putReq.request.method).toBe('PUT');
    putReq.flush({
      ...detailV1,
      editedByUser: true,
      content: bodyInput.value,
      wordCount: 14
    });

    const docxReq = httpMock.expectOne('/api/v1/tailored-documents/cover-letter/letter-uuid-1/export?format=docx');
    expect(docxReq.request.method).toBe('GET');
    docxReq.flush(
      new Blob(['PK\u0003\u0004docx'], {
        type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
      }),
      {
        headers: new HttpHeaders({
          'content-disposition': 'attachment; filename="elena-booking-cover-letter.docx"'
        })
      }
    );
    fixture.detectChanges();

    expect(clickSpy).toHaveBeenCalled();
    expect(host.querySelector('[data-cl-success]')?.textContent).toContain(
      'Saved draft edits and exported DOCX'
    );

    // Export TXT while clean
    host.querySelector<HTMLButtonElement>('[data-cl-action="export-txt"]')?.click();
    const txtReq = httpMock.expectOne('/api/v1/tailored-documents/cover-letter/letter-uuid-1/export?format=txt');
    expect(txtReq.request.method).toBe('GET');
    txtReq.flush(new Blob(['Plain text'], { type: 'text/plain; charset=UTF-8' }), {
      headers: new HttpHeaders({
        'content-disposition': 'attachment; filename="elena-booking-cover-letter.txt"'
      })
    });
    fixture.detectChanges();

    // PDF export server error parses Blob JSON message cleanly
    host.querySelector<HTMLButtonElement>('[data-cl-action="export-pdf"]')?.click();
    const pdfErrReq = httpMock.expectOne('/api/v1/tailored-documents/cover-letter/letter-uuid-1/export?format=pdf');
    pdfErrReq.flush(
      new Blob([JSON.stringify({ message: 'PDF rendering failed cleanly' })], {
        type: 'application/json'
      }),
      {
        status: 500,
        statusText: 'Internal Server Error'
      }
    );

    for (let i = 0; i < 40 && !fixture.componentInstance.exportErrorMessage(); i++) {
      await new Promise(resolve => setTimeout(resolve, 5));
    }
    fixture.detectChanges();

    expect(host.querySelector('[data-cl-export-error]')?.textContent).toContain(
      'PDF rendering failed cleanly'
    );
  });
});
