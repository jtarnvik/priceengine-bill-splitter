package com.tarnvik.pbs.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;
import java.util.Optional;

/**
 * What Claude reads from a receipt photo. The JSON schema of the structured output is generated from these records,
 * and every @JsonPropertyDescription becomes part of it, so the instructions about a field live next to the field.
 * The canned parser reads the same JSON back from a recording.
 *
 * <p>Money is integer öre throughout, in the schema as in Java, so the model can never answer 24.999999.
 *
 * <p>The two fields that may be missing are Optional, because that is what the SDK's schema generator turns into
 * {@code "type": ["integer", "null"]}. A plain nullable Long becomes a required integer, which the model would then
 * have to make up.
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public record ParsedReceipt(
  @JsonPropertyDescription("The restaurant's name, as printed at the top of the receipt")
  String restaurantName,
  @JsonPropertyDescription("The amount to pay as printed on the receipt, usually on the line \"Att betala\" (or "
    + "\"Totalt\" or \"Summa\" when there is none), in öre. Read it from the receipt; never compute it from the items")
  long total,
  @JsonPropertyDescription("One entry per priced line, in the receipt's order")
  List<Item> items,
  @JsonPropertyDescription("Lines that change the amount without being something ordered: discounts (\"rabatt\"), "
    + "\"öresavrundning\" and the like, in the receipt's order. Empty when there are none")
  List<Adjustment> adjustments
) {
  public record Item(
    @JsonPropertyDescription("The item's name exactly as printed, abbreviations included")
    String name,
    @JsonPropertyDescription("How many, at least 1. A line such as \"3 x Öl\" or \"3 st Öl à 60,00\" has quantity 3")
    int quantity,
    @JsonPropertyDescription("The price of one, in öre, when the receipt shows it; null when it does not")
    Optional<Long> unitPrice,
    @JsonPropertyDescription("The line's total as printed, in öre: for \"3 x Öl 60,00 180,00\" it is 18000")
    long price,
    @JsonPropertyDescription("Unpriced lines printed under the item, such as \"utan lök\", joined with \", \"; "
      + "null when there are none")
    Optional<String> note
  ) {}

  public record Adjustment(
    @JsonPropertyDescription("The line's text as printed, such as \"Rabatt 10%\" or \"Öresavrundning\"")
    String name,
    @JsonPropertyDescription("The amount in öre, negative when it lowers the total: a discount of 39,00 is -3900")
    long amount
  ) {}
}
