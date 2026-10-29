package com.tarnvik.pbs.model;

import java.util.List;
import java.util.UUID;

/**
 * Who had what, and what it comes to: the stream's {@code split} event, computed by SplitCalculator after every
 * change. Hand-written in the frontend's live.ts, since springdoc cannot see the stream. All amounts in öre.
 *
 * @param receiptId the receipt this split belongs to; a page holding another receipt ignores it
 * @param items every item of the receipt, in its order, with the people ticked for it
 * @param people every participant, in the configured order, with what they had. The names travel here, so that the
 *               labels arrive with the state they belong to
 * @param unassigned what nobody has been ticked for yet
 * @param done whether the owner has pressed "Klar": the amounts are final, and the friends can pay
 * @param payment who to pay and how, present only while done: the owner's Swish number reaches the pages then and
 *                at no other time
 */
public record Split(UUID receiptId, List<Item> items, List<Person> people, Unassigned unassigned, boolean done,
                    Payment payment) {
  /** The same split, done with this payment, or not done when it is null. */
  public Split withPayment(Payment newPayment) {
    return new Split(receiptId, items, people, unassigned, newPayment != null, newPayment);
  }

  /** Whether every item has someone: "Kvar att dela på" is then zero, and the split can be marked done. */
  public boolean allAssigned() {
    return items.stream().noneMatch(item -> item.people().isEmpty());
  }

  /** @param people in the configured order; empty when the item is unassigned */
  public record Item(int id, List<String> people) {}

  /**
   * @param items the items this person was ticked for, each with their share. The shares of one item add up to its
   *              price, the öre that does not divide going to the first people
   * @param adjustmentShare their part of the discounts and rounding lines, in proportion to their items, to the öre
   * @param total everything, rounded to whole kronor: always a multiple of 100
   */
  public record Person(String name, List<ItemShare> items, long adjustmentShare, long total) {}

  public record ItemShare(int id, long share) {}

  /**
   * @param adjustmentShare the unassigned items' part of the discounts and rounding lines, to the öre
   * @param total the unassigned items and that part, to the öre: "Kvar att dela på"
   */
  public record Unassigned(long adjustmentShare, long total) {}

  /**
   * @param payee the participant who paid the bill and gets no Swish button
   * @param swishNumber PBS_SWISH_NUMBER, exactly as written there
   * @param message for the Swish payment: the restaurant and the date, within Swish's characters and 50 of them
   */
  public record Payment(String payee, String swishNumber, String message) {}
}
