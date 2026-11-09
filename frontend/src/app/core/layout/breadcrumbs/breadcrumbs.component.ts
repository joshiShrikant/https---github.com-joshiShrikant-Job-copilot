import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { BreadcrumbItem } from '../navigation.config';

@Component({
  selector: 'app-breadcrumbs',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './breadcrumbs.component.html',
  styleUrl: './breadcrumbs.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class BreadcrumbsComponent {
  readonly items = input.required<readonly BreadcrumbItem[]>();
}

