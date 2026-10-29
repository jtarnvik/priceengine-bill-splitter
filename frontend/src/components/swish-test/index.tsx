// TEMPORARY (2026-10-29): every form of Swish link we can think of, each with its own button, to find the one an
// iPhone accepts. Goes once swishPaymentLink in util/swish.ts uses the form that works.
//
// Round 1: only `…/1/p/sw` and `swish://` opened Swish without "felaktigt format"; `…/1/p/sw/` failed even bare.
// Round 2: without the slash every link opened Swish, but nothing was filled in. Round 3: every form with the slash
// failed — because every one paid the owner's own number from the owner's own phone. A link typed by hand with
// another person's number worked: Swish refuses a payment to its own account with the same generic message.
// Round 4: PBS_SWISH_NUMBER temporarily set to another person's number, and a few forms to choose between.
import { Payment } from '../../types/live';
import { swishPaymentLink } from '../../util/swish';

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
  const { international, national } = numberForms(payment.swishNumber);
  const message = payment.message;
  const link = (parameters: [string, string][]) => `${BASE}?${query(parameters)}`;
  return [
    {
      label: '1. Som appens Betala-knapp nu: 46… + 1.00 + riktigt meddelande + edit=amt',
      url: swishPaymentLink(payment.swishNumber, 100, message),
    },
    {
      label: '2. Som 1 utan edit (beloppet låst)',
      url: link([['sw', international], ['amt', AMOUNT], ['msg', message]]),
    },
    {
      label: '3. Som 1 + src=qr',
      url: link([['sw', international], ['amt', AMOUNT], ['msg', message], ['edit', 'amt'], ['src', 'qr']]),
    },
    {
      label: '4. Testarens form: 46… + amt=1 + cur=SEK + msg + src=qr',
      url: link([['sw', international], ['amt', '1'], ['cur', 'SEK'], ['msg', message], ['src', 'qr']]),
    },
    {
      label: '5. Som 1 med 0…',
      url: link([['sw', national], ['amt', AMOUNT], ['msg', message], ['edit', 'amt']]),
    },
    {
      label: '6. Som 1 med edit=amt,msg',
      url: link([['sw', international], ['amt', AMOUNT], ['msg', message], ['edit', 'amt,msg']]),
    },
    { label: '7. swish://payment, JSON, 46…', url: jsonScheme(international, 1, message) },
  ];
}

/** Shown on the owner's page while the split is done, where the payment, and so the number, is at hand. */
export function SwishTestPanel({ payment }: { payment: Payment }) {
  return (
    <section className="rounded-lg border border-amber-300 bg-amber-50 p-3 text-gray-800">
      <h2 className="font-medium">Swish-test, omgång 4: någon annans nummer (tillfälligt)</h2>
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
