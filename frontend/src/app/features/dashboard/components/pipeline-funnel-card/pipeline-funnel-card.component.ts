import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { PipelineStageCount } from '../../../../core/models/dashboard.models';

const ACTIVE_PIPELINE_ORDER: readonly string[] = [
  'INTERESTED',
  'SAVED',
  'APPLIED',
  'ASSESSMENT',
  'TECH_INTERVIEW',
  'HR_INTERVIEW',
  'FINAL_INTERVIEW',
  'OFFER'
];

@Component({
  selector: 'app-pipeline-funnel-card',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './pipeline-funnel-card.component.html',
  styleUrl: './pipeline-funnel-card.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PipelineFunnelCardComponent {
  readonly stages = input.required<readonly PipelineStageCount[]>();
  readonly activeApplicationsCount = input.required<number>();

  readonly activeStages = computed(() =>
    this.stages().filter(stage => ACTIVE_PIPELINE_ORDER.includes(stage.status))
  );

  readonly closedStages = computed(() =>
    this.stages().filter(stage => !ACTIVE_PIPELINE_ORDER.includes(stage.status) && stage.count > 0)
  );

  readonly totalApplicationsCount = computed(() =>
    this.stages().reduce((sum, item) => sum + item.count, 0)
  );

  readonly maxStageCount = computed(() => {
    const maxVal = Math.max(...this.activeStages().map(s => s.count), 0);
    return maxVal > 0 ? maxVal : 1;
  });

  barWidthPercent(count: number): number {
    if (count <= 0) {
      return 0;
    }
    return Math.max(8, Math.round((count / this.maxStageCount()) * 100));
  }
}

