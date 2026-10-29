import { ChangeEvent } from 'react';
import { FaCamera } from 'react-icons/fa';

type Props = {
  busy: boolean,
  onFileChosen: (file: File) => void
}

/**
 * One large, rounded button in the iOS manner for both camera and photo library. A plain file input without
 * `capture`: on iOS it opens the system menu offering "Ta bild" and "Fotobibliotek", where `capture` would allow
 * the camera only. The input is hidden inside the label, so the whole button is its tap target.
 */
export function UploadButton({ busy, onFileChosen }: Props) {
  function handleChange(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    // Cleared, so that choosing the same photo again, after a failure, still counts as a change.
    event.target.value = '';
    if (file) {
      onFileChosen(file);
    }
  }

  const stateClass = busy
    ? 'bg-brand/60 cursor-default'
    : 'bg-brand hover:bg-brand-hover active:bg-brand-dark cursor-pointer';
  return (
    <label className={`flex items-center justify-center gap-3 w-full my-3 py-4 rounded-2xl text-white text-lg font-medium select-none has-focus-visible:ring-2 has-focus-visible:ring-brand-dark has-focus-visible:ring-offset-2 ${stateClass}`}>
      <FaCamera className="h-6 w-6" aria-hidden />
      {busy ? 'Läser kvittot…' : 'Ladda upp kvitto'}
      <input type="file" accept="image/*" className="sr-only" disabled={busy} onChange={handleChange} />
    </label>
  );
}
