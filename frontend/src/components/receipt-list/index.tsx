import { Receipt, ReceiptItem } from '../../communication/api/generated-api';
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

type Props = {
  receipt: Receipt
}

/** The rows in receipt order, never sorted; then the adjustments, the receipt's total and, if needed, the warning. */
export function ReceiptList({ receipt }: Props) {
  return (
    <div className="pb-2">
      {/* Out to the card's edges, so that the stripes span its width. */}
      <ul className="-mx-4">
        {receipt.items.map((item) => (
          <li key={item.id} className={ROW_CLASS}>
            <div className={NAME_CLASS}>
              {item.name}
              <ItemDetails item={item} />
            </div>
            <div className={PRICE_CLASS}>{formatKronor(item.price)}</div>
          </li>
        ))}
        {receipt.adjustments.map((adjustment, index) => (
          // Adjustments have no id; they never change once the receipt is parsed, so their position is stable.
          <li key={`adjustment-${index}`} className={`${ROW_CLASS} text-gray-600`}>
            <div className={NAME_CLASS}>{adjustment.name}</div>
            <div className={PRICE_CLASS}>{formatKronor(adjustment.amount)}</div>
          </li>
        ))}
      </ul>
      <div className="grid grid-cols-[1fr_auto] gap-x-4 border-t border-gray-300 pt-2 font-medium">
        <div>Totalt</div>
        <div className={PRICE_CLASS}>{formatKronor(receipt.total)}</div>
      </div>
      {!receipt.totalMatches && (
        <p role="alert" className="mt-3 rounded-md border border-amber-300 bg-amber-50 px-3 py-2 text-sm text-amber-900">
          Raderna blir {formatKronor(receipt.linesTotal)}, men kvittot säger {formatKronor(receipt.total)}.
          Jämför priserna med kvittot.
        </p>
      )}
    </div>
  );
}

/** Quantity and unit price ("3 × 60,00 kr") and the note, small and grey under the name. */
function ItemDetails({ item }: { item: ReceiptItem }) {
  let quantityText = null;
  if (item.quantity > 1) {
    quantityText = item.unitPrice !== null ? `${item.quantity} × ${formatKronor(item.unitPrice)}` : `${item.quantity} st`;
  }
  return (
    <>
      {quantityText && <div className={DETAIL_CLASS}>{quantityText}</div>}
      {item.note && <div className={DETAIL_CLASS}>{item.note}</div>}
    </>
  );
}
