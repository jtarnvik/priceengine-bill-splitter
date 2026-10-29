const SWISH_PAYMENT_URL = 'https://app.swish.nu/1/p/sw/';

/**
 * A link that opens the Swish app with a payment filled in: the payee's number, the amount and the message.
 * `edit=amt` lets the friend change the amount, to round up as they see fit; Swish locks it otherwise.
 *
 * The form of Swish's developer guide (`?sw=46701234567&amt=170.0&msg=Hot%20Dog`), with the trailing slash in the
 * path — without it the app opens but ignores every parameter. Confirmed on an iPhone 2026-10-29, with the number as
 * 46…, the amount with a decimal point, and each value through encodeURIComponent, not URLSearchParams, which writes
 * a space as `+`. (`cur=SEK` and `src=qr` are accepted too, but not needed.)
 *
 * It cannot be tried from the payee's own phone: Swish refuses a payment to its own account with "Länken som
 * användes för att öppna har ett felaktigt format", the same message as for a malformed link.
 */
export function swishPaymentLink(swishNumber: string, amountOre: number, message: string): string {
  const parameters = [
    ['sw', swishNumber],
    ['amt', (amountOre / 100).toFixed(2)],
    ['msg', message],
    ['edit', 'amt'],
  ];
  return SWISH_PAYMENT_URL + '?' + parameters
    .map(([name, value]) => `${name}=${encodeURIComponent(value)}`)
    .join('&');
}
