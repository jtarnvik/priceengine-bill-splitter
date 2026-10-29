import { useEffect, useState } from 'react';

const GREETED_RECEIPT_KEY = 'pbs.greetedReceiptId';

/**
 * Whether this receipt should be greeted: true the first time this tab shows it, false ever after. Kept in
 * sessionStorage, so that a reconnect, an unlocked phone and a reload of the tab do not replay the greeting, while a
 * new tab plays it.
 *
 * Call it in a component keyed on the receiptId. The answer is read once, when that component mounts, and stays the
 * same for its life: read again after the effect below has stored the id, it would turn false in the middle of the
 * animation. Reading in the initialiser and writing in an effect also keeps it right under React's double rendering
 * in development.
 */
export function useGreeting(receiptId: string): boolean {
  const [alreadyGreeted] = useState(() => sessionStorage.getItem(GREETED_RECEIPT_KEY) === receiptId);

  useEffect(() => {
    sessionStorage.setItem(GREETED_RECEIPT_KEY, receiptId);
  }, [receiptId]);

  return !alreadyGreeted;
}
