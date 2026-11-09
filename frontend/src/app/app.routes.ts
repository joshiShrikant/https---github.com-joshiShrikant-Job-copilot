import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/guards/auth.guard';

export const AUTH_ROUTES: Routes = [
  {
    path: '',
    redirectTo: 'login',
    pathMatch: 'full'
  },
  {
    path: 'login',
    loadComponent: () =>
      import('./features/auth/login/login.component').then(m => m.LoginComponent),
    title: 'Sign in – EuroJob Copilot'
  },
  {
    path: 'register',
    loadComponent: () =>
      import('./features/auth/register/register.component').then(m => m.RegisterComponent),
    title: 'Create Account – EuroJob Copilot'
  },
  {
    path: 'forgot-password',
    loadComponent: () =>
      import('./features/auth/forgot-password/forgot-password.component').then(
        m => m.ForgotPasswordComponent
      ),
    title: 'Forgot Password – EuroJob Copilot'
  },
  {
    path: 'reset-password',
    loadComponent: () =>
      import('./features/auth/reset-password/reset-password.component').then(
        m => m.ResetPasswordComponent
      ),
    title: 'Reset Password – EuroJob Copilot'
  }
];

export const WORKSPACE_ROUTES: Routes = [
  {
    path: '',
    redirectTo: 'dashboard',
    pathMatch: 'full'
  },
  {
    path: 'dashboard',
    loadComponent: () =>
      import('./features/dashboard/dashboard.component').then(m => m.DashboardComponent),
    title: 'Dashboard – EuroJob Copilot',
    data: {
      breadcrumb: 'Dashboard',
      section: 'Workspace'
    }
  },
  {
    path: 'jobs',
    loadComponent: () => import('./features/jobs/jobs.component').then(m => m.JobsComponent),
    title: 'Discover Jobs – EuroJob Copilot',
    data: {
      breadcrumb: 'Discover Jobs',
      section: 'Job Discovery'
    }
  },
  {
    path: 'recommended-jobs',
    loadComponent: () =>
      import('./features/recommended-jobs/recommended-jobs.component').then(
        m => m.RecommendedJobsComponent
      ),
    title: 'Recommended Jobs – EuroJob Copilot',
    data: {
      breadcrumb: 'Recommended Jobs',
      section: 'Job Discovery'
    }
  },
  {
    path: 'sponsorship',
    loadComponent: () =>
      import('./features/sponsorship/sponsorship.component').then(m => m.SponsorshipComponent),
    title: 'Visa Sponsorship – EuroJob Copilot',
    data: {
      breadcrumb: 'Visa Sponsorship',
      section: 'Job Discovery'
    }
  },
  {
    path: 'companies',
    loadComponent: () =>
      import('./features/companies/companies.component').then(m => m.CompaniesComponent),
    title: 'Companies – EuroJob Copilot',
    data: {
      breadcrumb: 'Companies',
      section: 'Job Discovery'
    }
  },
  {
    path: 'resume-builder',
    loadComponent: () =>
      import('./features/resume-builder/resume-builder.component').then(
        m => m.ResumeBuilderComponent
      ),
    title: 'AI Resume Builder – EuroJob Copilot',
    data: {
      breadcrumb: 'AI Resume Builder',
      section: 'AI & Documents'
    }
  },
  {
    path: 'cover-letter',
    loadComponent: () =>
      import('./features/cover-letter/cover-letter.component').then(
        m => m.CoverLetterComponent
      ),
    title: 'Cover Letter Builder – EuroJob Copilot',
    data: {
      breadcrumb: 'Cover Letter Builder',
      section: 'AI & Documents'
    }
  },
  {
    path: 'resumes',
    loadComponent: () =>
      import('./features/resumes/resumes.component').then(m => m.ResumesComponent),
    title: 'My Resumes – EuroJob Copilot',
    data: {
      breadcrumb: 'My Resumes',
      section: 'AI & Documents'
    }
  },
  {
    path: 'applications',
    loadComponent: () =>
      import('./features/applications/applications.component').then(
        m => m.ApplicationsComponent
      ),
    title: 'My Applications – EuroJob Copilot',
    data: {
      breadcrumb: 'My Applications',
      section: 'Pipeline & Insights'
    }
  },
  {
    path: 'analytics',
    loadComponent: () =>
      import('./features/analytics/analytics.component').then(m => m.AnalyticsComponent),
    title: 'Analytics – EuroJob Copilot',
    data: {
      breadcrumb: 'Analytics',
      section: 'Pipeline & Insights'
    }
  },
  {
    path: 'interview-prep',
    loadComponent: () =>
      import('./features/interview-prep/interview-prep.component').then(
        m => m.InterviewPrepComponent
      ),
    title: 'Interview Preparation – EuroJob Copilot',
    data: {
      breadcrumb: 'Interview Preparation',
      section: 'Pipeline & Insights'
    }
  },
  {
    path: 'notifications',
    loadComponent: () =>
      import('./features/notifications/notifications.component').then(
        m => m.NotificationsComponent
      ),
    title: 'Notifications – EuroJob Copilot',
    data: {
      breadcrumb: 'Notifications',
      section: 'Account'
    }
  },
  {
    path: 'profile',
    loadComponent: () =>
      import('./features/profile/profile.component').then(m => m.ProfileComponent),
    title: 'My Profile – EuroJob Copilot',
    data: {
      breadcrumb: 'My Profile',
      section: 'Account'
    }
  },
  {
    path: 'settings',
    loadComponent: () =>
      import('./features/settings/settings.component').then(m => m.SettingsComponent),
    title: 'Settings – EuroJob Copilot',
    data: {
      breadcrumb: 'Settings',
      section: 'Account'
    }
  }
];

export const routes: Routes = [
  {
    path: 'auth',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('./core/layout/auth-layout/auth-layout.component').then(m => m.AuthLayoutComponent),
    children: AUTH_ROUTES
  },
  {
    path: 'login',
    redirectTo: 'auth/login',
    pathMatch: 'full'
  },
  {
    path: 'register',
    redirectTo: 'auth/register',
    pathMatch: 'full'
  },
  {
    path: 'forgot-password',
    redirectTo: 'auth/forgot-password',
    pathMatch: 'full'
  },
  {
    path: 'reset-password',
    redirectTo: 'auth/reset-password',
    pathMatch: 'full'
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./core/layout/shell.component').then(m => m.ShellComponent),
    children: WORKSPACE_ROUTES
  },
  {
    path: '**',
    redirectTo: 'dashboard'
  }
];
