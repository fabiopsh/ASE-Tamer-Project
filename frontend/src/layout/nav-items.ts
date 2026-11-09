import type { NavIconName } from './NavIcon';

export interface NavItem {
  to: string;
  label: string;
  icon: NavIconName;
  end?: boolean;
}

/** Every top-level route of docs/plan/09-frontend.md. */
export const NAV_ITEMS: NavItem[] = [
  { to: '/', label: 'Home', icon: 'home', end: true },
  { to: '/encounters', label: 'Encounters', icon: 'encounters' },
  { to: '/battle', label: 'Battle', icon: 'battle' },
  { to: '/teams', label: 'Teams', icon: 'teams' },
  { to: '/collection', label: 'Collection', icon: 'collection' },
  { to: '/catalog', label: 'Catalogue', icon: 'catalog' },
  { to: '/runs', label: 'Runs', icon: 'runs' },
  { to: '/history', label: 'History', icon: 'history' },
];
