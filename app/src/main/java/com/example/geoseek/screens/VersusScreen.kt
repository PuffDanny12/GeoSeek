package com.example.geoseek.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.geoseek.data.Player
import com.example.geoseek.engine.DuelOutcome
import com.example.geoseek.engine.DuelState
import com.example.geoseek.engine.duelStats
import com.example.geoseek.engine.generateOpponentDeck
import com.example.geoseek.engine.toDuelCard
import com.example.geoseek.models.Card
import com.example.geoseek.models.GameObject
import com.example.geoseek.models.OBJECT_LIST

/** Card selection and a local, turn-based practice arena using only the player's discoveries. */
@Composable
fun VersusScreen(player: Player, onBack: () -> Unit, onFindCards: () -> Unit) {
    val owned = OBJECT_LIST.filter { it.name in player.cards }
    var selectedNames by rememberSaveable(player.username) { mutableStateOf(listOf("", "")) }
    var selectedSlot by rememberSaveable(player.username) { mutableIntStateOf(0) }
    var battling by rememberSaveable(player.username) { mutableStateOf(false) }
    val selectedDeck = selectedNames.map { name -> owned.firstOrNull { it.name == name } }
    val deckReady = selectedDeck.all { it != null }
    val accent = GeoColors.Danger

    BackHandler(enabled = battling) { battling = false }
    if (battling && deckReady) {
        DuelArena(selectedDeck.filterNotNull(), onBack = { battling = false })
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(140.dp),
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            ScreenHeader("Versus", "", accent, onBack, Icons.Versus)
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Panel(Modifier.fillMaxWidth(), border = accent) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedText("PRACTICE ARENA", GeoType.Title, color = accent)
                    Text(
                        "Choose two cards for a turn-based duel against a computer rival. Cards stay in your collection.",
                        style = GeoType.Body, color = GeoColors.TextSoft,
                    )
                    Text("Local practice • No cards or XP at stake", style = GeoType.Caption, color = GeoColors.Gold)
                }
            }
        }
        if (owned.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Panel(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedText("Your first fighter awaits", GeoType.Heading)
                        Text("Find an object in Free Look to unlock its card, then return here to battle.",
                            style = GeoType.Body, color = GeoColors.TextSoft)
                        PixelButton("Find cards", onFindCards, Modifier.fillMaxWidth(), color = accent, icon = Icons.Explore)
                    }
                }
            }
        } else {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedText("Choose your deck", GeoType.Heading)
                    selectedNames.forEachIndexed { index, name ->
                        PixelButton(
                            "Card ${index + 1}: ${name.ifEmpty { "Choose a card" }}",
                            { selectedSlot = index },
                            Modifier.fillMaxWidth(),
                            color = if (selectedSlot == index) accent else GeoColors.Gold,
                        )
                    }
                    Text("Tap a card below to fill the selected slot. Duplicate cards are allowed.",
                        style = GeoType.Caption, color = GeoColors.TextSoft)
                    PixelButton(
                        if (deckReady) "Enter arena" else "Choose both cards below",
                        { battling = true },
                        Modifier.fillMaxWidth(),
                        color = accent, enabled = deckReady, icon = Icons.Versus,
                    )
                }
            }
            items(owned, key = { it.name }) { card ->
                val isSelected = card.name in selectedNames
                Column(
                    Modifier.semantics { selected = isSelected },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    TradingCard(
                        card, discovered = true,
                        modifier = if (isSelected) Modifier.pixelPanel(border = accent).padding(6.dp) else Modifier.padding(6.dp),
                        onClick = {
                            selectedNames = selectedNames.toMutableList().also { it[selectedSlot] = card.name }
                            if (selectedSlot == 0 && selectedNames[1].isEmpty()) selectedSlot = 1
                        },
                    )
                    Text(if (isSelected) "SELECTED" else "Tap to select",
                        style = GeoType.Caption, color = if (isSelected) accent else GeoColors.TextSoft)
                }
            }
        }
    }
}

