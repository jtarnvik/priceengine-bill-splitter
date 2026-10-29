/** A small turning ring in the app's green, for waits whose length nobody knows. */
export function Spinner() {
  return (
    <div
      className="h-6 w-6 shrink-0 animate-spin rounded-full border-[3px] border-gray-300 border-t-brand"
      role="status"
      aria-label="Väntar"
    />
  );
}
