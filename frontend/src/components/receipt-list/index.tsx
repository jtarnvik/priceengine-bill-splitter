import { CSSProperties } from 'react';

import { Receipt, ReceiptItem } from '../../communication/api/generated-api';
import { Split } from '../../types/live';
import { formatKronor } from '../../util/money';

/**
 * Every `<li>` is its own grid, name and price. The columns line up across rows without one shared grid because the
 * tracks do not depend on content: the price column is as wide as its own price, right-aligned with tabular digits,
 * and every row is equally wide. A long name wraps rather than pushing the price out. Not a `<table>`: two lines
 * per item would need colspan, and auto table layout lets one long name move every column.
 */
const ROW_CLASS = 'grid grid-cols-[1fr_auto] gap-x-4 px-4 py-2 even:bg-white';
const NAME_CLASS = 'min-w-0 break-words';
const PRICE_CLASS = 'text-right tabular-nums whitespace-nowrap';
const DETAIL_CLASS = 'text-xs text-gray-500';
/**
 * A ticked row, name, details and price alike: a little lighter than gray-400 (2026-10-29: gray-400 alone, with the
 * details still gray-500, was hard to tell from an unticked row on a phone).
 */
const DONE_CLASS = 'text-gray-400/80';

/** Each row starts this long after the one above, */
const GREETING_STEP_MS = 40;
/** but the last one within this, however long the receipt: a greeting, not a wait. */
const GREETING_MAX_DELAY_MS = 1_000;

type FooterRow = {
  label: string,
  /** Öre. */
  amount: number
}

type Props = {
  receipt: Receipt,
  /** The rows under the line: "Totalt" and "Kvar att dela på" for the owner, "Kvar att dela på" for the friends. */
  footer: FooterRow[],
  /** Let the rows appear one by one. Mount the list anew per receipt (key it on receiptId) for this to play. */
  greet?: boolean,
  /** The owner's ticking: a line of boxes under every item, one per person, set from and sent as whole sets. */
  assignment?: {
    split: Split,
    onChange: (itemId: number, people: string[]) => void
  }
}

/**
 * The rows in receipt order, never sorted; then the adjustments, the receipt's total and, if needed, the warning.
 * The same on both pages, so that the friends see exactly what the owner sees.
 */
export function ReceiptList({ receipt, footer, greet = false, assignment }: Props) {
  // The footer and the warning come in as one more row, after the last.
  const rowCount = receipt.items.length + receipt.adjustments.length + 1;
  const step = Math.min(GREETING_STEP_MS, GREETING_MAX_DELAY_MS / Math.max(1, rowCount - 1));
  function greeting(index: number): { className: string, style?: CSSProperties } {
    if (!greet) {
      return { className: '' };
    }
    return { className: ' motion-safe:animate-row-greeting', style: { animationDelay: `${Math.round(index * step)}ms` } };
  }
  const footerGreeting = greeting(rowCount - 1);

  return (
    <div className="pb-2">
      {/* Out to the card's edges, so that the stripes span its width. */}
      <ul className="-mx-4">
        {receipt.items.map((item, index) => {
          const rowGreeting = greeting(index);
          const people = assignment?.split.items.find((candidate) => candidate.id === item.id)?.people ?? [];
          // Someone ticked: the name and price grey, never the boxes — a shared row still takes ticks, and the rows
          // still black are the ones left to do.
          const done = people.length > 0;
          const doneClass = done ? ` ${DONE_CLASS}` : '';
          return (
            <li key={item.id} className={ROW_CLASS + rowGreeting.className} style={rowGreeting.style}>
              <div className={NAME_CLASS + doneClass}>
                {item.name}
                <ItemDetails item={item} done={done} />
              </div>
              <div className={PRICE_CLASS + doneClass}>{formatKronor(item.price)}</div>
              {assignment && (
                <AssigneeBoxes
                  names={assignment.split.people.map((person) => person.name)}
                  people={people}
                  onChange={(newPeople) => assignment.onChange(item.id, newPeople)}
                />
              )}
            </li>
          );
        })}
        {receipt.adjustments.map((adjustment, index) => {
          const rowGreeting = greeting(receipt.items.length + index);
          return (
            // Adjustments have no id; they never change once the receipt is parsed, so their position is stable.
            <li
              key={`adjustment-${index}`}
              className={`${ROW_CLASS} text-gray-600${rowGreeting.className}`}
              style={rowGreeting.style}
            >
              <div className={NAME_CLASS}>{adjustment.name}</div>
              <div className={PRICE_CLASS}>{formatKronor(adjustment.amount)}</div>
            </li>
          );
        })}
      </ul>
      <div className={`border-t border-gray-300 pt-2 font-medium${footerGreeting.className}`} style={footerGreeting.style}>
        {footer.map((row) => (
          <div key={row.label} className="grid grid-cols-[1fr_auto] gap-x-4">
            <div>{row.label}</div>
            <div className={PRICE_CLASS}>{formatKronor(row.amount)}</div>
          </div>
        ))}
      </div>
      {!receipt.totalMatches && (
        <p
          role="alert"
          className={`mt-3 rounded-md border border-amber-300 bg-amber-50 px-3 py-2 text-sm text-amber-900${footerGreeting.className}`}
          style={footerGreeting.style}
        >
          Raderna blir {formatKronor(receipt.linesTotal)}, men kvittot säger {formatKronor(receipt.total)}.
          Jämför priserna med kvittot.
        </p>
      )}
    </div>
  );
}

type AssigneeBoxesProps = {
  names: string[],
  people: string[],
  onChange: (people: string[]) => void
}

/**
 * One box per person across the row's full width, `grid-cols-4`, so that the boxes line up from row to row. The
 * label is the tap target, larger than the box. A tap sends the row's whole new set of people, in the order of the
 * names — never a toggle, so that a request repeated or arriving late changes nothing it should not.
 */
function AssigneeBoxes({ names, people, onChange }: AssigneeBoxesProps) {
  function toggle(name: string) {
    const ticked = people.includes(name);
    onChange(names.filter((candidate) => (candidate === name ? !ticked : people.includes(candidate))));
  }

  return (
    <div className="col-span-2 grid grid-cols-4 gap-x-1 pt-1">
      {names.map((name) => (
        <label key={name} className="flex min-w-0 items-center gap-1.5 py-1.5 text-sm text-gray-800 cursor-pointer select-none">
          <input
            type="checkbox"
            className="h-4 w-4 shrink-0 accent-brand cursor-pointer"
            checked={people.includes(name)}
            onChange={() => toggle(name)}
          />
          <span className="truncate">{name}</span>
        </label>
      ))}
    </div>
  );
}

/** Quantity and unit price ("3 × 60,00 kr") and the note, small and grey under the name. */
function ItemDetails({ item, done = false }: { item: ReceiptItem, done?: boolean }) {
  const detailClass = done ? `text-xs ${DONE_CLASS}` : DETAIL_CLASS;
  let quantityText = null;
  if (item.quantity > 1) {
    quantityText = item.unitPrice !== null ? `${item.quantity} × ${formatKronor(item.unitPrice)}` : `${item.quantity} st`;
  }
  return (
    <>
      {quantityText && <div className={detailClass}>{quantityText}</div>}
      {item.note && <div className={detailClass}>{item.note}</div>}
    </>
  );
}
