package com.tarnvik.pbs.service;

import com.tarnvik.pbs.model.Receipt;
import com.tarnvik.pbs.model.ReceiptAdjustment;
import com.tarnvik.pbs.model.ReceiptItem;
import com.tarnvik.pbs.model.Split;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class SplitCalculatorTest {
  private static final List<String> PARTICIPANTS = List.of("Jesper", "Anders", "Anna", "Kia");

  @Test
  void calculate_nothingAssigned_everythingIsUnassigned() {
    Receipt receipt = receipt(List.of(), 1000, 2000);

    Split split = SplitCalculator.calculate(receipt, Map.of(), PARTICIPANTS);

    assertThat(split.receiptId()).isEqualTo(receipt.receiptId());
    assertThat(split.items()).extracting("id", "people").containsExactly(tuple(1, List.of()), tuple(2, List.of()));
    assertThat(split.unassigned().total()).isEqualTo(3000);
    assertThat(split.people()).extracting("name", "total").containsExactly(
      tuple("Jesper", 0L), tuple("Anders", 0L), tuple("Anna", 0L), tuple("Kia", 0L));
  }

  @Test
  void calculate_personalItem_goesWholeToThatPerson() {
    Split split = SplitCalculator.calculate(receipt(List.of(), 1000, 2000), Map.of(1, List.of("Anna")), PARTICIPANTS);

    assertThat(person(split, "Anna").items()).containsExactly(new Split.ItemShare(1, 1000));
    assertThat(person(split, "Anna").total()).isEqualTo(1000);
    assertThat(split.unassigned().total()).isEqualTo(2000);
  }

  @Test
  void calculate_sharedItem_isDividedEvenly() {
    Split split = SplitCalculator.calculate(receipt(List.of(), 1000), Map.of(1, List.of("Anna", "Kia")),
      PARTICIPANTS);

    assertThat(person(split, "Anna").items()).containsExactly(new Split.ItemShare(1, 500));
    assertThat(person(split, "Kia").items()).containsExactly(new Split.ItemShare(1, 500));
    assertThat(split.unassigned().total()).isZero();
  }

  /** 100 kr in three: the shares shown make the price to the öre, the öre that does not divide going first. */
  @Test
  void calculate_threeWayShare_sharesAddUpToThePrice() {
    Split split = SplitCalculator.calculate(receipt(List.of(), 10000), Map.of(1, List.of("Jesper", "Anna", "Kia")),
      PARTICIPANTS);

    assertThat(person(split, "Jesper").items()).containsExactly(new Split.ItemShare(1, 3334));
    assertThat(person(split, "Anna").items()).containsExactly(new Split.ItemShare(1, 3333));
    assertThat(person(split, "Kia").items()).containsExactly(new Split.ItemShare(1, 3333));
    // 33,33 kr each, exactly, rounded once to whole kronor.
    assertThat(person(split, "Jesper").total()).isEqualTo(3300);
  }

  @Test
  void calculate_totalIsRoundedHalfUpToWholeKronor() {
    Receipt receipt = receipt(List.of(), 34550, 34549);

    Split split = SplitCalculator.calculate(receipt, Map.of(1, List.of("Anna"), 2, List.of("Kia")), PARTICIPANTS);

    assertThat(person(split, "Anna").total()).isEqualTo(34600);
    assertThat(person(split, "Kia").total()).isEqualTo(34500);
  }

  /** The unassigned items are a fifth bucket, so the discount is spread over what is left too. */
  @Test
  void calculate_adjustmentsAreSpreadInProportionToTheItems() {
    Receipt receipt = receipt(List.of(new ReceiptAdjustment("Rabatt", -1000)), 6000, 4000);

    Split split = SplitCalculator.calculate(receipt, Map.of(1, List.of("Anna")), PARTICIPANTS);

    assertThat(person(split, "Anna").adjustmentShare()).isEqualTo(-600);
    assertThat(person(split, "Anna").total()).isEqualTo(5400);
    assertThat(split.unassigned().adjustmentShare()).isEqualTo(-400);
    assertThat(split.unassigned().total()).isEqualTo(3600);
  }

  /** Bistro Exempel, one item each: rounding once per person keeps the drift within 2 kr of the lines. */
  @Test
  void calculate_everythingAssigned_roundedTotalsWithinTwoKronorOfTheLines() {
    Receipt receipt = receipt(List.of(new ReceiptAdjustment("Rabatt 10%", -3390),
      new ReceiptAdjustment("Öresavrundning", -10)), 18900, 4500, 7000, 3500);
    Map<Integer, List<String>> assignees = Map.of(1, List.of("Jesper"), 2, List.of("Anders"), 3, List.of("Anna"),
      4, List.of("Kia"));

    Split split = SplitCalculator.calculate(receipt, assignees, PARTICIPANTS);

    assertThat(split.unassigned().total()).isZero();
    assertThat(split.people()).extracting("total").containsExactly(17000L, 4000L, 6300L, 3100L);
    long roundedSum = split.people().stream().mapToLong(Split.Person::total).sum();
    assertThat(Math.abs(roundedSum - receipt.linesTotal())).isLessThanOrEqualTo(200);
  }

  private static Split.Person person(Split split, String name) {
    return split.people().stream().filter(person -> person.name().equals(name)).findFirst().orElseThrow();
  }

  private static Receipt receipt(List<ReceiptAdjustment> adjustments, long... prices) {
    List<ReceiptItem> items = new ArrayList<>();
    for (long price : prices) {
      items.add(new ReceiptItem(items.size() + 1, "Rad " + (items.size() + 1), 1, null, price, null));
    }
    long linesTotal = items.stream().mapToLong(ReceiptItem::price).sum()
      + adjustments.stream().mapToLong(ReceiptAdjustment::amount).sum();
    return new Receipt(UUID.randomUUID(), "Restaurang", linesTotal, items, adjustments, linesTotal, true);
  }
}
