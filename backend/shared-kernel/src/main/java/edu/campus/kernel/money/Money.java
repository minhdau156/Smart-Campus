package edu.campus.kernel.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * An amount of money in one currency.
 *
 * <p>The amount is always stored with the currency's number of decimals (VND 0, USD 2). An amount
 * with more decimals is rejected, never rounded: {@code 1000.5 VND} does not exist. Because the
 * scale is fixed, {@code equals} does not care how the amount was written ({@code 10.5} equals
 * {@code 10.50}).
 */
public record Money(BigDecimal amount, Currency currency) {

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        int decimals = currency.getDefaultFractionDigits();
        if (decimals < 0) {
            throw new IllegalArgumentException(currency + " has no decimal rules, so it cannot be used as money");
        }
        try {
            amount = amount.setScale(decimals, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException tooManyDecimals) {
            throw new IllegalArgumentException(
                    "%s allows %d decimal places, but got %s".formatted(currency, decimals, amount.toPlainString()));
        }
    }

    /** {@code Money.of("1500000", "VND")}. */
    public static Money of(String amount, String currencyCode) {
        return new Money(new BigDecimal(amount), Currency.getInstance(currencyCode));
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("Cannot mix currencies: %s and %s".formatted(currency, other.currency));
        }
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency.getCurrencyCode();
    }
}
