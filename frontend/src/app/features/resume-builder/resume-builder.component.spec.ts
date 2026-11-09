import { HttpHeaders, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { JobDetail, JobSummaryPageResponse } from '../../core/models/job.models';
import { ResumeResponse } from '../../core/models/resume.models';
import {
  TailoredResumeResponse,
  TailoredResumeSummaryDto
} from '../../core/models/tailored-resume.models';
import { ResumeBuilderComponent } from './resume-builder.component';

describe('ResumeBuilderComponent', () => {
  let fixture: ComponentFixture<ResumeBuilderComponent>;
  let httpMock: HttpTestingController;
  let queryParamSubject: BehaviorSubject<ReturnType<typeof convertToParamMap>>;

  const masterResume: ResumeResponse = {
    uuid: 'master-uuid-1',
    title: 'Master Backend CV',
    fileName: 'elena-master.pdf',
    fileSizeBytes: 32768,
    mimeType: 'application/pdf',
    resumeType: 'MASTER',
    storageProvider: 'LOCAL',
    versionNumber: 1,
    isMasterApproved: true,
    parseStatus: 'APPROVED',
    hasParsedContent: true,
    createdAt: '2026-11-01T10:00:00Z',
    updatedAt: '2026-11-01T10:00:00Z'
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

  const summaryV1: TailoredResumeSummaryDto = {
    uuid: 'tailored-v1',
    masterResumeUuid: 'master-uuid-1',
    masterResumeTitle: 'Master Backend CV',
    jobId: 101,
    jobTitle: 'Staff Java Clearing Architect',
    companyName: 'Adyen N.V.',
    jobCountry: 'Netherlands',
    title: 'Staff Java Clearing Architect · Adyen N.V. (v1)',
    versionNumber: 1,
    templateName: 'MODERN',
    matchScore: 88,
    atsKeywordCoverage: 80,
    aiProvider: 'GEMINI',
    aiModelUsed: 'gemini-2.5-flash',
    editedByUser: false,
    hasPdfExport: false,
    hasDocxExport: false,
    generatedAt: '2026-11-09T09:00:00Z',
    createdAt: '2026-11-09T09:00:00Z',
    updatedAt: '2026-11-09T09:00:00Z'
  };

  const detailV1: TailoredResumeResponse = {
    ...summaryV1,
    tailoringFocus: 'Highlight SEPA Instant settlement throughput',
    tailoredSummary: 'Senior Java architect grounded in SEPA Instant payment rails.',
    optimizedExperiences: [
      {
        companyName: 'ClearPay Systems',
        designation: 'Senior Java Engineer',
        location: 'Berlin, Germany',
        startDate: '2021-03-01',
        endDate: null,
        current: true,
        originalBullets: ['Built SEPA Instant ledger handling 5,000 TPS'],
        tailoredBullets: ['Architected Java 21 SEPA Instant ledger processing 5,000 TPS'],
        matchedKeywords: ['Java 21', 'Spring Boot'],
        relevanceScore: 95
      }
    ],
    highlightedSkills: ['Java 21', 'Spring Boot', 'PostgreSQL'],
    missingKeywords: ['Kafka'],
    resumeData: {
      header: {
        fullName: 'Elena Weber',
        headline: 'Staff Java Settlement Engineer',
        email: 'elena@example.com',
        location: 'Berlin, Germany',
        country: 'Germany',
        yearsOfExperience: 6.5,
        workAuthorizationSummary: 'EU/EEA Citizen',
        linkedinUrl: 'https://linkedin.com/in/elena',
        githubUrl: 'javascript:alert(1)',
        portfolioUrl: null
      },
      tailoredSummary: 'Senior Java architect grounded in SEPA Instant payment rails.',
      highlightedSkills: ['Java 21', 'Spring Boot', 'PostgreSQL'],
      additionalSkills: ['Docker'],
      matchedKeywords: ['Java 21', 'Spring Boot', 'PostgreSQL', 'Docker'],
      missingKeywords: ['Kafka'],
      tailoringRationale: 'Prioritized Java 21 settlement throughput.',
      optimizedExperiences: [
        {
          companyName: 'ClearPay Systems',
          designation: 'Senior Java Engineer',
          location: 'Berlin, Germany',
          startDate: '2021-03-01',
          endDate: null,
          current: true,
          originalBullets: ['Built SEPA Instant ledger handling 5,000 TPS'],
          tailoredBullets: ['Architected Java 21 SEPA Instant ledger processing 5,000 TPS'],
          matchedKeywords: ['Java 21', 'Spring Boot'],
          relevanceScore: 95
        }
      ],
      projects: [
        {
          name: 'SEPA Instant Core',
          description: 'Sub-50ms clearing engine.',
          tailoredBullets: ['Maintained 99.99% uptime across EU hubs'],
          technologies: ['Java 21', 'PostgreSQL'],
          url: 'https://github.com/elena/sepa-core'
        }
      ],
      educations: [
        {
          institution: 'Technical University of Munich',
          degree: 'M.Sc.',
          fieldOfStudy: 'Computer Science',
          startDate: '2016-10-01',
          endDate: '2018-09-30',
          gradeOrGpa: '1.3',
          country: 'Germany'
        }
      ],
      languages: ['English (C2)', 'German (B2)'],
      certifications: ['Oracle Certified Professional: Java SE 21'],
      groundingWarnings: ["Removed unverified skill 'Rust' from highlighted skills."]
    }
  };

  function setupTestBed(initialParams: Record<string, string> = {}): void {
    queryParamSubject = new BehaviorSubject(convertToParamMap(initialParams));

    TestBed.configureTestingModule({
      imports: [ResumeBuilderComponent],
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

    fixture = TestBed.createComponent(ResumeBuilderComponent);
    httpMock = TestBed.inject(HttpTestingController);
  }

  afterEach(() => {
    httpMock.verify();
  });

  it('should handle empty workspace without master CV and display upload guidance', () => {
    setupTestBed();
    fixture.detectChanges();

    httpMock.expectOne('/api/v1/resumes').flush([]);
    httpMock.expectOne('/api/v1/jobs?page=0&size=50&sort=matchScore,desc').flush(jobsPage);
    httpMock.expectOne('/api/v1/tailored-documents/resumes').flush([]);
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('[data-builder-no-master]')?.textContent).toContain('Master CV Required');
    expect(host.querySelector('[data-builder-workspace-empty]')?.textContent).toContain(
      'Ready to build a JD-tailored European CV'
    );
    const generateBtn = host.querySelector<HTMLButtonElement>('[data-builder-action="generate"]');
    expect(generateBtn?.disabled).toBeTrue();
  });

  it('should preselect deep-linked jobId, generate a grounded draft, edit & save, preview CV, filter versions, and delete', () => {
    setupTestBed({ jobId: '501' });
    fixture.detectChanges();

    httpMock.expectOne('/api/v1/resumes').flush([masterResume]);
    httpMock.expectOne('/api/v1/jobs?page=0&size=50&sort=matchScore,desc').flush(jobsPage);
    httpMock.expectOne('/api/v1/jobs/501').flush(deepJob501);
    httpMock.expectOne('/api/v1/tailored-documents/resumes').flush([]);
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    const jobSelect = host.querySelector<HTMLSelectElement>('[data-builder-job-select]');
    expect(jobSelect?.value).toBe('501');

    const focusArea = host.querySelector<HTMLTextAreaElement>('[data-builder-focus-input]')!;
    focusArea.value = 'Emphasize distributed booking throughput';
    focusArea.dispatchEvent(new Event('input'));

    host.querySelector<HTMLButtonElement>('[data-builder-action="generate"]')?.click();
    fixture.detectChanges();

    const genReq = httpMock.expectOne('/api/v1/tailored-documents/resume/generate');
    expect(genReq.request.method).toBe('POST');
    expect(genReq.request.body).toEqual({
      jobId: 501,
      masterResumeUuid: 'master-uuid-1',
      templateName: 'MODERN',
      tailoringFocus: 'Emphasize distributed booking throughput',
      forceRefresh: true
    });
    genReq.flush({ ...detailV1, jobId: 501, jobTitle: 'Principal JVM Engineer', companyName: 'Booking.com' });
    fixture.detectChanges();

    expect(host.querySelector('[data-builder-ats-coverage]')?.textContent).toContain('80%');
    expect(host.querySelector('[data-builder-matched-keywords]')?.textContent).toContain('Java 21');
    expect(host.querySelector('[data-builder-missing-keywords]')?.textContent).toContain('Kafka');
    expect(host.querySelector('[data-builder-grounding-warnings]')?.textContent).toContain('Rust');
    expect(host.querySelector('[data-builder-export-bar]')?.textContent).toContain('Download PDF');
    expect(host.querySelector('[data-builder-export-bar]')?.textContent).toContain('Download DOCX');
    expect(
      host.querySelector('[data-builder-action="draft-cover-letter"]')?.getAttribute('href')
    ).toContain('/cover-letter?jobId=501&tailoredResumeUuid=tailored-v1');

    // Toggle original bullets comparison
    host.querySelector<HTMLButtonElement>('[data-builder-toggle-orig="0"]')?.click();
    fixture.detectChanges();
    expect(host.querySelector('[data-builder-orig-box="0"]')?.textContent).toContain(
      'Built SEPA Instant ledger handling 5,000 TPS'
    );

    // Edit draft title & summary and save changes
    const titleInput = host.querySelector<HTMLInputElement>('[data-builder-draft-title]')!;
    titleInput.value = 'Custom Booking JVM Draft';
    titleInput.dispatchEvent(new Event('input'));
    const templateSelect = host.querySelector<HTMLSelectElement>('[data-builder-draft-template]')!;
    templateSelect.value = 'CLASSIC';
    templateSelect.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    expect(host.querySelector('[data-builder-dirty-badge]')?.textContent).toContain('Unsaved Changes');

    host.querySelector<HTMLButtonElement>('[data-builder-action="save"]')?.click();
    const putReq = httpMock.expectOne('/api/v1/tailored-documents/resume/tailored-v1');
    expect(putReq.request.method).toBe('PUT');
    expect(putReq.request.body.title).toBe('Custom Booking JVM Draft');
    expect(putReq.request.body.templateName).toBe('CLASSIC');
    putReq.flush({
      ...detailV1,
      jobId: 501,
      jobTitle: 'Principal JVM Engineer',
      companyName: 'Booking.com',
      title: 'Custom Booking JVM Draft',
      templateName: 'CLASSIC',
      editedByUser: true,
      atsKeywordCoverage: 92
    });
    fixture.detectChanges();

    expect(host.querySelector('[data-builder-edited-badge]')?.textContent).toContain('Manually Edited');
    expect(host.querySelector('[data-builder-ats-coverage]')?.textContent).toContain('92%');

    // Switch to Preview tab and verify CV rendering + unsafe javascript: URL filtering
    host.querySelector<HTMLButtonElement>('[data-builder-tab="preview"]')?.click();
    fixture.detectChanges();

    const cvSheet = host.querySelector('[data-builder-cv-sheet]');
    expect(cvSheet?.classList.contains('template-classic')).toBeTrue();
    expect(cvSheet?.textContent).toContain('Elena Weber');
    expect(cvSheet?.textContent).toContain('EXECUTIVE SUMMARY');
    expect(cvSheet?.textContent).toContain('Technical University of Munich');
    const previewLinks = Array.from(cvSheet?.querySelectorAll('a[href]') ?? []).map(a =>
      a.getAttribute('href')
    );
    expect(previewLinks).toContain('https://linkedin.com/in/elena');
    expect(previewLinks.some(href => href?.startsWith('javascript:'))).toBeFalse();

    // Direct PDF export when clean
    const createUrlSpy = spyOn(URL, 'createObjectURL').and.returnValue('blob:pdf-preview');
    spyOn(URL, 'revokeObjectURL');
    const clickSpy = spyOn(HTMLAnchorElement.prototype, 'click');

    host.querySelector<HTMLButtonElement>('[data-builder-action="export-pdf"]')?.click();
    const exportPdfReq = httpMock.expectOne(
      '/api/v1/tailored-documents/resume/tailored-v1/export?format=pdf'
    );
    expect(exportPdfReq.request.method).toBe('GET');
    exportPdfReq.flush(new Blob(['%PDF-1.4'], { type: 'application/pdf' }), {
      headers: new HttpHeaders({
        'content-disposition': 'attachment; filename="custom-booking-classic-cv.pdf"'
      })
    });
    fixture.detectChanges();

    expect(createUrlSpy).toHaveBeenCalled();
    expect(clickSpy).toHaveBeenCalled();
    expect(host.querySelector('[data-builder-export-bar]')?.textContent).toContain('PDF cached');

    // Filter saved versions by selected job
    host.querySelector<HTMLButtonElement>('[data-builder-filter="job"]')?.click();
    const filteredReq = httpMock.expectOne('/api/v1/tailored-documents/resumes?jobId=501');
    expect(filteredReq.request.method).toBe('GET');
    filteredReq.flush([{ ...summaryV1, jobId: 501, companyName: 'Booking.com', title: 'Custom Booking JVM Draft' }]);
    fixture.detectChanges();

    // Delete tailored version via ConfirmDialog
    host.querySelector<HTMLButtonElement>('[data-builder-delete-uuid="tailored-v1"]')?.click();
    fixture.detectChanges();
    host.querySelector<HTMLButtonElement>('[data-dialog-action="confirm"]')?.click();

    const deleteReq = httpMock.expectOne('/api/v1/tailored-documents/resume/tailored-v1');
    expect(deleteReq.request.method).toBe('DELETE');
    deleteReq.flush({ success: true, timestamp: '2026-11-09T10:00:00Z' });
    fixture.detectChanges();

    expect(host.querySelector('[data-builder-empty-versions]')?.textContent).toContain('No tailored CVs yet');
  });

  it('should reorder work experiences, switch preview templates with save-before-export DOCX, and show export errors', async () => {
    const multiExpDetail: TailoredResumeResponse = {
      ...detailV1,
      resumeData: {
        ...detailV1.resumeData!,
        optimizedExperiences: [
          detailV1.resumeData!.optimizedExperiences[0],
          {
            companyName: 'FinGrid Europe GmbH',
            designation: 'Java Developer',
            location: 'Munich, Germany',
            startDate: '2019-01-01',
            endDate: '2021-02-28',
            current: false,
            originalBullets: ['Built REST APIs in Spring Boot'],
            tailoredBullets: ['Engineered Spring Boot SEPA onboarding microservices'],
            matchedKeywords: ['Spring Boot'],
            relevanceScore: 84
          }
        ]
      }
    };

    setupTestBed();
    fixture.detectChanges();

    httpMock.expectOne('/api/v1/resumes').flush([masterResume]);
    httpMock.expectOne('/api/v1/jobs?page=0&size=50&sort=matchScore,desc').flush(jobsPage);
    httpMock.expectOne('/api/v1/tailored-documents/resumes').flush([summaryV1]);
    httpMock.expectOne('/api/v1/tailored-documents/resume/tailored-v1').flush(multiExpDetail);
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;

    // Reorder second experience to first position
    host.querySelector<HTMLButtonElement>('[data-builder-move-exp-up="1"]')?.click();
    fixture.detectChanges();

    expect(host.querySelector('[data-builder-exp-index="0"]')?.textContent).toContain('FinGrid Europe GmbH');
    expect(host.querySelector('[data-builder-dirty-badge]')?.textContent).toContain('Unsaved Changes');

    // Switch to preview and pick EUROPASS template via preview quick-switcher
    host.querySelector<HTMLButtonElement>('[data-builder-tab="preview"]')?.click();
    fixture.detectChanges();
    host.querySelector<HTMLButtonElement>('[data-preview-template="EUROPASS"]')?.click();
    fixture.detectChanges();

    const previewSheet = host.querySelector('[data-builder-cv-sheet]');
    expect(previewSheet?.classList.contains('template-europass')).toBeTrue();
    expect(previewSheet?.textContent).toContain('PERSONAL PROFILE & SUMMARY');

    // Export DOCX while dirty -> automatically saves (PUT) reordered experience + EUROPASS template, then downloads DOCX
    spyOn(URL, 'createObjectURL').and.returnValue('blob:docx-preview');
    spyOn(URL, 'revokeObjectURL');
    spyOn(HTMLAnchorElement.prototype, 'click');

    host.querySelector<HTMLButtonElement>('[data-builder-action="export-docx"]')?.click();
    const putReq = httpMock.expectOne('/api/v1/tailored-documents/resume/tailored-v1');
    expect(putReq.request.method).toBe('PUT');
    expect(putReq.request.body.templateName).toBe('EUROPASS');
    expect(putReq.request.body.optimizedExperiences[0].companyName).toBe('FinGrid Europe GmbH');
    putReq.flush({
      ...multiExpDetail,
      templateName: 'EUROPASS',
      editedByUser: true
    });

    const exportDocxReq = httpMock.expectOne(
      '/api/v1/tailored-documents/resume/tailored-v1/export?format=docx'
    );
    expect(exportDocxReq.request.method).toBe('GET');
    exportDocxReq.flush(
      new Blob(['PK\u0003\u0004docx'], {
        type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
      }),
      {
        headers: new HttpHeaders({
          'content-disposition': 'attachment; filename="elena-europass-cv.docx"'
        })
      }
    );
    fixture.detectChanges();

    expect(host.querySelector('[data-builder-export-bar]')?.textContent).toContain('DOCX cached');

    // Export failure displays user-friendly error banner
    host.querySelector<HTMLButtonElement>('[data-builder-action="export-pdf"]')?.click();
    const failedExportReq = httpMock.expectOne(
      '/api/v1/tailored-documents/resume/tailored-v1/export?format=pdf'
    );
    failedExportReq.flush(
      new Blob([JSON.stringify({ message: 'Export generation error' })], {
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

    expect(host.querySelector('[data-builder-export-error]')?.textContent).toContain(
      'Export generation error'
    );
  });
});
