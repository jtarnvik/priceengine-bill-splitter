import { useState } from 'react';

const ME_KEY = 'pbs.me';

type StoredMe = {
  receiptId: string,
  name: string
};

function readStoredMe(): StoredMe | null {
  try {
    return JSON.parse(localStorage.getItem(ME_KEY) ?? 'null') as StoredMe | null;
  } catch {
    return null;
  }
}

/**
 * Which of the people at the table this reader is: chosen in "Vem är du?" once per receipt, never checked — we are
 * all friends. Kept in localStorage with the receipt's id, so a reload, a reconnect or an unlocked phone does not ask
 * again for the same receipt, while the next one does, with last time's name offered first.
 *
 * @returns `name` for this receipt, or null until chosen; `previousName` from any receipt, to suggest
 */
export function useMe(receiptId: string) {
  const [stored, setStored] = useState<StoredMe | null>(readStoredMe);

  function choose(name: string) {
    const chosen = { receiptId, name };
    localStorage.setItem(ME_KEY, JSON.stringify(chosen));
    setStored(chosen);
  }

  return {
    name: stored?.receiptId === receiptId ? stored.name : null,
    previousName: stored?.name ?? null,
    choose,
  };
}
