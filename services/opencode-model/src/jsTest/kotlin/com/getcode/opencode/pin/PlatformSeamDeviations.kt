package com.getcode.opencode.pin

/**
 * Node's outputs where they differ from the JVM:
 * - ties (1.005, 2.675, 1.0005): Intl rounds a Double's shortest decimal, DecimalFormat its exact binary value;
 * - en-IN grouping: Intl groups lakhs and crores (12,34,567.89), DecimalFormat groups in threes;
 * - Fiat(String): DecimalFormat accepts NaN, infinity and non-ASCII digits, the Node parser rejects them.
 */
internal actual val platformSeamDeviations: Map<String, String> = mapOf(
    "formatFiat en-US 3ff0147ae147ae14 2 true" to "\$1.01",
    "formatFiat en-US 3ff0147ae147ae14 2 false" to "\$1.01",
    "formatFiat en-US 4005666666666666 2 true" to "\$2.68",
    "formatFiat en-US 4005666666666666 2 false" to "\$2.68",
    "formatFiat de-DE 3ff0147ae147ae14 2 true" to "\$1,01",
    "formatFiat de-DE 3ff0147ae147ae14 2 false" to "\$1,01",
    "formatFiat de-DE 4005666666666666 2 true" to "\$2,68",
    "formatFiat de-DE 4005666666666666 2 false" to "\$2,68",
    "formatFiat en-IN 3ff0147ae147ae14 2 true" to "\$1.01",
    "formatFiat en-IN 3ff0147ae147ae14 2 false" to "\$1.01",
    "formatFiat en-IN 4005666666666666 2 true" to "\$2.68",
    "formatFiat en-IN 4005666666666666 2 false" to "\$2.68",
    "formatFiat en-IN 4132d687e4189375 0 true" to "\$12,34,568",
    "formatFiat en-IN 4132d687e4189375 2 true" to "\$12,34,567.89",
    "formatFiat en-IN 4132d687e4189375 3 true" to "\$12,34,567.891",
    "formatFiat en-IN 41678c29dccccccd 0 true" to "\$1,23,45,679",
    "formatFiat en-IN 41678c29dccccccd 2 true" to "\$1,23,45,678.90",
    "formatFiat en-IN 41678c29dccccccd 3 true" to "\$1,23,45,678.900",
    "formatUsDecimal 3ff0020c49ba5e35 null" to "1.001",
    "Fiat(string) 'NaN'" to "throws",
    "Fiat(string) '∞'" to "throws",
    "Fiat(string) '-∞'" to "throws",
    "Fiat(string) '١٢٣'" to "throws",
    "Fiat(string) '１２３'" to "throws",
)
