import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ResumeService } from '../../../../core/services/resume.service';
import { ResumesSectionComponent } from './resumes-section.component';

describe('ResumesSectionComponent', () => {
  let httpMock: HttpTestingController;
  let resumeService: ResumeService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ResumesSectionComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    resumeService = TestBed.inject(ResumeService);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should reject invalid file extension on selection and accept valid PDF upload', () => {
    const fixture = TestBed.createComponent(ResumesSectionComponent);
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    const comp = fixture.componentInstance;

    const badFile = new File(['hello'], 'notes.txt', { type: 'text/plain' });
    const badDataTransfer = new DataTransfer();
    badDataTransfer.items.add(badFile);

    const fileInput = host.querySelector<HTMLInputElement>('input[type="file"]');
    if (fileInput) {
      fileInput.files = badDataTransfer.files;
      fileInput.dispatchEvent(new Event('change'));
    }
    fixture.detectChanges();

    expect(comp.selectedFile()).toBeNull();
    expect(host.querySelector('.status-banner.error')?.textContent).toContain('.pdf and .docx');

    const goodFile = new File(['%PDF-1.4 mock content'], 'alex_backend_cv.pdf', {
      type: 'application/pdf'
    });
    const goodDataTransfer = new DataTransfer();
    goodDataTransfer.items.add(goodFile);
    if (fileInput) {
      fileInput.files = goodDataTransfer.files;
      fileInput.dispatchEvent(new Event('change'));
    }
    fixture.detectChanges();

    expect(comp.selectedFile()).toBeTruthy();
    expect(comp.resumeTitle()).toBe('alex backend cv');

    host.querySelector<HTMLButtonElement>('.upload-submit-btn')?.click();

    const uploadReq = httpMock.expectOne('/api/v1/resumes/upload');
    expect(uploadReq.request.method).toBe('POST');
    uploadReq.flush({
      uuid: 'res-uuid-1',
      title: 'alex backend cv',
      fileName: 'alex_backend_cv.pdf',
      fileSizeBytes: 2048,
      mimeType: 'application/pdf',
      resumeType: 'MASTER',
      storageProvider: 'LOCAL',
      versionNumber: 1,
      isMasterApproved: true,
      createdAt: '2026-03-01T10:00:00Z',
      updatedAt: '2026-03-01T10:00:00Z'
    });
    fixture.detectChanges();

    expect(host.querySelector('.resume-card')?.textContent).toContain('v1');
    expect(host.querySelector('.resume-card')?.textContent).toContain('Master Approved');
  });

  it('should download resume blob with object URL cleanup and show conflict message on referenced resume delete', () => {
    resumeService.loadResumes().subscribe();
    httpMock.expectOne('/api/v1/resumes').flush([
      {
        uuid: 'res-uuid-2',
        title: 'Master CV v1',
        fileName: 'master.docx',
        fileSizeBytes: 4096,
        mimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
        resumeType: 'MASTER',
        storageProvider: 'LOCAL',
        versionNumber: 1,
        isMasterApproved: false,
        createdAt: '2026-03-01T10:00:00Z',
        updatedAt: '2026-03-01T10:00:00Z'
      }
    ]);

    const fixture = TestBed.createComponent(ResumesSectionComponent);
    fixture.detectChanges();

    const createUrlSpy = spyOn(URL, 'createObjectURL').and.returnValue('blob:mock-url');
    const revokeUrlSpy = spyOn(URL, 'revokeObjectURL');

    const host = fixture.nativeElement as HTMLElement;
    const downloadBtn = Array.from(host.querySelectorAll<HTMLButtonElement>('.resume-actions button')).find(
      b => b.textContent?.includes('Download')
    );
    downloadBtn?.click();

    const dlReq = httpMock.expectOne('/api/v1/resumes/res-uuid-2/download');
    dlReq.flush(new Blob(['docx'], { type: 'application/octet-stream' }), {
      headers: { 'Content-Disposition': 'attachment; filename="master.docx"' }
    });

    expect(createUrlSpy).toHaveBeenCalled();
    expect(revokeUrlSpy).toHaveBeenCalledWith('blob:mock-url');

    // Attempt delete -> simulate 409 conflict
    host.querySelector<HTMLButtonElement>('.resume-actions .btn-danger-ghost')?.click();
    fixture.detectChanges();

    const confirmBtn = Array.from(
      host.querySelectorAll<HTMLButtonElement>('.resume-actions .btn-danger-ghost')
    ).find(b => b.textContent?.includes('Confirm Delete'));
    confirmBtn?.click();

    const delReq = httpMock.expectOne('/api/v1/resumes/res-uuid-2');
    delReq.flush(
      { message: 'Cannot delete master resume referenced by a tailored resume.' },
      { status: 409, statusText: 'Conflict' }
    );
    fixture.detectChanges();

    expect(host.querySelector('.status-banner.error')?.textContent).toContain(
      'Cannot delete master resume referenced by a tailored resume.'
    );
    expect(resumeService.resumes().length).toBe(1);
  });

  it('should trigger AI parse, open review panel, approve edits, and emit masterApproved', () => {
    resumeService.loadResumes().subscribe();
    httpMock.expectOne('/api/v1/resumes').flush([
      {
        uuid: 'res-parse-1',
        title: 'Elena Master CV',
        fileName: 'elena.pdf',
        fileSizeBytes: 8192,
        mimeType: 'application/pdf',
        resumeType: 'MASTER',
        storageProvider: 'LOCAL',
        versionNumber: 1,
        isMasterApproved: false,
        parseStatus: 'NOT_PARSED',
        createdAt: '2026-03-01T10:00:00Z',
        updatedAt: '2026-03-01T10:00:00Z'
      }
    ]);

    const fixture = TestBed.createComponent(ResumesSectionComponent);
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    const emittedApprovals: string[] = [];
    fixture.componentInstance.masterApproved.subscribe(res => emittedApprovals.push(res.uuid));

    host.querySelector<HTMLButtonElement>('.resume-actions .parse-btn')?.click();

    const parseReq = httpMock.expectOne('/api/v1/resumes/res-parse-1/parse?forceRefresh=true');
    expect(parseReq.request.method).toBe('POST');
    parseReq.flush({
      resumeUuid: 'res-parse-1',
      title: 'Elena Master CV',
      fileName: 'elena.pdf',
      mimeType: 'application/pdf',
      versionNumber: 1,
      isMasterApproved: false,
      parseStatus: 'PARSED',
      parsedAt: '2026-03-02T10:00:00Z',
      approvedAt: null,
      aiProvider: 'GEMINI',
      aiModelUsed: 'gemini-2.5-flash',
      extractedCharCount: 2200,
      parsedData: {
        profile: {
          fullName: 'Elena Weber',
          currentDesignation: 'Senior Java Engineer',
          yearsOfExperience: 8,
          currentCountry: 'Germany',
          currentCity: 'Berlin',
          linkedinUrl: null,
          githubUrl: null,
          portfolioUrl: null,
          bio: 'Backend architect.',
          workAuthorizationStatus: 'EU_CITIZEN',
          relocationPreference: 'ALREADY_IN_EUROPE'
        },
        summary: 'Backend architect.',
        skills: [
          { skillName: 'Java 21', yearsExperience: 8, proficiencyLevel: 'EXPERT', primary: true }
        ],
        experiences: [],
        educations: [],
        projects: [],
        languages: ['English'],
        certifications: []
      },
      warnings: []
    });
    fixture.detectChanges();

    expect(host.querySelector('app-resume-review-panel')).toBeTruthy();
    expect(host.querySelector('.resume-card')?.textContent).toContain('Parsed · Review Pending');

    host.querySelector<HTMLButtonElement>('app-resume-review-panel .approve-submit-btn')?.click();

    const approveReq = httpMock.expectOne('/api/v1/resumes/master/approve');
    expect(approveReq.request.method).toBe('POST');
    approveReq.flush({
      uuid: 'res-parse-1',
      title: 'Elena Master CV',
      fileName: 'elena.pdf',
      fileSizeBytes: 8192,
      mimeType: 'application/pdf',
      resumeType: 'MASTER',
      storageProvider: 'LOCAL',
      versionNumber: 1,
      isMasterApproved: true,
      parseStatus: 'APPROVED',
      approvedAt: '2026-03-02T10:05:00Z',
      hasParsedContent: true,
      profileSyncSummary: {
        syncMode: 'MERGE',
        profileCreated: true,
        profileUpdated: false,
        skillsAdded: 1,
        skillsUpdated: 0,
        experiencesAdded: 0,
        experiencesUpdated: 0,
        educationsAdded: 0,
        educationsUpdated: 0,
        syncedAt: '2026-03-02T10:05:00Z'
      },
      createdAt: '2026-03-01T10:00:00Z',
      updatedAt: '2026-03-02T10:05:00Z'
    });
    fixture.detectChanges();

    expect(emittedApprovals).toEqual(['res-parse-1']);
    expect(host.querySelector('.status-banner.success')?.textContent).toContain('1 skills');
    expect(host.querySelector('.resume-card')?.textContent).toContain('Master Approved');
  });

  it('should clear prior resume draft when switching to parse a different resume that fails with 503', () => {
    resumeService.loadResumes().subscribe();
    httpMock.expectOne('/api/v1/resumes').flush([
      {
        uuid: 'res-a',
        title: 'Resume A',
        fileName: 'a.pdf',
        fileSizeBytes: 2048,
        mimeType: 'application/pdf',
        resumeType: 'MASTER',
        storageProvider: 'LOCAL',
        versionNumber: 2,
        isMasterApproved: false,
        parseStatus: 'PARSED',
        createdAt: '2026-03-02T10:00:00Z',
        updatedAt: '2026-03-02T10:00:00Z'
      },
      {
        uuid: 'res-b',
        title: 'Resume B',
        fileName: 'b.pdf',
        fileSizeBytes: 2048,
        mimeType: 'application/pdf',
        resumeType: 'MASTER',
        storageProvider: 'LOCAL',
        versionNumber: 1,
        isMasterApproved: false,
        parseStatus: 'NOT_PARSED',
        createdAt: '2026-03-01T10:00:00Z',
        updatedAt: '2026-03-01T10:00:00Z'
      }
    ]);

    const fixture = TestBed.createComponent(ResumesSectionComponent);
    fixture.detectChanges();
    const host = fixture.nativeElement as HTMLElement;

    // Open Review on Resume A first
    const reviewBtns = host.querySelectorAll<HTMLButtonElement>('.resume-actions .review-btn');
    reviewBtns[0].click();

    httpMock.expectOne('/api/v1/resumes/res-a/parsed').flush({
      resumeUuid: 'res-a',
      title: 'Resume A',
      fileName: 'a.pdf',
      mimeType: 'application/pdf',
      versionNumber: 2,
      isMasterApproved: false,
      parseStatus: 'PARSED',
      parsedAt: '2026-03-02T10:00:00Z',
      approvedAt: null,
      aiProvider: 'GEMINI',
      aiModelUsed: 'gemini-2.5-flash',
      extractedCharCount: 1500,
      parsedData: {
        profile: {
          fullName: 'Alice A',
          currentDesignation: 'Resume A Architect',
          yearsOfExperience: 5,
          currentCountry: 'Germany',
          currentCity: 'Berlin',
          linkedinUrl: null,
          githubUrl: null,
          portfolioUrl: null,
          bio: null,
          workAuthorizationStatus: null,
          relocationPreference: null
        },
        summary: 'Resume A summary',
        skills: [],
        experiences: [],
        educations: [],
        projects: [],
        languages: [],
        certifications: []
      },
      warnings: []
    });
    fixture.detectChanges();
    expect(host.querySelector<HTMLInputElement>('#rev-designation')?.value).toBe('Resume A Architect');

    // Now click Parse with AI on Resume B and return 503 -> Resume A draft must NOT leak into Resume B panel
    const parseBtns = host.querySelectorAll<HTMLButtonElement>('.resume-actions .parse-btn');
    parseBtns[1].click();

    httpMock
      .expectOne('/api/v1/resumes/res-b/parse?forceRefresh=true')
      .flush({ message: 'AI service unavailable' }, { status: 503, statusText: 'Service Unavailable' });
    fixture.detectChanges();

    expect(host.querySelector('.status-banner.error')?.textContent).toContain('AI service unavailable');
    expect(fixture.componentInstance.activeReviewUuid()).toBe('res-b');
    expect(fixture.componentInstance.reviewParseResult()).toBeNull();
    expect(host.querySelector('app-resume-review-panel .empty-state')).toBeTruthy();
    expect(host.querySelector('#rev-designation')).toBeNull();
  });
});

