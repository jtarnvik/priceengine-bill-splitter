package com.tarnvik.pbs.service;

import com.tarnvik.pbs.model.Receipt;
import com.tarnvik.pbs.model.ReceiptAdjustment;
import com.tarnvik.pbs.model.ReceiptItem;
import com.tarnvik.pbs.model.Split;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Divides the receipt's lines between the people ticked for them (see The split in CLAUDE.md):
 * <ul>
 *   <li>an item evenly between its people;</li>
 *   <li>the adjustments — discounts, öresavrundning — in proportion to each bucket's items, the unassigned items
 *       being a fifth bucket, so that the numbers always add up while the owner is still ticking;</li>
 *   <li>exact in between, rounded once at the end: a person's total to whole kronor, everything else to the öre.
 *       Rounding per item instead would let the errors pile up over a long receipt.</li>
 * </ul>
 * The split is of the lines, items plus adjustments, not of the printed total: when the two differ, the sum warning
 * is already showing.
 */
public final class SplitCalculator {
  // Far more than öre need; the exact values never leave this class.
  private static final int SCALE = 10;
  private static final BigDecimal ORE_PER_KRONA = BigDecimal.valueOf(100);

  private SplitCalculator() {
  }

  /**
   * @param assignees each item id's people, in participant order; an item missing here, or with nobody, is unassigned
   */
  public static Split calculate(Receipt receipt, Map<Integer, List<String>> assignees, List<String> participants) {
    Map<String, BigDecimal> subtotals = new LinkedHashMap<>();
    Map<String, List<Split.ItemShare>> shares = new LinkedHashMap<>();
    for (String participant : participants) {
      subtotals.put(participant, BigDecimal.ZERO);
      shares.put(participant, new ArrayList<>());
    }
    BigDecimal unassignedSubtotal = BigDecimal.ZERO;
    BigDecimal itemsTotal = BigDecimal.ZERO;
    List<Split.Item> items = new ArrayList<>();

    for (ReceiptItem item : receipt.items()) {
      List<String> people = assignees.getOrDefault(item.id(), List.of());
      items.add(new Split.Item(item.id(), people));
      BigDecimal price = BigDecimal.valueOf(item.price());
      itemsTotal = itemsTotal.add(price);
      if (people.isEmpty()) {
        unassignedSubtotal = unassignedSubtotal.add(price);
      } else {
        BigDecimal exactShare = price.divide(BigDecimal.valueOf(people.size()), SCALE, RoundingMode.HALF_EVEN);
        long[] shownShares = divideInWholeOre(item.price(), people.size());
        for (int i = 0; i < people.size(); i++) {
          subtotals.merge(people.get(i), exactShare, BigDecimal::add);
          shares.get(people.get(i)).add(new Split.ItemShare(item.id(), shownShares[i]));
        }
      }
    }

    BigDecimal adjustmentsTotal = BigDecimal.valueOf(
      receipt.adjustments().stream().mapToLong(ReceiptAdjustment::amount).sum());
    List<Split.Person> people = new ArrayList<>();
    for (String participant : participants) {
      BigDecimal subtotal = subtotals.get(participant);
      BigDecimal adjustmentShare = proportionalShare(adjustmentsTotal, subtotal, itemsTotal);
      people.add(new Split.Person(participant, List.copyOf(shares.get(participant)), toOre(adjustmentShare),
        toWholeKronor(subtotal.add(adjustmentShare))));
    }
    BigDecimal unassignedAdjustmentShare = proportionalShare(adjustmentsTotal, unassignedSubtotal, itemsTotal);
    Split.Unassigned unassigned = new Split.Unassigned(toOre(unassignedAdjustmentShare),
      toOre(unassignedSubtotal.add(unassignedAdjustmentShare)));

    return new Split(receipt.receiptId(), List.copyOf(items), List.copyOf(people), unassigned, false, null);
  }

  /** A bucket's part of the adjustments, in proportion to its items. Without any items there is nothing to share. */
  private static BigDecimal proportionalShare(BigDecimal adjustmentsTotal, BigDecimal subtotal, BigDecimal itemsTotal) {
    if (itemsTotal.signum() == 0) {
      return BigDecimal.ZERO;
    }
    return adjustmentsTotal.multiply(subtotal).divide(itemsTotal, SCALE, RoundingMode.HALF_EVEN);
  }

  /**
   * An item's price per person in whole öre, for showing: the shares add up to the price, the öre that does not
   * divide going one each to the first people. The totals use the exact share instead.
   */
  private static long[] divideInWholeOre(long price, int count) {
    long[] result = new long[count];
    long base = Math.floorDiv(price, count);
    long remainder = Math.floorMod(price, count);
    for (int i = 0; i < count; i++) {
      result[i] = i < remainder ? base + 1 : base;
    }
    return result;
  }

  private static long toOre(BigDecimal exact) {
    return exact.setScale(0, RoundingMode.HALF_UP).longValueExact();
  }

  private static long toWholeKronor(BigDecimal exactOre) {
    return exactOre.divide(ORE_PER_KRONA, 0, RoundingMode.HALF_UP).multiply(ORE_PER_KRONA).longValueExact();
  }
}
