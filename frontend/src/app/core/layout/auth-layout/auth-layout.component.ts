import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { ThemeService } from '../../services/theme.service';

@Component({
  selector: 'app-auth-layout',
  standalone: true,
  imports: [RouterOutlet, RouterLink],
  templateUrl: './auth-layout.component.html',
  styleUrl: './auth-layout.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AuthLayoutComponent {
  readonly theme = inject(ThemeService);
}

