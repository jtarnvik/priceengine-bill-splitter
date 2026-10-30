import { useEffect, useRef, useState } from 'react';

import { Settings, useGetSettingsQuery, useUpdateSettingsMutation } from '../../communication/api/generated-api';
import { useGetLiveStateQuery } from '../../communication/api/live-api';
import { AxiosQueryError } from '../../communication/axios-base-query';

const PARSER_CHOICES = [
  { value: 'canned', label: 'Inspelade kvitton', hint: 'Gratis, Claude anropas inte' },
  { value: 'claude', label: 'Claude', hint: 'Läser riktiga kvitton, några ören per kvitto' },
];

/** The field's names as the server will see them: split on commas, trimmed, the empty ones dropped. */
function namesIn(text: string): string[] {
  return text.split(',').map((name) => name.trim()).filter((name) => name.length > 0);
}

/**
 * The owner's settings, over whatever page is shown: the names, who gets paid, and which parser answers. Saved as a
 * whole; changed names empty the receipt for everyone, so saving them with a receipt loaded asks first.
 */
export function SettingsDialog({ onClose }: { onClose: () => void }) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const { data: settings } = useGetSettingsQuery();

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
      className="m-auto w-[min(92vw,24rem)] rounded-lg border border-gray-200 bg-white p-0 text-gray-800 shadow-lg backdrop:bg-black/40"
      onClose={onClose}
    >
      <h2 className="px-4 pt-4 text-lg font-medium">Inställningar</h2>
      {settings ? <SettingsForm settings={settings} onClose={onClose} /> : <p className="p-4">Hämtar …</p>}
    </dialog>
  );
}

/** Mounted once the settings are at hand, so that the fields can start from them. */
function SettingsForm({ settings, onClose }: { settings: Settings, onClose: () => void }) {
  const { data: live } = useGetLiveStateQuery();
  const [updateSettings, { isLoading: saving }] = useUpdateSettingsMutation();
  const [namesText, setNamesText] = useState(settings.names.join(', '));
  const [payee, setPayee] = useState(settings.payee);
  const [receiptParser, setReceiptParser] = useState(settings.receiptParser);
  const [confirmingReset, setConfirmingReset] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const names = namesIn(namesText);
  // The payee follows the names: one no longer among them falls back to the first.
  const chosenPayee = names.includes(payee) ? payee : (names[0] ?? '');
  const namesChanged = names.join(',') !== settings.names.join(',');
  const receiptLoaded = !!live?.receipt;

  async function save() {
    setError(null);
    try {
      await updateSettings({
        settingsRequest: { names: namesText.split(','), payee: chosenPayee, receiptParser },
      }).unwrap();
      onClose();
    } catch (failure) {
      const queryError = failure as Partial<AxiosQueryError>;
      setError(queryError.serverMessage ?? 'Inställningarna kunde inte sparas. Försök igen.');
      setConfirmingReset(false);
    }
  }

  function handleSave() {
    if (namesChanged && receiptLoaded) {
      setConfirmingReset(true);
    } else {
      void save();
    }
  }

  return (
    <div className="flex flex-col gap-4 p-4">
      <label className="flex flex-col gap-1">
        <span className="text-sm font-medium">Namn</span>
        <input
          className="rounded-md border border-gray-300 px-3 py-2"
          value={namesText}
          onChange={(event) => setNamesText(event.target.value)}
          autoCapitalize="words"
          autoComplete="off"
        />
        <span className="text-xs text-gray-500">Två till fyra, åtskilda med komma.</span>
      </label>

      <label className="flex flex-col gap-1">
        <span className="text-sm font-medium">Får betalt</span>
        <select
          className="rounded-md border border-gray-300 bg-white px-3 py-2"
          value={chosenPayee}
          onChange={(event) => setPayee(event.target.value)}
        >
          {names.map((name) => <option key={name} value={name}>{name}</option>)}
        </select>
      </label>

      <fieldset className="flex flex-col gap-2">
        <legend className="pb-1 text-sm font-medium">Kvitton läses av</legend>
        {PARSER_CHOICES.map((choice) => (
          <label key={choice.value} className="flex items-start gap-2 cursor-pointer">
            <input
              type="radio"
              name="receiptParser"
              className="mt-1 accent-brand"
              checked={receiptParser === choice.value}
              onChange={() => setReceiptParser(choice.value)}
            />
            <span>
              {choice.label}
              <span className="block text-xs text-gray-500">{choice.hint}</span>
            </span>
          </label>
        ))}
      </fieldset>

      {error && <p role="alert" className="text-sm text-red-700">{error}</p>}

      {confirmingReset ? (
        <div className="flex flex-col gap-2">
          <p className="text-sm">Med nya namn töms kvittot för alla. Det går inte att ångra.</p>
          <div className="flex justify-end gap-2">
            <button className="rounded-md px-4 py-2 hover:bg-gray-100 cursor-pointer" onClick={() => setConfirmingReset(false)}>
              Avbryt
            </button>
            <button
              className="rounded-md bg-red-600 px-4 py-2 text-white hover:bg-red-700 cursor-pointer disabled:bg-red-300"
              disabled={saving}
              onClick={() => void save()}
            >
              Spara och töm
            </button>
          </div>
        </div>
      ) : (
        <div className="flex justify-end gap-2">
          <button className="rounded-md px-4 py-2 hover:bg-gray-100 cursor-pointer" onClick={onClose}>
            Avbryt
          </button>
          <button
            className="rounded-md bg-brand px-4 py-2 font-medium text-white hover:bg-brand-hover cursor-pointer disabled:bg-brand/40"
            disabled={saving}
            onClick={handleSave}
          >
            Spara
          </button>
        </div>
      )}
    </div>
  );
}
