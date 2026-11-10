/** Pixel-art wordmark; geometry and colors only (no navigation or app behavior). */
export function Logo({ compact = false, size = 34 }: { compact?: boolean; size?: number }) {
  return (
    <span className="logo">
      <svg className="logo__mark" width={size} height={size} viewBox="0 0 64 64" shapeRendering="crispEdges" aria-hidden="true">
        <path d="M20 4H44V8H52V16H60V48H52V56H44V60H20V56H12V48H4V16H12V8H20Z" fill="#e6cc8b" />
        <path d="M20 8H44V12H52V20H56V44H52V52H44V56H20V52H12V44H8V20H12V12H20Z" fill="#102637" />
        <path d="M20 16H44V20H48V44H44V48H20V44H16V20H20Z" fill="#375668" />
        <path d="M28 16H36V24H44V32H48V40H40V48H24V40H16V32H20V24H28Z" fill="#0b1721" />
        <path d="M28 20H36V28H44V36H40V44H24V36H20V28H28Z" fill="#7ed5bb" />
        <path d="M28 24H36V32H40V36H36V40H28V36H24V32H28Z" fill="#e8edd2" />
        <path d="M28 28H32V32H28ZM36 36H40V40H36Z" fill="#ffffff" />
        <path d="M12 28H16V36H12ZM48 28H52V36H48ZM28 12H36V16H28ZM28 48H36V52H28Z" fill="#e6cc8b" />
      </svg>
      {!compact && (
        <span className="logo__text">
          <span className="logo__word">Monster</span>
          <span className="logo__word logo__word--accent">Tamers</span>
        </span>
      )}
    </span>
  );
}
