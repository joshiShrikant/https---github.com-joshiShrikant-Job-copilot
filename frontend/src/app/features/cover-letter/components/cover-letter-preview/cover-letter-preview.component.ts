import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import {
  CoverLetterMetadataDto,
  CoverLetterTone,
  formatCoverLetterToneLabel
} from '../../../../core/models/cover-letter.models';

@Component({
  selector: 'app-cover-letter-preview',
  standalone: true,
  templateUrl: './cover-letter-preview.component.html',
  styleUrl: './cover-letter-preview.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CoverLetterPreviewComponent {
  readonly tone = input<CoverLetterTone>('PROFESSIONAL');
  readonly content = input<string>('');
  readonly jobTitle = input<string>('');
  readonly companyName = input<string>('');
  readonly jobCountry = input<string | null>(null);
  readonly letterDate = input<string | null>(null);
  readonly metadata = input<CoverLetterMetadataDto | null>(null);

  readonly toneLabel = computed<string>(() => formatCoverLetterToneLabel(this.tone()));

  readonly formattedDate = computed<string>(() => {
    const raw = this.letterDate();
    const parsed = raw ? new Date(raw) : new Date();
    if (Number.isNaN(parsed.getTime())) {
      return new Intl.DateTimeFormat('en-US', {
        month: 'long',
        day: 'numeric',
        year: 'numeric'
      }).format(new Date());
    }
    return new Intl.DateTimeFormat('en-US', {
      month: 'long',
      day: 'numeric',
      year: 'numeric'
    }).format(parsed);
  });

  readonly contactSegments = computed<readonly string[]>(() => {
    const meta = this.metadata();
    if (!meta) {
      return [];
    }
    const items: string[] = [];
    if (meta.candidateLocation?.trim()) {
      items.push(meta.candidateLocation.trim());
    }
    if (meta.candidateEmail?.trim()) {
      items.push(meta.candidateEmail.trim());
    }
    if (meta.workAuthorizationSummary?.trim()) {
      items.push(meta.workAuthorizationSummary.trim());
    }
    return items;
  });

  readonly paragraphs = computed<readonly string[]>(() => {
    const normalized = this.content().replace(/\r\n/g, '\n').trim();
    if (!normalized) {
      return [];
    }
    return normalized
      .split(/\n\s*\n+/)
      .map(block => block.trim())
      .filter(block => block.length > 0);
  });
}

