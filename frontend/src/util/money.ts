const KRONOR = new Intl.NumberFormat('sv-SE', { style: 'currency', currency: 'SEK' });
const WHOLE_KRONOR = new Intl.NumberFormat('sv-SE', { style: 'currency', currency: 'SEK', maximumFractionDigits: 0 });

/** Öre, as the backend counts money, as Swedish kronor: 8000 → "80,00 kr". */
export function formatKronor(ore: number): string {
  return KRONOR.format(ore / 100);
}

/**
 * A person's total, which the backend has rounded to whole kronor, without öre: 34200 → "342 kr". Shown so that it
 * reads as rounded, where "342,00 kr" would claim a precision it does not have.
 */
export function formatWholeKronor(ore: number): string {
  return WHOLE_KRONOR.format(ore / 100);
}
