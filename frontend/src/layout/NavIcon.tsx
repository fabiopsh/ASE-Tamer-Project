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
  home: 'M4 10h4V6h8v4h4v10h-6v-6h-4v6H4Zm6-6h4',
  catalog: 'M5 4h14v16H5Zm0 12h14M9 8h6M9 11h4',
  collection: 'M4 4h16v16H4Zm0 8h16M0 14h4m4-2h8M8 18h8',
  teams: 'M3 8h6v6H3Zm12 0h6v6h-6ZM9 3h6v5H9ZM6 14v5h12v-5M12 8v11',
  encounters: 'M8 3h8v3h3v10h-3v3h-3v3h-2v-3H8v-3H5V6h3Zm2 5h4v4h-4Z',
  battle: 'M4 4h4v4h4v4h4v4h4v4h-4v-4h-4v-4H8V8H4Zm16 0h-4v4h-4m-4 4v4H4v4',
  runs: 'M4 20v-4h4v-4h4V8h4V4h4M4 4v6M1 7h6',
  history: 'M8 4h8v3h4v10h-4v3H8v-3H4v-7M1 4v6h7M12 8v5h4',
  logout: 'M14 4h6v16h-6M10 8H6v4H2l4 4h4M6 12h10',
};

export function NavIcon({ name, size = 20 }: { name: NavIconName; size?: number }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="square"
      strokeLinejoin="miter"
      aria-hidden="true"
    >
      <path d={PATHS[name]} />
    </svg>
  );
}
