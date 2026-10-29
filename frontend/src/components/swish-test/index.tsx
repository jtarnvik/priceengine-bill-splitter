// TEMPORARY (2026-10-29): every form of Swish link we can think of, each with its own button, to find the one an
// iPhone accepts. Goes once swishPaymentLink in util/swish.ts uses the form that works.
import { Payment } from '../../types/live';

const BASE = 'https://app.swish.nu/1/p/sw/';
const AMOUNT = '1.00';

type Variant = {
  label: string,
  url: string
}

/** The number as international (46…), national (0…) and with a plus (+46…), whatever form it is written in. */
function numberForms(swishNumber: string) {
  const digits = swishNumber.replace(/\D/g, '');
  const international = digits.startsWith('46') ? digits : `46${digits.replace(/^0/, '')}`;
  const national = `0${international.slice(2)}`;
  return { international, national, plus: `+${international}` };
}

function query(parameters: [string, string][]): string {
  return parameters.map(([name, value]) => `${name}=${encodeURIComponent(value)}`).join('&');
}

/** The old swish:// scheme with its JSON payload, in case the app still takes it. */
function legacyScheme(payee: string, message: string): string {
  const data = {
    version: 1,
    payee: { value: payee },
    amount: { value: 1, editable: true },
    message: { value: message, editable: true },
  };
  return `swish://payment?data=${encodeURIComponent(JSON.stringify(data))}`;
}

function variants(payment: Payment): Variant[] {
  const { international, national, plus } = numberForms(payment.swishNumber);
  const message = payment.message;
  return [
    { label: '1. Bara Swish-länken, inget annat', url: BASE },
    { label: '2. Bara Swish-länken, utan snedstreck sist', url: BASE.slice(0, -1) },
    { label: '3. Nummer 46…', url: `${BASE}?${query([['sw', international]])}` },
    { label: '4. Nummer 0…', url: `${BASE}?${query([['sw', national]])}` },
    { label: '5. Nummer +46…', url: `${BASE}?${query([['sw', plus]])}` },
    { label: '6. 46… + belopp 1.00', url: `${BASE}?${query([['sw', international], ['amt', AMOUNT]])}` },
    { label: '7. 46… + belopp 1 (heltal)', url: `${BASE}?${query([['sw', international], ['amt', '1']])}` },
    { label: '8. 0… + belopp 1.00', url: `${BASE}?${query([['sw', national], ['amt', AMOUNT]])}` },
    {
      label: '9. 46… + belopp + meddelande "Test"',
      url: `${BASE}?${query([['sw', international], ['amt', AMOUNT], ['msg', 'Test']])}`,
    },
    {
      label: '10. 46… + belopp + riktigt meddelande',
      url: `${BASE}?${query([['sw', international], ['amt', AMOUNT], ['msg', message]])}`,
    },
    {
      label: '11. 46… + belopp + "Test" + edit=amt',
      url: `${BASE}?${query([['sw', international], ['amt', AMOUNT], ['msg', 'Test'], ['edit', 'amt']])}`,
    },
    {
      label: '12. 46… + belopp + "Test" + edit=amt,msg',
      url: `${BASE}?${query([['sw', international], ['amt', AMOUNT], ['msg', 'Test'], ['edit', 'amt,msg']])}`,
    },
    {
      label: '13. Som appen gör nu: 46… + belopp + riktigt meddelande + edit=amt',
      url: `${BASE}?${query([['sw', international], ['amt', AMOUNT], ['msg', message], ['edit', 'amt']])}`,
    },
    {
      label: '14. Som 13, med 0…',
      url: `${BASE}?${query([['sw', national], ['amt', AMOUNT], ['msg', message], ['edit', 'amt']])}`,
    },
    { label: '15. swish://, bara appen', url: 'swish://' },
    { label: '16. swish://payment med JSON, 46…', url: legacyScheme(international, 'Test') },
    { label: '17. swish://payment med JSON, 0…', url: legacyScheme(national, 'Test') },
  ];
}

/** Shown on the owner's page while the split is done, where the payment, and so the number, is at hand. */
export function SwishTestPanel({ payment }: { payment: Payment }) {
  return (
    <section className="rounded-lg border border-amber-300 bg-amber-50 p-3 text-gray-800">
      <h2 className="font-medium">Swish-test (tillfälligt)</h2>
      <p className="pb-2 text-sm text-gray-600">Varje variant öppnar Swish på sitt sätt. Belopp 1 kr. Avbryt i appen.</p>
      <ul className="flex flex-col gap-3">
        {variants(payment).map((variant) => (
          <li key={variant.label}>
            <div className="text-sm font-medium">{variant.label}</div>
            <div className="break-all font-mono text-xs text-gray-600">{variant.url}</div>
            <a
              href={variant.url}
              className="mt-1 inline-block rounded-md border border-gray-300 bg-white px-3 py-1.5 text-sm hover:bg-gray-100"
            >
              Öppna
            </a>
          </li>
        ))}
      </ul>
    </section>
  );
}
