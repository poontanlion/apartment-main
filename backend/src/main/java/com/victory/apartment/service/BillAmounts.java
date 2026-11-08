package com.victory.apartment.service;

/** How amounts missing from a stored bill are read. */
public final class BillAmounts {

    private static final double DEFAULT_COMMON_FEE = 300;

    private BillAmounts() {}

    public static double commonFeeOrDefault(Double commonFee) {
        return commonFee != null ? commonFee : DEFAULT_COMMON_FEE;
    }

    /** Older bills stored no rent, but their total already includes the rent charged then. */
    public static double rentInsideTotal(Double total, Double water, Double electric, Double commonFee) {
        double charges = orZero(water) + orZero(electric) + commonFeeOrDefault(commonFee);
        return Math.max(0, orZero(total) - charges);
    }

    private static double orZero(Double amount) {
        return amount != null ? amount : 0;
    }
}
