import { Receipt } from '../../communication/api/generated-api';
import { Payment, Split, SplitPerson } from '../../types/live';
import { formatKronor, formatWholeKronor } from '../../util/money';
import { swishPaymentLink } from '../../util/swish';
// Swish's primary logo for light backgrounds, from its marketing toolbox ("Swish Logo Primary Light-BG SVG.svg").
import swishLogo from '../../assets/swish-logo-primary-light-bg.svg';

/** The line for a bucket's part of the discounts and rounding lines, spread in proportion to its items. */
export const ADJUSTMENT_SHARE_LABEL = 'Rabatt och avrundning, andel';

const LINE_CLASS = 'grid grid-cols-[1fr_auto] gap-x-4';
const AMOUNT_CLASS = 'text-right tabular-nums whitespace-nowrap';

type Props = {
  receipt: Receipt,
  split: Split
}

/**
 * Each person's total, two by two, and the drift line under them, while the owner is still ticking.
 */
export function SplitTotals({ receipt, split }: Props) {
  return (
    <div className="pb-2">
      <div className="grid grid-cols-2 gap-x-6 gap-y-1">
        {split.people.map((person) => (
          <div key={person.name} className={LINE_CLASS}>
            <span className="truncate">{person.name}</span>
            <span className={`${AMOUNT_CLASS} font-medium`}>{formatWholeKronor(person.total)}</span>
          </div>
        ))}
      </div>
      <DriftLine receipt={receipt} split={split} />
    </div>
  );
}

/**
 * Once the owner has pressed "Klar", the totals become this: one row per person with the total and a Swish button
 * that opens the app with the payment filled in. The payee, who paid the bill, gets none.
 */
export function SplitPayment({ receipt, split, payment }: Props & { payment: Payment }) {
  return (
    <div className="pb-2">
      <ul>
        {split.people.map((person) => (
          // A fixed third column, so that the totals line up whether a row has a button or not.
          <li key={person.name} className="grid grid-cols-[1fr_auto_7rem] items-center gap-x-3 py-1">
            <span className="min-w-0 truncate">{person.name}</span>
            <span className={`${AMOUNT_CLASS} font-medium`}>{formatWholeKronor(person.total)}</span>
            {person.name === payment.payee ? (
              <span className="text-sm text-gray-500">Får betalt</span>
            ) : (
              <SwishButton href={swishPaymentLink(payment.swishNumber, person.total, payment.message)} />
            )}
          </li>
        ))}
      </ul>
      <DriftLine receipt={receipt} split={split} />
    </div>
  );
}

/**
 * Swish's own logo on a white button, as its guidelines want it: not recoloured, not cropped. A plain link: the
 * phone hands it to the Swish app.
 */
function SwishButton({ href }: { href: string }) {
  return (
    <a
      href={href}
      aria-label="Betala med Swish"
      className="flex h-10 items-center justify-center gap-1.5 rounded-md border border-gray-300 bg-white px-2 text-sm font-medium text-gray-800 hover:bg-gray-50"
    >
      <img src={swishLogo} alt="" className="h-8 w-auto" />
      Betala
    </a>
  );
}

/**
 * The receipt's total next to the sum of the rounded totals, so that the arithmetic is visible and shows how far
 * the owner has got. The friends round up as they see fit when paying.
 */
function DriftLine({ receipt, split }: Props) {
  const roundedSum = split.people.reduce((sum, person) => sum + person.total, 0);

  return (
    <p className="pt-1 text-xs text-gray-500">
      Kvittot {formatKronor(receipt.total)} · Allas summor {formatWholeKronor(roundedSum)}
    </p>
  );
}

/**
 * Who had what: under each person the items they were ticked for, with their share, so that a friend can see not
 * only that a total is wrong but why.
 */
export function SplitPeople({ receipt, split }: Props) {
  return (
    <div className="py-1">
      {split.people.map((person) => (
        <PersonItems key={person.name} person={person} receipt={receipt} split={split} />
      ))}
    </div>
  );
}

function PersonItems({ person, receipt, split }: Props & { person: SplitPerson }) {
  return (
    <div className="border-t border-gray-200 py-2 first:border-t-0">
      <div className={`${LINE_CLASS} font-medium`}>
        <span className="min-w-0 break-words">{person.name}</span>
        <span className={AMOUNT_CLASS}>{formatWholeKronor(person.total)}</span>
      </div>
      {person.items.length === 0 ? (
        <p className="text-sm text-gray-500">Inget ännu.</p>
      ) : (
        <ul className="text-sm text-gray-600">
          {person.items.map((share) => {
            const item = receipt.items.find((candidate) => candidate.id === share.id);
            const sharedBy = split.items.find((candidate) => candidate.id === share.id)?.people.length ?? 1;
            return (
              <li key={share.id} className={LINE_CLASS}>
                <span className="min-w-0 break-words">
                  {item?.name}
                  {sharedBy > 1 && <span className="text-gray-400"> · delad på {sharedBy}</span>}
                </span>
                <span className={AMOUNT_CLASS}>{formatKronor(share.share)}</span>
              </li>
            );
          })}
          {person.adjustmentShare !== 0 && (
            <li className={LINE_CLASS}>
              <span>{ADJUSTMENT_SHARE_LABEL}</span>
              <span className={AMOUNT_CLASS}>{formatKronor(person.adjustmentShare)}</span>
            </li>
          )}
        </ul>
      )}
    </div>
  );
}
