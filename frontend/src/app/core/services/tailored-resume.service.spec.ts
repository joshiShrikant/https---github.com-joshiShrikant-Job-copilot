import { HttpHeaders, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import {
  GenerateTailoredResumeRequest,
  TailoredResumeResponse,
  TailoredResumeSummaryDto,
  UpdateTailoredResumeRequest
} from '../models/tailored-resume.models';
import { TailoredResumeService } from './tailored-resume.service';

describe('TailoredResumeService', () => {
  let service: TailoredResumeService;
  let httpMock: HttpTestingController;

  const summaryV1: TailoredResumeSummaryDto = {
    uuid: 'tailored-uuid-1',
    masterResumeUuid: 'master-uuid-1',
    masterResumeTitle: 'Master Backend CV',
    jobId: 101,
    jobTitle: 'Staff Java Architect',
    companyName: 'Adyen N.V.',
    jobCountry: 'Netherlands',
    title: 'Staff Java Architect · Adyen N.V. (v1)',
    versionNumber: 1,
    templateName: 'MODERN',
    matchScore: 88.5,
    atsKeywordCoverage: 80.0,
    aiProvider: 'GEMINI',
    aiModelUsed: 'gemini-2.5-flash',
    editedByUser: false,
    hasPdfExport: false,
    hasDocxExport: false,
    generatedAt: '2026-11-09T09:00:00Z',
    createdAt: '2026-11-09T09:00:00Z',
    updatedAt: '2026-11-09T09:00:00Z'
  };

  const fullDetailV1: TailoredResumeResponse = {
    ...summaryV1,
    tailoringFocus: 'Highlight SEPA Instant throughput',
    tailoredSummary: 'Senior Java Engineer with deep distributed settlement architecture expertise.',
    optimizedExperiences: [
      {
        companyName: 'ClearPay Systems',
        designation: 'Senior Java Engineer',
        location: 'Berlin, Germany',
        startDate: '2021-03-01',
        endDate: null,
        current: true,
        originalBullets: ['Built SEPA Instant ledger processing 5,000 TPS in Java 21'],
        tailoredBullets: ['Architected Java 21 & Spring Boot SEPA ledger handling 5,000 TPS'],
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
        githubUrl: 'https://github.com/elena',
        portfolioUrl: null
      },
      tailoredSummary: 'Senior Java Engineer with deep distributed settlement architecture expertise.',
      highlightedSkills: ['Java 21', 'Spring Boot', 'PostgreSQL'],
      additionalSkills: ['Docker'],
      matchedKeywords: ['Java 21', 'Spring Boot', 'PostgreSQL', 'Docker'],
      missingKeywords: ['Kafka'],
      tailoringRationale: 'Prioritized SEPA throughput and JVM resilience.',
      optimizedExperiences: [],
      projects: [],
      educations: [],
      languages: ['English (C2)'],
      certifications: [],
      groundingWarnings: []
    }
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(TailoredResumeService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should load filtered and unfiltered tailored resume summaries', () => {
    service.loadTailoredResumes(101).subscribe(list => {
      expect(list.length).toBe(1);
    });
    const filteredReq = httpMock.expectOne('/api/v1/tailored-documents/resumes?jobId=101');
    expect(filteredReq.request.method).toBe('GET');
    filteredReq.flush([summaryV1]);
    expect(service.resumes().length).toBe(1);

    service.loadTailoredResumes(null).subscribe(list => {
      expect(list.length).toBe(1);
    });
    const allReq = httpMock.expectOne('/api/v1/tailored-documents/resumes');
    expect(allReq.request.method).toBe('GET');
    allReq.flush([summaryV1]);
  });

  it('should generate, fetch detail, update draft, and delete tailored resume updating signals', () => {
    const genReqPayload: GenerateTailoredResumeRequest = {
      jobId: 101,
      masterResumeUuid: 'master-uuid-1',
      templateName: 'MODERN',
      tailoringFocus: 'Highlight SEPA Instant throughput',
      forceRefresh: true
    };

    service.generateTailoredResume(genReqPayload).subscribe(res => {
      expect(res.uuid).toBe('tailored-uuid-1');
    });
    const genReq = httpMock.expectOne('/api/v1/tailored-documents/resume/generate');
    expect(genReq.request.method).toBe('POST');
    expect(genReq.request.body).toEqual(genReqPayload);
    genReq.flush(fullDetailV1);

    expect(service.selectedResume()?.uuid).toBe('tailored-uuid-1');
    expect(service.resumes().length).toBe(1);

    service.getTailoredResume('tailored-uuid-1').subscribe(detail => {
      expect(detail.title).toBe(fullDetailV1.title);
    });
    const getReq = httpMock.expectOne('/api/v1/tailored-documents/resume/tailored-uuid-1');
    expect(getReq.request.method).toBe('GET');
    getReq.flush(fullDetailV1);

    const updatePayload: UpdateTailoredResumeRequest = {
      title: 'Custom Adyen Draft',
      templateName: 'CLASSIC',
      tailoredSummary: 'Updated human-reviewed summary.',
      highlightedSkills: ['Java 21', 'Spring Boot']
    };
    service.updateTailoredResume('tailored-uuid-1', updatePayload).subscribe(updated => {
      expect(updated.editedByUser).toBeTrue();
    });
    const putReq = httpMock.expectOne('/api/v1/tailored-documents/resume/tailored-uuid-1');
    expect(putReq.request.method).toBe('PUT');
    putReq.flush({
      ...fullDetailV1,
      title: 'Custom Adyen Draft',
      templateName: 'CLASSIC',
      editedByUser: true,
      atsKeywordCoverage: 90.0
    });

    expect(service.selectedResume()?.editedByUser).toBeTrue();
    expect(service.resumes()[0].title).toBe('Custom Adyen Draft');
    expect(service.resumes()[0].atsKeywordCoverage).toBe(90.0);

    service.deleteTailoredResume('tailored-uuid-1').subscribe(res => {
      expect(res.success).toBeTrue();
    });
    const delReq = httpMock.expectOne('/api/v1/tailored-documents/resume/tailored-uuid-1');
    expect(delReq.request.method).toBe('DELETE');
    delReq.flush({ success: true, timestamp: '2026-11-09T09:10:00Z' });

    expect(service.resumes().length).toBe(0);
    expect(service.selectedResume()).toBeNull();
  });

  it('should export tailored PDF and DOCX blobs, extract filenames, and update cached export flags', () => {
    service.getTailoredResume('tailored-uuid-1').subscribe();
    httpMock.expectOne('/api/v1/tailored-documents/resume/tailored-uuid-1').flush(fullDetailV1);

    expect(service.selectedResume()?.hasPdfExport).toBeFalse();
    expect(service.selectedResume()?.hasDocxExport).toBeFalse();

    const pdfBlob = new Blob(['%PDF-1.4 mock'], { type: 'application/pdf' });
    let capturedFileName = '';
    service.exportTailoredResume('tailored-uuid-1', 'pdf').subscribe(file => {
      capturedFileName = file.fileName;
      expect(file.format).toBe('pdf');
      expect(file.blob.size).toBe(pdfBlob.size);
    });

    expect(service.exportingFormat()).toBe('pdf');
    expect(service.isExporting()).toBeTrue();

    const pdfReq = httpMock.expectOne('/api/v1/tailored-documents/resume/tailored-uuid-1/export?format=pdf');
    expect(pdfReq.request.method).toBe('GET');
    expect(pdfReq.request.responseType).toBe('blob');
    pdfReq.flush(pdfBlob, {
      headers: new HttpHeaders({
        'content-disposition': 'attachment; filename="elena-weber-modern-cv.pdf"'
      })
    });

    expect(capturedFileName).toBe('elena-weber-modern-cv.pdf');
    expect(service.isExporting()).toBeFalse();
    expect(service.selectedResume()?.hasPdfExport).toBeTrue();
    expect(service.resumes()[0].hasPdfExport).toBeTrue();

    // Template override export with regenerate=true should NOT mark default template DOCX as cached
    const docxBlob = new Blob(['PK\u0003\u0004mock'], {
      type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
    });
    service.exportTailoredResume('tailored-uuid-1', 'docx', 'EUROPASS', true).subscribe(file => {
      expect(file.fileName).toBe('elena-weber-europass-cv.docx');
      expect(file.format).toBe('docx');
    });

    const docxReq = httpMock.expectOne(
      '/api/v1/tailored-documents/resume/tailored-uuid-1/export?format=docx&template=EUROPASS&regenerate=true'
    );
    expect(docxReq.request.method).toBe('GET');
    docxReq.flush(docxBlob, {
      headers: new HttpHeaders({
        'content-disposition': 'attachment; filename="elena-weber-europass-cv.docx"'
      })
    });

    expect(service.selectedResume()?.hasDocxExport).toBeFalse();
  });
});

