import { HttpHeaders, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import {
  CoverLetterResponse,
  CoverLetterSummaryDto,
  GenerateCoverLetterRequest,
  UpdateCoverLetterRequest
} from '../models/cover-letter.models';
import { CoverLetterService } from './cover-letter.service';

describe('CoverLetterService', () => {
  let service: CoverLetterService;
  let httpMock: HttpTestingController;

  const summaryV1: CoverLetterSummaryDto = {
    uuid: 'letter-uuid-1',
    jobId: 101,
    jobTitle: 'Staff Java Clearing Architect',
    companyName: 'Adyen N.V.',
    jobCountry: 'Netherlands',
    tailoredResumeUuid: 'tailored-uuid-1',
    tailoredResumeTitle: 'Staff Java · Adyen (v1)',
    title: 'Cover Letter · Staff Java Clearing Architect · Adyen N.V. (v1)',
    tone: 'PROFESSIONAL',
    versionNumber: 1,
    wordCount: 312,
    aiProvider: 'GEMINI',
    aiModelUsed: 'gemini-2.5-flash',
    editedByUser: false,
    generatedAt: '2026-11-09T09:00:00Z',
    createdAt: '2026-11-09T09:00:00Z',
    updatedAt: '2026-11-09T09:00:00Z'
  };

  const detailV1: CoverLetterResponse = {
    ...summaryV1,
    personalNotes: 'Highlight SEPA Instant scalability',
    content: 'Dear Hiring Team at Adyen N.V.,\n\nI am writing to apply...\n\nSincerely,\nElena Weber',
    metadata: {
      salutation: 'Dear Hiring Team at Adyen N.V.,',
      signOff: 'Sincerely,\nElena Weber',
      candidateFullName: 'Elena Weber',
      candidateEmail: 'elena@eurojob.eu',
      candidateLocation: 'Berlin, Germany',
      candidateHeadline: 'Senior Java Backend Engineer',
      workAuthorizationSummary: 'Requires Visa Sponsorship · Open to relocation · 30-day notice',
      relocationReadinessNote: 'Open to relocating to Netherlands · Adyen N.V. is a verified sponsor in Netherlands',
      companyConnectionSummary: 'Strong architectural alignment with Adyen SEPA instant clearing.',
      keyAlignmentPoints: ['Java 21 & Spring Boot SEPA ledgers', 'Verified M.Sc. Computer Science'],
      highlightedSkills: ['Java 21', 'Spring Boot', 'PostgreSQL'],
      groundingWarnings: [],
      wordCount: 312
    }
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(CoverLetterService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should load filtered and unfiltered cover letter lists', () => {
    service.loadCoverLetters(101).subscribe(list => {
      expect(list.length).toBe(1);
    });
    const filteredReq = httpMock.expectOne('/api/v1/tailored-documents/cover-letters?jobId=101');
    expect(filteredReq.request.method).toBe('GET');
    filteredReq.flush([summaryV1]);
    expect(service.coverLetters().length).toBe(1);

    service.loadCoverLetters(null).subscribe(list => {
      expect(list.length).toBe(1);
    });
    const allReq = httpMock.expectOne('/api/v1/tailored-documents/cover-letters');
    expect(allReq.request.method).toBe('GET');
    allReq.flush([summaryV1]);
  });

  it('should generate, inspect, update, and delete cover letters updating signals', () => {
    const genReqPayload: GenerateCoverLetterRequest = {
      jobId: 101,
      tailoredResumeUuid: 'tailored-uuid-1',
      tone: 'CONFIDENT',
      personalNotes: 'Highlight SEPA Instant scalability',
      forceRefresh: true
    };

    service.generateCoverLetter(genReqPayload).subscribe(res => {
      expect(res.uuid).toBe('letter-uuid-1');
    });
    const genReq = httpMock.expectOne('/api/v1/tailored-documents/cover-letter/generate');
    expect(genReq.request.method).toBe('POST');
    expect(genReq.request.body).toEqual(genReqPayload);
    genReq.flush(detailV1);

    expect(service.selectedCoverLetter()?.uuid).toBe('letter-uuid-1');
    expect(service.coverLetters().length).toBe(1);

    service.getCoverLetter('letter-uuid-1').subscribe(detail => {
      expect(detail.title).toBe(detailV1.title);
    });
    const getReq = httpMock.expectOne('/api/v1/tailored-documents/cover-letter/letter-uuid-1');
    expect(getReq.request.method).toBe('GET');
    getReq.flush(detailV1);

    const updatePayload: UpdateCoverLetterRequest = {
      title: 'Refined Adyen Letter',
      tone: 'CONCISE',
      personalNotes: 'Emphasize 5,000 TPS ledger',
      content: 'Dear Hiring Team at Adyen N.V.,\n\nUpdated crisp pitch.\n\nSincerely,\nElena Weber'
    };
    service.updateCoverLetter('letter-uuid-1', updatePayload).subscribe(updated => {
      expect(updated.editedByUser).toBeTrue();
    });
    const putReq = httpMock.expectOne('/api/v1/tailored-documents/cover-letter/letter-uuid-1');
    expect(putReq.request.method).toBe('PUT');
    putReq.flush({
      ...detailV1,
      title: 'Refined Adyen Letter',
      tone: 'CONCISE',
      editedByUser: true,
      wordCount: 15
    });

    expect(service.selectedCoverLetter()?.editedByUser).toBeTrue();
    expect(service.coverLetters()[0].title).toBe('Refined Adyen Letter');
    expect(service.coverLetters()[0].wordCount).toBe(15);

    service.deleteCoverLetter('letter-uuid-1').subscribe(res => {
      expect(res.success).toBeTrue();
    });
    const delReq = httpMock.expectOne('/api/v1/tailored-documents/cover-letter/letter-uuid-1');
    expect(delReq.request.method).toBe('DELETE');
    delReq.flush({ success: true, timestamp: '2026-11-09T09:10:00Z' });

    expect(service.coverLetters().length).toBe(0);
    expect(service.selectedCoverLetter()).toBeNull();
  });

  it('should export PDF, DOCX, and TXT files and decode JSON errors inside Blobs', async () => {
    const pdfBlob = new Blob(['%PDF-1.4 mock'], { type: 'application/pdf' });
    let capturedName = '';
    service.exportCoverLetter('letter-uuid-1', 'pdf').subscribe(file => {
      capturedName = file.fileName;
      expect(file.format).toBe('pdf');
      expect(file.blob.size).toBe(pdfBlob.size);
    });

    expect(service.exportingFormat()).toBe('pdf');
    expect(service.isExporting()).toBeTrue();

    const pdfReq = httpMock.expectOne('/api/v1/tailored-documents/cover-letter/letter-uuid-1/export?format=pdf');
    expect(pdfReq.request.method).toBe('GET');
    expect(pdfReq.request.responseType).toBe('blob');
    pdfReq.flush(pdfBlob, {
      headers: new HttpHeaders({
        'content-disposition': 'attachment; filename="elena-weber-adyen-n-v-cover-letter.pdf"'
      })
    });
    expect(capturedName).toBe('elena-weber-adyen-n-v-cover-letter.pdf');
    expect(service.isExporting()).toBeFalse();

    const txtBlob = new Blob(['Plain text letter'], { type: 'text/plain; charset=UTF-8' });
    service.exportCoverLetter('letter-uuid-1', 'txt').subscribe(file => {
      expect(file.fileName).toBe('elena-weber-adyen-n-v-cover-letter.txt');
      expect(file.format).toBe('txt');
    });
    const txtReq = httpMock.expectOne('/api/v1/tailored-documents/cover-letter/letter-uuid-1/export?format=txt');
    txtReq.flush(txtBlob, {
      headers: new HttpHeaders({
        'content-disposition': 'attachment; filename="elena-weber-adyen-n-v-cover-letter.txt"'
      })
    });

    // JSON error inside Blob gets parsed cleanly
    let capturedErrMessage = '';
    service.exportCoverLetter('letter-uuid-1', 'docx').subscribe({
      error: err => {
        capturedErrMessage = err?.error?.message ?? '';
      }
    });
    const docxErrReq = httpMock.expectOne('/api/v1/tailored-documents/cover-letter/letter-uuid-1/export?format=docx');
    const errorBlob = new Blob([JSON.stringify({ message: 'Cover letter missing on server' })], {
      type: 'application/json'
    });
    docxErrReq.flush(errorBlob, { status: 404, statusText: 'Not Found' });

    await new Promise(resolve => setTimeout(resolve, 10));
    expect(capturedErrMessage).toBe('Cover letter missing on server');
  });
});

