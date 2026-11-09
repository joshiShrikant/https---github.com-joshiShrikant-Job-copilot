import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { JobAssistantMessageResponse } from '../../../../core/models/job-assistant.models';
import { JobAssistantPanelComponent } from './job-assistant-panel.component';

describe('JobAssistantPanelComponent', () => {
  let fixture: ComponentFixture<JobAssistantPanelComponent>;
  let httpMock: HttpTestingController;

  const sampleTurn: JobAssistantMessageResponse = {
    id: 21,
    uuid: 'assist-uuid-21',
    jobId: 101,
    actionType: 'PREPARE_RECRUITER_MESSAGE',
    actionLabel: 'Prepare Recruiter Message',
    userPrompt: null,
    assistantTitle: 'Recruiter Outreach Draft for Adyen N.V.',
    assistantContent: 'Hi Sophie, excited about Senior Java Clearing role.',
    editableContent: 'Hi Sophie, excited about Senior Java Clearing role.',
    highlights: ['6 yrs Java', 'IND Sponsor'],
    editedByUser: false,
    aiGenerated: true,
    aiProvider: 'GEMINI',
    aiModelUsed: 'gemini-2.5-flash',
    createdAt: '2026-11-09T10:00:00Z',
    updatedAt: '2026-11-09T10:00:00Z'
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [JobAssistantPanelComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]
    }).compileComponents();

    fixture = TestBed.createComponent(JobAssistantPanelComponent);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should load history, invoke quick & custom assistant turns, edit draft, and delete turn', () => {
    fixture.componentRef.setInput('jobId', 101);
    fixture.componentRef.setInput('jobTitle', 'Senior Java Clearing Engineer');
    fixture.componentRef.setInput('companyName', 'Adyen N.V.');
    fixture.detectChanges();

    const histReq = httpMock.expectOne('/api/v1/jobs/101/assistant/history');
    histReq.flush([]);
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelectorAll('[data-assistant-action]').length).toBe(10);

    // Invoke quick action
    host.querySelector<HTMLButtonElement>('[data-assistant-action="PREPARE_RECRUITER_MESSAGE"]')?.click();
    const postReq = httpMock.expectOne('/api/v1/jobs/101/assistant/respond');
    expect(postReq.request.body).toEqual({
      actionType: 'PREPARE_RECRUITER_MESSAGE',
      additionalContext: undefined
    });
    postReq.flush(sampleTurn);
    fixture.detectChanges();

    expect(host.querySelector('[data-assistant-turn="21"]')?.textContent).toContain(
      'Recruiter Outreach Draft for Adyen N.V.'
    );

    // Edit draft & persist via PUT
    host.querySelector<HTMLButtonElement>('[data-assistant-edit="21"]')?.click();
    fixture.detectChanges();
    const textarea = host.querySelector<HTMLTextAreaElement>('[data-assistant-editor="21"]')!;
    textarea.value = 'Hi Sophie, edited personalized outreach note.';
    textarea.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    host.querySelector<HTMLButtonElement>('[data-assistant-save-edit="21"]')?.click();
    const putReq = httpMock.expectOne('/api/v1/jobs/101/assistant/21');
    expect(putReq.request.method).toBe('PUT');
    putReq.flush({
      ...sampleTurn,
      editableContent: 'Hi Sophie, edited personalized outreach note.',
      editedByUser: true
    });
    fixture.detectChanges();

    expect(host.querySelector('[data-assistant-edited]')).toBeTruthy();
    expect(host.querySelector('[data-assistant-content="21"]')?.textContent).toContain(
      'edited personalized outreach note'
    );

    // Delete turn
    host.querySelector<HTMLButtonElement>('[data-assistant-delete="21"]')?.click();
    const delReq = httpMock.expectOne('/api/v1/jobs/101/assistant/21');
    expect(delReq.request.method).toBe('DELETE');
    delReq.flush({ success: true, message: 'Deleted' });
    fixture.detectChanges();

    expect(host.querySelector('[data-assistant-turn="21"]')).toBeNull();
  });
});

