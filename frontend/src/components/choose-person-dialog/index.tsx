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
      className="m-auto w-[min(90vw,22rem)] rounded-lg border border-gray-200 bg-white p-0 shadow-lg backdrop:bg-black/40"
      onCancel={(event) => event.preventDefault()}
    >
      <h2 className="px-4 pt-4 font-medium text-gray-800">Vem är du?</h2>
      <p className="px-4 pb-3 text-sm text-gray-500">Ditt namn visas i fetstil. Tryck på det för att byta.</p>
      <div className="grid grid-cols-2 gap-2 px-4 pb-4">
        {names.map((name) => (
          <button
            key={name}
            className={`truncate rounded-md border px-3 py-3 text-gray-800 cursor-pointer hover:bg-gray-100 ${name === suggested ? 'border-brand font-medium' : 'border-gray-300'}`}
            onClick={() => onChoose(name)}
          >
            {name}
          </button>
        ))}
      </div>
    </dialog>
  );
}
