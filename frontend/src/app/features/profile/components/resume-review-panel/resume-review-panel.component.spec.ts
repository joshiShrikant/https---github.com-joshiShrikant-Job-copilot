import { TestBed } from '@angular/core/testing';
import { ApproveMasterResumeRequest, ResumeParseResponse, ResumeResponse } from '../../../../core/models/resume.models';
import { ResumeReviewPanelComponent } from './resume-review-panel.component';

describe('ResumeReviewPanelComponent', () => {
  const sampleResume: ResumeResponse = {
    uuid: 'res-review-1',
    title: 'Senior Java Architect CV',
    fileName: 'elena-cv.pdf',
    fileSizeBytes: 30000,
    mimeType: 'application/pdf',
    resumeType: 'MASTER',
    storageProvider: 'LOCAL',
    versionNumber: 1,
    isMasterApproved: false,
    parseStatus: 'PARSED',
    createdAt: '2026-03-01T10:00:00Z',
    updatedAt: '2026-03-01T10:00:00Z'
  };

  const sampleParseResult: ResumeParseResponse = {
    resumeUuid: 'res-review-1',
    title: 'Senior Java Architect CV',
    fileName: 'elena-cv.pdf',
    mimeType: 'application/pdf',
    versionNumber: 1,
    isMasterApproved: false,
    parseStatus: 'PARSED',
    parsedAt: '2026-03-01T10:05:00Z',
    approvedAt: null,
    aiProvider: 'GEMINI',
    aiModelUsed: 'gemini-2.5-flash',
    extractedCharCount: 3100,
    parsedData: {
      profile: {
        fullName: 'Elena Weber',
        currentDesignation: 'Senior Java Engineer',
        yearsOfExperience: 7,
        currentCountry: 'Germany',
        currentCity: 'Munich',
        linkedinUrl: 'https://linkedin.com/in/elena-weber',
        githubUrl: null,
        portfolioUrl: null,
        bio: 'Distributed Java systems architect.',
        workAuthorizationStatus: 'EU_CITIZEN',
        relocationPreference: 'ALREADY_IN_EUROPE'
      },
      summary: 'Distributed Java systems architect.',
      skills: [
        { skillName: 'Java 21', yearsExperience: 7, proficiencyLevel: 'EXPERT', primary: true }
      ],
      experiences: [
        {
          companyName: 'Adyen N.V.',
          designation: 'Senior Java Engineer',
          location: 'Amsterdam',
          startDate: '2022-03-01',
          endDate: null,
          current: true,
          description: 'Built SEPA Instant rails.',
          skillsUsed: ['Java 21', 'Kafka']
        }
      ],
      educations: [
        {
          institution: 'TU Munich',
          degree: 'M.Sc.',
          fieldOfStudy: 'Computer Science',
          startDate: '2016-10-01',
          endDate: '2018-09-30',
          gradeOrGpa: '1.3',
          country: null
        }
      ],
      projects: [],
      languages: ['English (C2)', 'German (B2)'],
      certifications: []
    },
    warnings: ['Missing country for education "M.Sc. at TU Munich"; specify country before approving.']
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ResumeReviewPanelComponent]
    }).compileComponents();
  });

  it('should render AI warnings, block approval when education country is missing, and emit edited payload once completed', () => {
    const fixture = TestBed.createComponent(ResumeReviewPanelComponent);
    fixture.componentRef.setInput('resume', sampleResume);
    fixture.componentRef.setInput('parseResult', sampleParseResult);
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    const comp = fixture.componentInstance;

    expect(host.querySelector('.status-banner.warning')?.textContent).toContain('Missing country for education');

    const emitted: ApproveMasterResumeRequest[] = [];
    comp.approveRequested.subscribe(req => emitted.push(req));

    // Attempt approve while education country is null -> blocked client-side
    host.querySelector<HTMLButtonElement>('.approve-submit-btn')?.click();
    fixture.detectChanges();

    expect(emitted.length).toBe(0);
    expect(host.querySelector('.status-banner.error')?.textContent).toContain('Country is required for education');

    // Fill missing country & reject unsafe javascript: URL before accepting valid URLs
    comp.updateEducationText(0, 'country', { target: { value: 'Germany' } } as unknown as Event);
    comp.updateProfileText('portfolioUrl', { target: { value: 'javascript:alert(1)' } } as unknown as Event);
    host.querySelector<HTMLButtonElement>('.approve-submit-btn')?.click();
    fixture.detectChanges();

    expect(emitted.length).toBe(0);
    expect(host.querySelector('.status-banner.error')?.textContent).toContain('Portfolio URL');

    comp.updateProfileText('portfolioUrl', { target: { value: 'https://elenaweber.dev' } } as unknown as Event);
    comp.updateEducationText(0, 'gradeOrGpa', { target: { value: '1.2' } } as unknown as Event);
    comp.addProject();
    comp.updateProjectText(0, 'name', { target: { value: 'SEPA Clearing Engine' } } as unknown as Event);
    comp.updateProjectText(0, 'url', { target: { value: 'https://github.com/elena-weber/sepa' } } as unknown as Event);
    comp.updateProfileText('currentDesignation', { target: { value: 'Principal Java Architect' } } as unknown as Event);
    comp.newSkillName.set('Kubernetes');
    comp.addSkillFromInput();
    comp.syncMode.set('REPLACE');
    fixture.detectChanges();

    host.querySelector<HTMLButtonElement>('.approve-submit-btn')?.click();
    fixture.detectChanges();

    expect(emitted.length).toBe(1);
    expect(emitted[0].syncMode).toBe('REPLACE');
    expect(emitted[0].approvedParsedData.profile?.currentDesignation).toBe('Principal Java Architect');
    expect(emitted[0].approvedParsedData.profile?.portfolioUrl).toBe('https://elenaweber.dev');
    expect(emitted[0].approvedParsedData.educations[0].country).toBe('Germany');
    expect(emitted[0].approvedParsedData.educations[0].gradeOrGpa).toBe('1.2');
    expect(emitted[0].approvedParsedData.projects[0].url).toBe('https://github.com/elena-weber/sepa');
    expect(emitted[0].approvedParsedData.skills.map(s => s.skillName)).toEqual(['Java 21', 'Kubernetes']);
  });

  it('should allow manual entry mode when resume is NOT_PARSED', () => {
    const unparsedResume: ResumeResponse = {
      ...sampleResume,
      parseStatus: 'NOT_PARSED'
    };
    const emptyParseResult: ResumeParseResponse = {
      ...sampleParseResult,
      parseStatus: 'NOT_PARSED',
      aiProvider: null,
      aiModelUsed: null,
      extractedCharCount: null,
      parsedData: null,
      warnings: []
    };

    const fixture = TestBed.createComponent(ResumeReviewPanelComponent);
    fixture.componentRef.setInput('resume', unparsedResume);
    fixture.componentRef.setInput('parseResult', emptyParseResult);
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    const comp = fixture.componentInstance;

    expect(host.querySelector('.empty-state')?.textContent).toContain('No parsed CV draft saved yet');

    const fillBtn = Array.from(host.querySelectorAll<HTMLButtonElement>('.empty-actions button')).find(
      b => b.textContent?.includes('Fill Manually')
    );
    fillBtn?.click();
    fixture.detectChanges();

    expect(comp.hasEditableDraft()).toBeTrue();
    expect(host.querySelector('#rev-designation')).toBeTruthy();
  });
});
