import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ResumesComponent } from './resumes.component';

describe('ResumesComponent', () => {
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ResumesComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should load resumes on init and render error banner with Retry on failure', () => {
    const fixture = TestBed.createComponent(ResumesComponent);
    fixture.detectChanges();

    const firstReq = httpMock.expectOne('/api/v1/resumes');
    expect(firstReq.request.method).toBe('GET');
    firstReq.flush({ message: 'Storage unavailable' }, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('.status-banner.error')?.textContent).toContain('Storage unavailable');

    const retryBtn = Array.from(host.querySelectorAll<HTMLButtonElement>('.status-banner.error button')).find(
      b => b.textContent?.includes('Retry')
    );
    retryBtn?.click();

    const retryReq = httpMock.expectOne('/api/v1/resumes');
    retryReq.flush([
      {
        uuid: 'res-uuid-standalone',
        title: 'EU Backend Master CV',
        fileName: 'eu-backend.pdf',
        fileSizeBytes: 51200,
        mimeType: 'application/pdf',
        resumeType: 'MASTER',
        storageProvider: 'LOCAL',
        versionNumber: 1,
        isMasterApproved: true,
        createdAt: '2026-03-02T10:00:00Z',
        updatedAt: '2026-03-02T10:00:00Z'
      }
    ]);
    fixture.detectChanges();

    expect(host.querySelector('.status-banner.error')).toBeNull();
    expect(host.querySelector('app-resumes-section')?.textContent).toContain('EU Backend Master CV');
  });
});

