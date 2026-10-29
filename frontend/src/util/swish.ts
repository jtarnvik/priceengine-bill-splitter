const SWISH_PAYMENT_URL = 'https://app.swish.nu/1/p/sw/';

/**
 * A link that opens the Swish app with a payment filled in: the payee's number, the amount in whole kronor and the
 * message. `edit=amt` lets the friend change the amount, to round up as they see fit; Swish locks it otherwise.
 *
 * Each value through encodeURIComponent, not URLSearchParams, which writes a space as `+` that Swish might show as it
 * is. The parameters are as Swish's developer guide describes them; the phone test in step 7.4 confirms them.
 */
export function swishPaymentLink(swishNumber: string, amountOre: number, message: string): string {
  const parameters = [
    ['sw', swishNumber],
    ['amt', String(Math.round(amountOre / 100))],
    ['cur', 'SEK'],
    ['msg', message],
    ['edit', 'amt'],
  ];
  return SWISH_PAYMENT_URL + '?' + parameters
    .map(([name, value]) => `${name}=${encodeURIComponent(value)}`)
    .join('&');
}
