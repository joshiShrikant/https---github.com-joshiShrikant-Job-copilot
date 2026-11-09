export interface NavItem {
  readonly id: string;
  readonly label: string;
  readonly route: string;
  readonly icon: string;
  readonly section: string;
  readonly description: string;
}

export interface NavGroup {
  readonly id: string;
  readonly label: string;
  readonly items: readonly NavItem[];
}

export interface BreadcrumbItem {
  readonly label: string;
  readonly route?: string;
  readonly isCurrent: boolean;
}

export const NAV_GROUPS: readonly NavGroup[] = [
  {
    id: 'workspace',
    label: 'Workspace',
    items: [
      {
        id: 'dashboard',
        label: 'Dashboard',
        route: '/dashboard',
        icon: 'dashboard',
        section: 'Workspace',
        description: 'Workspace summary, discovery highlights, and recent career activity.'
      }
    ]
  },
  {
    id: 'discovery',
    label: 'Job Discovery',
    items: [
      {
        id: 'jobs',
        label: 'Discover Jobs',
        route: '/jobs',
        icon: 'travel_explore',
        section: 'Job Discovery',
        description: 'Explore European engineering roles with sponsorship filters and skill search.'
      },
      {
        id: 'recommended-jobs',
        label: 'Recommended Jobs',
        route: '/recommended-jobs',
        icon: 'auto_awesome',
        section: 'Job Discovery',
        description: 'Personalized role rankings matched against your profile and visa preferences.'
      },
      {
        id: 'sponsorship',
        label: 'Visa Sponsorship',
        route: '/sponsorship',
        icon: 'verified_user',
        section: 'Job Discovery',
        description: 'Verified sponsor registries, EU Blue Card eligibility, and IND sponsor intelligence.'
      },
      {
        id: 'companies',
        label: 'Companies',
        route: '/companies',
        icon: 'apartment',
        section: 'Job Discovery',
        description: 'Sponsoring European tech employers, relocation profiles, and open roles.'
      }
    ]
  },
  {
    id: 'career-studio',
    label: 'AI & Documents',
    items: [
      {
        id: 'resume-builder',
        label: 'AI Resume Builder',
        route: '/resume-builder',
        icon: 'edit_document',
        section: 'AI & Documents',
        description: 'Tailor resumes with AI keyword optimization grounded in your experience.'
      },
      {
        id: 'cover-letter',
        label: 'Cover Letter Builder',
        route: '/cover-letter',
        icon: 'article',
        section: 'AI & Documents',
        description: 'Draft targeted European cover letters aligned to role and relocation context.'
      },
      {
        id: 'resumes',
        label: 'My Resumes',
        route: '/resumes',
        icon: 'description',
        section: 'AI & Documents',
        description: 'Manage uploaded source CVs, primary resume selection, and tailored versions.'
      }
    ]
  },
  {
    id: 'pipeline',
    label: 'Pipeline & Insights',
    items: [
      {
        id: 'applications',
        label: 'My Applications',
        route: '/applications',
        icon: 'view_kanban',
        section: 'Pipeline & Insights',
        description: 'Track applications across saved, applied, interviewing, and offer stages.'
      },
      {
        id: 'analytics',
        label: 'Analytics',
        route: '/analytics',
        icon: 'insights',
        section: 'Pipeline & Insights',
        description: 'Conversion funnels, sponsorship match trends, and country response insights.'
      },
      {
        id: 'interview-prep',
        label: 'Interview Preparation',
        route: '/interview-prep',
        icon: 'psychology',
        section: 'Pipeline & Insights',
        description: 'Role-specific technical questions, system design notes, and prep checklists.'
      }
    ]
  },
  {
    id: 'account',
    label: 'Account',
    items: [
      {
        id: 'notifications',
        label: 'Notifications',
        route: '/notifications',
        icon: 'notifications',
        section: 'Account',
        description: 'Job match alerts, application reminders, and system activity updates.'
      },
      {
        id: 'profile',
        label: 'My Profile',
        route: '/profile',
        icon: 'account_circle',
        section: 'Account',
        description: 'Manage your career headline, skills, visa status, and relocation target countries.'
      },
      {
        id: 'settings',
        label: 'Settings',
        route: '/settings',
        icon: 'settings',
        section: 'Account',
        description: 'Configure theme preferences, alert cadence, and workspace options.'
      }
    ]
  }
];

export const ALL_NAV_ITEMS: readonly NavItem[] = NAV_GROUPS.flatMap(group => group.items);

export function findNavItemByPath(rawUrl: string): NavItem | undefined {
  const cleanPath = rawUrl.split('?')[0].split('#')[0];
  return ALL_NAV_ITEMS.find(item => cleanPath === item.route || cleanPath.startsWith(`${item.route}/`));
}

