import { useEffect, useRef } from 'react';

type Props = {
  names: string[],
  /** The current or last time's choice, marked among the buttons. */
  suggested: string | null,
  onChoose: (name: string) => void
}

/**
 * "Vem är du?" — one button per person, and no way out but choosing: a reader is one of the people at the table.
 * Not a login, nothing is checked. Rendered only while a choice is needed; choosing unmounts it, which closes it.
 */
export function ChoosePersonDialog({ names, suggested, onChoose }: Props) {
  const dialogRef = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const dialog = dialogRef.current;
    // Guarded: React's development double run would otherwise open an open dialog, which throws.
    if (dialog && !dialog.open) {
      dialog.showModal();
    }
  }, []);

  return (
    // m-auto, because Tailwind's reset removes the margin that centres a dialog by default.
    <dialog
      ref={dialogRef}
      className="m-auto w-[min(90vw,22rem)] rounded-lg bg-brand p-0 text-white shadow-lg backdrop:bg-black/40"
      onCancel={(event) => event.preventDefault()}
    >
      {/* The header's green (2026-10-30: in white it looked colourless), the names on white so they read as buttons. */}
      <h2 className="px-4 pt-4 text-lg font-medium">Vem är du?</h2>
      <p className="px-4 pb-3 text-sm text-white/80">Ditt namn visas i fetstil. Tryck på det för att byta.</p>
      <div className="grid grid-cols-2 gap-2 px-4 pb-4">
        {names.map((name) => (
          <button
            key={name}
            className={`truncate rounded-md bg-white px-3 py-3 text-gray-800 cursor-pointer hover:bg-gray-100 ${name === suggested ? 'font-medium ring-2 ring-white ring-offset-2 ring-offset-brand' : ''}`}
            onClick={() => onChoose(name)}
          >
            {name}
          </button>
        ))}
      </div>
    </dialog>
  );
}
