package com.example.geoseek.engine

import com.example.geoseek.models.CardType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CardDuelTest {
    private fun card(
        name: String,
        hp: Int = 60,
        attack: Int = 15,
        type: CardType = CardType.MISC,
    ) = DuelCard(name, maxHp = hp, attack = attack, cardType = type)

    @Test
    fun neutralStrikeDamagesBothActiveCards() {
        val state = DuelState(listOf(card("Player")), listOf(card("Rival")))
        val next = state.play()

        assertEquals(45, next.playerActiveCard.currentHp)
        assertEquals(45, next.rivalActiveCard.currentHp)
        assertEquals(2, next.turn)
        assertTrue(next.statusMessage.contains("Neutral"))
    }

    @Test
    fun typeMatchupsScaleAndReportBothHits() {
        val state = DuelState(
            listOf(card("Tech", type = CardType.TECH)),
            listOf(card("Misc", type = CardType.MISC)),
        )
        val next = state.play()

        assertEquals(37, next.rivalActiveCard.currentHp)
        assertEquals(49, next.playerActiveCard.currentHp)
        assertTrue(next.statusMessage.contains("Super Effective"))
        assertTrue(next.statusMessage.contains("Not Very Effective"))
    }

    @Test
    fun knockoutAdvancesToNextAliveCardAndBattleContinues() {
        val state = DuelState(
            playerDeck = listOf(card("Player")),
            rivalDeck = listOf(card("First rival", hp = 10), card("Second rival")),
        )
        val next = state.play()

        assertTrue(next.rivalDeck[0].isKnockedOut)
        assertEquals(1, next.rivalActiveIndex)
        assertEquals("Second rival", next.rivalActiveCard.name)
        assertFalse(next.finished)
    }

    @Test
    fun playerKnockoutAdvancesActiveCardAndReceivesCounterattack() {
        val state = DuelState(
            playerDeck = listOf(card("First player", hp = 10), card("Second player")),
            rivalDeck = listOf(card("Rival")),
        )
        val next = state.play()

        assertTrue(next.playerDeck[0].isKnockedOut)
        assertEquals(1, next.playerActiveIndex)
        assertEquals(60, next.playerActiveCard.currentHp)
        assertFalse(next.finished)
    }

    @Test
    fun knockingOutFinalRivalCardWinsWithoutCounterattack() {
        val state = DuelState(
            playerDeck = listOf(card("Player", attack = 100)),
            rivalDeck = listOf(card("Last rival", hp = 20)),
        )
        val next = state.play()

        assertEquals(DuelOutcome.VICTORY, next.outcome)
        assertEquals(60, next.playerActiveCard.currentHp)
        assertTrue(next.finished)
        assertSame(next, next.play())
    }

    @Test
    fun swappingConsumesTurnAndRivalImmediatelyAttacks() {
        val state = DuelState(
            playerDeck = listOf(card("Active"), card("Backup", hp = 50)),
            rivalDeck = listOf(card("Rival")),
        )
        val next = state.swapCard(1)

        assertEquals(1, next.playerActiveIndex)
        assertEquals(60, next.playerDeck[0].currentHp)
        assertEquals(35, next.playerActiveCard.currentHp)
        assertEquals(60, next.rivalActiveCard.currentHp)
        assertEquals(2, next.turn)
        assertTrue(next.statusMessage.startsWith("Swapped to Backup!"))
        assertTrue(next.statusMessage.contains("Enemy attacked for 15 damage"))
    }

    @Test
    fun swappingToInvalidOrKnockedOutCardDoesNothing() {
        val state = DuelState(
            playerDeck = listOf(card("Active"), card("Knocked out").copy(currentHp = 0)),
            rivalDeck = listOf(card("Rival")),
        )

        assertSame(state, state.swapCard(-1))
        assertSame(state, state.swapCard(0))
        assertSame(state, state.swapCard(1))
        assertSame(state, state.swapCard(2))
    }

    @Test
    fun recoveryConsumesTurnAndCannotExceedMaxHp() {
        val state = DuelState(
            playerDeck = listOf(card("Player", hp = 60).copy(currentHp = 55)),
            rivalDeck = listOf(card("Rival")),
        )
        val next = state.play(recover = true)

        assertEquals(45, next.playerActiveCard.currentHp)
        assertEquals(60, next.rivalActiveCard.currentHp)
        assertEquals(1, next.recoveries)
        assertEquals(2, next.turn)
    }

    @Test
    fun exhaustedRecoveryDoesNotAdvanceTurn() {
        val state = DuelState(listOf(card("Player")), listOf(card("Rival")), recoveries = 0)
        assertSame(state, state.play(recover = true))
    }
}
