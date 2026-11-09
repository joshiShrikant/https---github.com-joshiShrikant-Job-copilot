import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import {
  ApproveMasterResumeRequest,
  MAX_RESUME_FILE_SIZE_BYTES,
  ResumeParseResponse,
  ResumeResponse
} from '../models/resume.models';
import { ResumeService } from './resume.service';

describe('ResumeService', () => {
  let service: ResumeService;
  let httpMock: HttpTestingController;

  const mockResume: ResumeResponse = {
    uuid: '550e8400-e29b-41d4-a716-446655440000',
    title: 'Senior Java Master CV',
    fileName: 'alex-mueller-cv.pdf',
    fileSizeBytes: 245_760,
    mimeType: 'application/pdf',
    resumeType: 'MASTER',
    storageProvider: 'LOCAL',
    versionNumber: 1,
    isMasterApproved: false,
    createdAt: '2026-02-01T12:00:00Z',
    updatedAt: '2026-02-01T12:00:00Z'
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });

    service = TestBed.inject(ResumeService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should validate PDF/DOCX file extension, MIME type, non-empty size, and 10 MB limit', () => {
    const validPdf = new File([new Uint8Array(1024)], 'cv.pdf', { type: 'application/pdf' });
    expect(service.validateResumeFile(validPdf)).toBeNull();

    const wrongExt = new File(['hello'], 'notes.txt', { type: 'text/plain' });
    expect(service.validateResumeFile(wrongExt)).toContain('.pdf and .docx');

    const emptyFile = new File([], 'empty.pdf', { type: 'application/pdf' });
    expect(service.validateResumeFile(emptyFile)).toContain('empty');

    const oversized = {
      name: 'huge.pdf',
      size: MAX_RESUME_FILE_SIZE_BYTES + 1,
      type: 'application/pdf'
    } as File;
    expect(service.validateResumeFile(oversized)).toContain('10 MB');
  });

  it('should load, upload FormData, download Blob filename from Content-Disposition, and delete resumes', () => {
    service.loadResumes().subscribe(list => {
      expect(list.length).toBe(1);
    });
    httpMock.expectOne('/api/v1/resumes').flush([mockResume]);
    expect(service.resumes().length).toBe(1);

    const file = new File(['%PDF-1.4'], 'cv.pdf', { type: 'application/pdf' });
    service.uploadMasterResume(file, 'Backend Master v2').subscribe();
    const uploadReq = httpMock.expectOne('/api/v1/resumes/upload');
    expect(uploadReq.request.method).toBe('POST');
    expect(uploadReq.request.body instanceof FormData).toBeTrue();
    uploadReq.flush({ ...mockResume, uuid: 'uuid-2', title: 'Backend Master v2', versionNumber: 2 });
    expect(service.resumes().length).toBe(2);

    let downloadedName = '';
    service.downloadResume('uuid-2', 'fallback.pdf').subscribe(res => {
      downloadedName = res.fileName;
    });
    const downloadReq = httpMock.expectOne('/api/v1/resumes/uuid-2/download');
    expect(downloadReq.request.responseType).toBe('blob');
    downloadReq.flush(new Blob(['pdf-bytes'], { type: 'application/pdf' }), {
      headers: {
        'Content-Disposition': 'attachment; filename="backend-master-v2.pdf"'
      }
    });
    expect(downloadedName).toBe('backend-master-v2.pdf');

    service.deleteResume('uuid-2').subscribe();
    const deleteReq = httpMock.expectOne('/api/v1/resumes/uuid-2');
    expect(deleteReq.request.method).toBe('DELETE');
    deleteReq.flush({ success: true, message: 'Deleted' });
    expect(service.resumes().length).toBe(1);
  });

  it('should format file sizes cleanly in KB and MB', () => {
    expect(service.formatFileSize(512)).toBe('1 KB');
    expect(service.formatFileSize(245_760)).toBe('240 KB');
    expect(service.formatFileSize(2 * 1024 * 1024)).toBe('2.00 MB');
  });

  it('should parse master resume, fetch parsed draft, and approve while demoting prior master resume', () => {
    const v1Approved: ResumeResponse = {
      ...mockResume,
      uuid: 'uuid-v1',
      versionNumber: 1,
      isMasterApproved: true,
      parseStatus: 'APPROVED'
    };
    const v2Unparsed: ResumeResponse = {
      ...mockResume,
      uuid: 'uuid-v2',
      versionNumber: 2,
      isMasterApproved: false,
      parseStatus: 'NOT_PARSED'
    };

    service.loadResumes().subscribe();
    httpMock.expectOne('/api/v1/resumes').flush([v2Unparsed, v1Approved]);

    const parseResponse: ResumeParseResponse = {
      resumeUuid: 'uuid-v2',
      title: 'Senior Java Master CV',
      fileName: 'alex-mueller-cv.pdf',
      mimeType: 'application/pdf',
      versionNumber: 2,
      isMasterApproved: false,
      parseStatus: 'PARSED',
      parsedAt: '2026-03-02T10:00:00Z',
      approvedAt: null,
      aiProvider: 'GEMINI',
      aiModelUsed: 'gemini-2.5-flash',
      extractedCharCount: 2450,
      parsedData: {
        profile: {
          fullName: 'Alex Mueller',
          currentDesignation: 'Senior Java Engineer',
          yearsOfExperience: 7,
          currentCountry: 'Germany',
          currentCity: 'Munich',
          linkedinUrl: 'https://linkedin.com/in/alex-mueller',
          githubUrl: null,
          portfolioUrl: null,
          bio: 'Backend specialist',
          workAuthorizationStatus: 'EU_CITIZEN',
          relocationPreference: 'ALREADY_IN_EUROPE'
        },
        summary: 'Backend specialist',
        skills: [{ skillName: 'Java 21', yearsExperience: 7, proficiencyLevel: 'EXPERT', primary: true }],
        experiences: [],
        educations: [],
        projects: [],
        languages: ['English (C2)'],
        certifications: []
      },
      warnings: []
    };

    service.parseMasterResume('uuid-v2', true).subscribe(res => {
      expect(res.parseStatus).toBe('PARSED');
    });
    const parseReq = httpMock.expectOne('/api/v1/resumes/uuid-v2/parse?forceRefresh=true');
    expect(parseReq.request.method).toBe('POST');
    parseReq.flush(parseResponse);

    const updatedV2 = service.resumes().find(r => r.uuid === 'uuid-v2');
    expect(updatedV2?.parseStatus).toBe('PARSED');
    expect(updatedV2?.aiModelUsed).toBe('gemini-2.5-flash');
    expect(updatedV2?.hasParsedContent).toBeTrue();

    service.getParsedResume('uuid-v2').subscribe(res => {
      expect(res.parsedData?.skills.length).toBe(1);
    });
    const getParsedReq = httpMock.expectOne('/api/v1/resumes/uuid-v2/parsed');
    expect(getParsedReq.request.method).toBe('GET');
    getParsedReq.flush(parseResponse);

    const approveReqPayload: ApproveMasterResumeRequest = {
      resumeUuid: 'uuid-v2',
      approvedParsedData: parseResponse.parsedData!,
      syncMode: 'MERGE'
    };
    service.approveMasterResume(approveReqPayload).subscribe(res => {
      expect(res.isMasterApproved).toBeTrue();
    });
    const approveReq = httpMock.expectOne('/api/v1/resumes/master/approve');
    expect(approveReq.request.method).toBe('POST');
    approveReq.flush({
      ...v2Unparsed,
      isMasterApproved: true,
      parseStatus: 'APPROVED',
      approvedAt: '2026-03-02T10:05:00Z',
      hasParsedContent: true
    });

    const v2After = service.resumes().find(r => r.uuid === 'uuid-v2');
    const v1After = service.resumes().find(r => r.uuid === 'uuid-v1');
    expect(v2After?.isMasterApproved).toBeTrue();
    expect(v2After?.parseStatus).toBe('APPROVED');
    expect(v1After?.isMasterApproved).toBeFalse();
    expect(v1After?.parseStatus).toBe('PARSED');
  });
});

