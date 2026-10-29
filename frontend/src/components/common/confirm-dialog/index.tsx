import { useEffect, useRef } from 'react';

type Props = {
  open: boolean,
  message: string,
  confirmLabel: string,
  onConfirm: () => void,
  onCancel: () => void
}

/**
 * A question before something that cannot be undone, in the app's own look. Not `window.confirm`, which puts
 * "pbs.tarnvik.com säger…" above the question. A modal `<dialog>`: the browser keeps focus inside it and puts
 * "Avbryt" (the first button) in focus.
 *
 * Every close is reported as onCancel, Escape included. Escape closes the dialog in the browser itself, and Chrome
 * does not always let a cancel handler prevent that, so `open` would otherwise stay true and the dialog never open
 * again. A close after onConfirm reports it too, which is harmless: `open` is false by then.
 */
export function ConfirmDialog({ open, message, confirmLabel, onConfirm, onCancel }: Props) {
  const dialogRef = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) {
      return;
    }
    if (open && !dialog.open) {
      dialog.showModal();
    } else if (!open && dialog.open) {
      dialog.close();
    }
  }, [open]);

  return (
    // m-auto, because Tailwind's reset removes the margin that centres a dialog by default.
    <dialog
      ref={dialogRef}
      className="m-auto w-[min(90vw,22rem)] rounded-lg border border-gray-200 bg-white p-0 shadow-lg backdrop:bg-black/40"
      onClose={onCancel}
    >
      <p className="px-4 pt-4 pb-3 text-gray-800">{message}</p>
      <div className="flex justify-end gap-2 px-4 pb-4">
        <button className="rounded-md px-4 py-2 text-gray-800 hover:bg-gray-100 cursor-pointer" onClick={onCancel}>
          Avbryt
        </button>
        <button className="rounded-md bg-red-600 px-4 py-2 text-white hover:bg-red-700 cursor-pointer" onClick={onConfirm}>
          {confirmLabel}
        </button>
      </div>
    </dialog>
  );
}
