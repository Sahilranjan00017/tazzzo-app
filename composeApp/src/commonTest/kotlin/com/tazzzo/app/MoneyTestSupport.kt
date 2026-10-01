package com.tazzzo.app

import com.tazzzo.app.data.model.Money

/** Whole rupees as [Money] — the way the pre-paise fixtures in this suite were written. */
fun r(rupees: Int): Money = Money.ofRupees(rupees.toLong())
