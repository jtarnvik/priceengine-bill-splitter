// TEMPORARY (2026-10-29): every form of Swish link we can think of, each with its own button, to find the one an
// iPhone accepts. Goes once swishPaymentLink in util/swish.ts uses the form that works.
//
// Round 1: only `…/1/p/sw` and `swish://` opened Swish without "felaktigt format"; `…/1/p/sw/` failed even bare.
// Round 2: without the slash every link opened Swish, but nothing was filled in: the app reads the parameters only
// with the slash, and refused ours. Round 3: with the slash, in the form a tester reports working on a phone
// (https://gist.github.com/Armandur/ead542b56cca2696b4286dbc05740da8, measured 2026-09-10):
// `?sw=…&amt=100&cur=SEK&msg=…&src=qr` — with `src=qr`, which we never sent, and `cur`. Then one change at a time.
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

/** The swish:// scheme with its JSON payload, as the tester's example: no `editable` anywhere. */
function jsonScheme(payee: string, amount: number | string, message: string): string {
  const data = { version: 1, payee: { value: payee }, amount: { value: amount }, message: { value: message } };
  return `swish://payment?data=${encodeURIComponent(JSON.stringify(data))}`;
}

function variants(payment: Payment): Variant[] {
  const { international, national, plus } = numberForms(payment.swishNumber);
  const message = payment.message;
  const link = (parameters: [string, string][]) => `${BASE}?${query(parameters)}`;
  return [
    {
      label: '1. Testarens form exakt: 46… + amt=1 + cur=SEK + msg=Test + src=qr',
      url: link([['sw', international], ['amt', '1'], ['cur', 'SEK'], ['msg', 'Test'], ['src', 'qr']]),
    },
    {
      label: '2. Som 1, med 0…',
      url: link([['sw', national], ['amt', '1'], ['cur', 'SEK'], ['msg', 'Test'], ['src', 'qr']]),
    },
    {
      label: '3. Som 1, med +46…',
      url: link([['sw', plus], ['amt', '1'], ['cur', 'SEK'], ['msg', 'Test'], ['src', 'qr']]),
    },
    { label: '4. Bara nummer 46… + src=qr', url: link([['sw', international], ['src', 'qr']]) },
    { label: '5. Bara nummer 0… + src=qr', url: link([['sw', national], ['src', 'qr']]) },
    {
      label: '6. Som 1 utan cur',
      url: link([['sw', international], ['amt', '1'], ['msg', 'Test'], ['src', 'qr']]),
    },
    {
      label: '7. Som 1 utan src',
      url: link([['sw', international], ['amt', '1'], ['cur', 'SEK'], ['msg', 'Test']]),
    },
    {
      label: '8. Som 1 med belopp 1.00',
      url: link([['sw', international], ['amt', AMOUNT], ['cur', 'SEK'], ['msg', 'Test'], ['src', 'qr']]),
    },
    {
      label: '9. Som 1 med riktigt meddelande',
      url: link([['sw', international], ['amt', '1'], ['cur', 'SEK'], ['msg', message], ['src', 'qr']]),
    },
    {
      label: '10. Som 1 + edit=amt',
      url: link([['sw', international], ['amt', '1'], ['cur', 'SEK'], ['msg', 'Test'], ['edit', 'amt'], ['src', 'qr']]),
    },
    {
      label: '11. Som 10 med 0…',
      url: link([['sw', national], ['amt', '1'], ['cur', 'SEK'], ['msg', 'Test'], ['edit', 'amt'], ['src', 'qr']]),
    },
    {
      label: '12. Hela betalningen: 46… + 1.00 + cur + riktigt meddelande + edit=amt + src=qr',
      url: link([['sw', international], ['amt', AMOUNT], ['cur', 'SEK'], ['msg', message], ['edit', 'amt'], ['src', 'qr']]),
    },
    { label: '13. swish://payment, testarens JSON, 46…', url: jsonScheme(international, 1, 'Test') },
    { label: '14. swish://payment, testarens JSON, 0…', url: jsonScheme(national, 1, 'Test') },
    { label: '15. swish://payment, JSON, 0…, belopp "1.00"', url: jsonScheme(national, AMOUNT, 'Test') },
  ];
}

/** Shown on the owner's page while the split is done, where the payment, and so the number, is at hand. */
export function SwishTestPanel({ payment }: { payment: Payment }) {
  return (
    <section className="rounded-lg border border-amber-300 bg-amber-50 p-3 text-gray-800">
      <h2 className="font-medium">Swish-test, omgång 3: med snedstreck och src=qr (tillfälligt)</h2>
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
