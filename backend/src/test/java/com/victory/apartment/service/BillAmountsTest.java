package com.victory.apartment.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BillAmountsTest {

    @Test
    void rentInsideTotal_subtractsCharges() {
        assertThat(BillAmounts.rentInsideTotal(6000.0, 100.0, 200.0, 300.0)).isEqualTo(5400.0);
    }

    @Test
    void rentInsideTotal_missingCommonFeeUsesDefault() {
        assertThat(BillAmounts.rentInsideTotal(6000.0, 100.0, 200.0, null)).isEqualTo(5400.0);
    }

    @Test
    void rentInsideTotal_recordedZeroCommonFeeStaysZero() {
        assertThat(BillAmounts.rentInsideTotal(6000.0, 100.0, 200.0, 0.0)).isEqualTo(5700.0);
    }

    @Test
    void rentInsideTotal_missingAmountsNeverGoNegative() {
        assertThat(BillAmounts.rentInsideTotal(null, null, null, null)).isEqualTo(0.0);
    }

    @Test
    void rentInsideTotal_chargesAboveTotalGiveZero() {
        assertThat(BillAmounts.rentInsideTotal(100.0, 500.0, 0.0, 0.0)).isEqualTo(0.0);
    }

    @Test
    void commonFeeOrDefault_usesDefaultOnlyWhenMissing() {
        assertThat(BillAmounts.commonFeeOrDefault(null)).isEqualTo(300.0);
        assertThat(BillAmounts.commonFeeOrDefault(0.0)).isEqualTo(0.0);
        assertThat(BillAmounts.commonFeeOrDefault(450.0)).isEqualTo(450.0);
    }
}
