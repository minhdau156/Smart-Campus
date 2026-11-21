package edu.campus.kernel.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.math.BigDecimal;
import java.util.Currency;
import org.junit.jupiter.api.Test;

class MoneyTest {

    private static final Currency VND = Currency.getInstance("VND");
    private static final Currency USD = Currency.getInstance("USD");

    @Test
    void vndHasNoDecimalPlaces() {
        Money fee = Money.of("1500000", "VND");

        assertThat(fee.amount()).isEqualByComparingTo("1500000");
        assertThat(fee.amount().scale()).isZero();
        assertThat(fee.currency()).isEqualTo(VND);
    }

    @Test
    void vndWithDecimalsIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Money.of("1000.5", "VND"))
                .withMessageContaining("VND")
                .withMessageContaining("0 decimal");
    }

    @Test
    void usdWithThreeDecimalsIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> Money.of("1.005", "USD"));
    }

    @Test
    void trailingZerosAreAllowedAndNormalized() {
        assertThat(Money.of("1000.00", "VND")).isEqualTo(Money.of("1000", "VND"));
        assertThat(Money.of("2.5", "USD").amount().scale()).isEqualTo(2);
    }

    @Test
    void equalsIgnoresScale() {
        Money a = new Money(new BigDecimal("10.5"), USD);
        Money b = new Money(new BigDecimal("10.50"), USD);

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }

    @Test
    void sameAmountInDifferentCurrenciesIsNotEqual() {
        assertThat(Money.of("10", "USD")).isNotEqualTo(Money.of("10", "VND"));
    }

    @Test
    void nullCurrencyIsRejected() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Money(BigDecimal.ONE, null))
                .withMessageContaining("currency");
    }

    @Test
    void nullAmountIsRejected() {
        assertThatNullPointerException().isThrownBy(() -> new Money(null, VND)).withMessageContaining("amount");
    }

    @Test
    void currencyWithoutDecimalRulesIsRejected() {
        // XAU (gold) is an ISO code but not money for us: it has no default number of decimals.
        assertThatIllegalArgumentException().isThrownBy(() -> Money.of("1", "XAU"));
    }

    @Test
    void plusAddsTheSameCurrency() {
        assertThat(Money.of("1000", "VND").plus(Money.of("500", "VND"))).isEqualTo(Money.of("1500", "VND"));
    }

    @Test
    void minusSubtractsAndMayGoNegative() {
        assertThat(Money.of("10.00", "USD").minus(Money.of("2.50", "USD"))).isEqualTo(Money.of("7.50", "USD"));
        assertThat(Money.of("1", "USD").minus(Money.of("3", "USD"))).isEqualTo(Money.of("-2", "USD"));
    }

    @Test
    void plusWithMixedCurrenciesIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Money.of("1", "USD").plus(Money.of("1", "VND")))
                .withMessageContaining("USD")
                .withMessageContaining("VND");
    }

    @Test
    void minusWithMixedCurrenciesIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Money.of("1", "USD").minus(Money.of("1", "VND")));
    }

    @Test
    void zeroHasTheCurrencyScale() {
        Money zero = Money.zero(USD);

        assertThat(zero.amount()).isEqualByComparingTo("0");
        assertThat(zero.amount().scale()).isEqualTo(2);
    }

    @Test
    void toStringShowsAmountAndCurrency() {
        assertThat(Money.of("1500000", "VND")).hasToString("1500000 VND");
    }
}
