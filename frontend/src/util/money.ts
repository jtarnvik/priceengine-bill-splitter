const KRONOR = new Intl.NumberFormat('sv-SE', { style: 'currency', currency: 'SEK' });

/** Öre, as the backend counts money, as Swedish kronor: 8000 → "80,00 kr". */
export function formatKronor(ore: number): string {
  return KRONOR.format(ore / 100);
}
