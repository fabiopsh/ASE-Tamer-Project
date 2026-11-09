import type { ReactNode } from 'react';

export interface ChipOption<T extends string> {
  value: T;
  label: string;
  icon?: ReactNode;
  /** Sets data-element so the selected chip takes that element colour. */
  element?: string;
}

interface FilterChipsProps<T extends string> {
  /** Visible group label (e.g. "Element"). */
  label: string;
  options: ChipOption<T>[];
  /** Selected value; null = "All". */
  value: T | null;
  onChange: (value: T | null) => void;
  /** Label of the reset chip (default "All"); pass null to hide it. */
  allLabel?: string | null;
}

/** Single-choice chip group (toggle buttons with aria-pressed). */
export function FilterChips<T extends string>({ label, options, value, onChange, allLabel = 'All' }: FilterChipsProps<T>) {
  return (
    <div className="filter-group" role="group" aria-label={label}>
      <span className="filter-group__label">{label}</span>
      <div className="cluster">
        {allLabel !== null && (
          <button type="button" className="chip" aria-pressed={value === null} onClick={() => onChange(null)}>
            {allLabel}
          </button>
        )}
        {options.map((o) => (
          <button
            key={o.value}
            type="button"
            className="chip"
            data-element={o.element}
            aria-pressed={value === o.value}
            onClick={() => onChange(value === o.value ? null : o.value)}
          >
            {o.icon}
            {o.label}
          </button>
        ))}
      </div>
    </div>
  );
}
