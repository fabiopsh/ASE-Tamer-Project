export type NavIconName =
  | 'home'
  | 'catalog'
  | 'collection'
  | 'teams'
  | 'encounters'
  | 'battle'
  | 'runs'
  | 'history'
  | 'logout';

const PATHS: Record<NavIconName, string> = {
  home: 'M4 11.5 12 5l8 6.5V20a1 1 0 0 1-1 1h-4.5v-6h-5v6H5a1 1 0 0 1-1-1Z',
  catalog: 'M5 4.5h10.5A3.5 3.5 0 0 1 19 8v11.5H8.5A3.5 3.5 0 0 1 5 16ZM5 16a3.5 3.5 0 0 1 3.5-3.5H19M9 8h6',
  collection: 'M12 3.5 20 8v8l-8 4.5L4 16V8Zm0 0v17M4 8l8 4.5L20 8',
  teams: 'M8.5 11a3 3 0 1 0 0-6 3 3 0 0 0 0 6Zm7.5 0a2.5 2.5 0 1 0 0-5M3 19.5c.6-3.3 2.8-5 5.5-5s4.9 1.7 5.5 5M15.5 14.5c2.6 0 4.6 1.5 5.2 5',
  encounters: 'M12 3a7 7 0 0 1 7 7c0 5-7 11-7 11S5 15 5 10a7 7 0 0 1 7-7Zm0 4.5a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5Z',
  battle: 'M5 19 15.5 8.5M14 4h6v6M4.5 14.5l5 5M19 19 8.5 8.5M10 4H4v6M14.5 14.5l5 5',
  runs: 'M5 21V4M5 4.5h12.5l-2.5 4 2.5 4H5',
  history: 'M4 12a8 8 0 1 0 2.4-5.7L4 8.5M4 4v4.5h4.5M12 8v4.5l3 2',
  logout: 'M14.5 4H18a1.5 1.5 0 0 1 1.5 1.5v13A1.5 1.5 0 0 1 18 20h-3.5M10 16.5 5.5 12 10 7.5M5.5 12H15',
};

export function NavIcon({ name, size = 20 }: { name: NavIconName; size?: number }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.9"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d={PATHS[name]} />
    </svg>
  );
}
