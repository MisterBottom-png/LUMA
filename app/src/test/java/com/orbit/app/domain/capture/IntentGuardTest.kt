package com.orbit.app.domain.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** F03: a negated, conditional or quoted thought is never acted on, in English, Estonian or Russian. */
class IntentGuardTest {
    @Test
    fun negationsConditionsAndQuotesNeedTheUser() {
        listOf(
            "don't remind me to pay tomorrow at 9",
            "Don’t remind me to pay tomorrow at 9",
            "do not call the bank",
            "I no longer need to renew the passport",
            "never book that hotel again",
            "if it rains remind me to close the windows",
            "remind me unless the parcel came",
            "she said \"remind me at 9\"",
            "„tuleta meelde“ kirjutas ta",
            "ära tuleta mulle meelde",
            "pole vaja arstile helistada",
            "kui sajab, tuleta meelde aknad sulgeda",
            "не напоминай мне завтра",
            "если пойдёт дождь, напомни закрыть окна",
            "больше не нужно звонить в банк",
        ).forEach { assertTrue(it, IntentGuard.needsTheUser(it)) }
    }

    @Test
    fun plainRequestsAndDontForgetAreNotBlocked() {
        listOf(
            "remind me tomorrow at 9 to water the plants",
            "don't forget to buy milk",
            "Do not forget the dentist on Friday",
            "tuleta mulle homme kell 9 meelde lilli kasta",
            "ära unusta piima osta",
            "напомни мне завтра в 9 полить цветы",
            "не забудь купить молоко",
            "Buy dog food",
        ).forEach { assertFalse(it, IntentGuard.needsTheUser(it)) }
    }
}