@Composable
private fun DuelArena(deck: List<GameObject>, onBack: () -> Unit) {
    val availableCards = remember { OBJECT_LIST.map { Card(obj = it, xpValue = 0) } }
    val playerCards = remember(deck) { deck.map { Card(obj = it, xpValue = 0) } }
    val opponentCards = remember(deck) {
        generateOpponentDeck(playerCards, availableCards, matchRarity = true)
    }
    val playerBattleDeck = remember(deck) { playerCards.map { it.obj.duelStats().toDuelCard() } }
    val opponentBattleDeck = remember(deck) { opponentCards.map { it.obj.duelStats().toDuelCard() } }
    val initialState = remember(deck) {
        DuelState(playerDeck = playerBattleDeck, rivalDeck = opponentBattleDeck)
    }
    var state by remember(deck) { mutableStateOf(initialState) }
    val accent = GeoColors.Danger

    fun play(recover: Boolean) {
        state = state.play(recover)
    }

    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader("Card duel", "Local practice • Rarity-matched rival", accent, onBack, Icons.Versus)
        Row(
            Modifier.widthIn(max = 600.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val playerCard = deck[state.playerActiveIndex]
            val rivalCard = opponentCards[state.rivalActiveIndex].obj
            BattleCard(
                playerCard, "YOU", state.playerActiveCard.currentHp, state.playerActiveCard.maxHp,
                GeoColors.Success, Modifier.weight(1f),
            )
            OutlinedText("VS", GeoType.Heading, color = accent)
            BattleCard(
                rivalCard, "COMPUTER", state.rivalActiveCard.currentHp, state.rivalActiveCard.maxHp,
                accent, Modifier.weight(1f),
            )
        }
        Panel(Modifier.widthIn(max = 600.dp).fillMaxWidth(), border = accent) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedText(
                    if (state.finished) {
                        if (state.outcome == DuelOutcome.VICTORY) "VICTORY!" else "DEFEAT"
                    } else {
                        "TURN ${state.turn}"
                    },
                    GeoType.Title, color = if (state.outcome == DuelOutcome.VICTORY) GeoColors.Gold else accent,
                )
                Text(state.statusMessage.ifEmpty { "Your move! Strike, recover, or switch cards." },
                    style = GeoType.Body, color = GeoColors.TextSoft)
                if (!state.finished) {
                    PixelButton("Strike • ${state.playerActiveCard.attack} base damage", { play(false) },
                        Modifier.fillMaxWidth(), color = accent)
                    PixelButton("Recover • ${state.recoveries} left", { play(true) },
                        Modifier.fillMaxWidth(), color = GeoColors.Success,
                        enabled = state.recoveries > 0 && state.playerActiveCard.currentHp < state.playerActiveCard.maxHp)
                    state.playerDeck.forEachIndexed { index, card ->
                        if (index != state.playerActiveIndex && !card.isKnockedOut) {
                            PixelButton(
                                "Switch to card ${index + 1}: ${card.name} (${card.currentHp}/${card.maxHp} HP)",
                                { state = state.swapCard(index) },
                                Modifier.fillMaxWidth(),
                                color = GeoColors.Gold,
                            )
                        }
                    }
                    Text("You act first, then the rival attacks. Recovery and switching use your turn.",
                        style = GeoType.Caption, color = GeoColors.TextSoft)
                } else {
                    PixelButton("Rematch", {
                        state = initialState.copy(statusMessage = "New duel. Your move!")
                    }, Modifier.fillMaxWidth(), color = accent)
                    PixelButton("Choose another card", onBack, Modifier.fillMaxWidth(), color = GeoColors.Gold)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun BattleCard(
    card: GameObject, owner: String, health: Int, maxHealth: Int,
    accent: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(owner, style = GeoType.Caption, color = accent)
        TradingCard(card, discovered = true, modifier = Modifier.fillMaxWidth())
        PixelBar(health / maxHealth.toFloat(), accent, Modifier.fillMaxWidth())
        Text("$health / $maxHealth HP", style = GeoType.Caption, color = GeoColors.TextSoft)
    }
}
