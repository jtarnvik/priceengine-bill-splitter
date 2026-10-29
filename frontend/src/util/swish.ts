const SWISH_PAYMENT_URL = 'https://app.swish.nu/1/p/sw/';

/**
 * A link that opens the Swish app with a payment filled in: the payee's number, the amount and the message.
 * `edit=amt` lets the friend change the amount, to round up as they see fit; Swish locks it otherwise.
 *
 * Exactly the form of Swish's developer guide (`?sw=46701234567&amt=170.0&msg=Hot%20Dog`) and nothing more: an
 * iPhone refuses anything else with "Länken som användes för att öppna har ett felaktigt format". Found
 * 2026-10-29 with a `cur=SEK` the guide does not have. The amount with a decimal point; each value through
 * encodeURIComponent, not URLSearchParams, which writes a space as `+`.
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
