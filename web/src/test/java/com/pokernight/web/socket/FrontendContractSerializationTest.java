package com.pokernight.web.socket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pokernight.engine.event.GameEvent;
import com.pokernight.engine.hand.HoleCards;
import com.pokernight.engine.model.Card;
import com.pokernight.engine.model.GamePhase;
import com.pokernight.engine.model.Rank;
import com.pokernight.engine.model.Suit;
import com.pokernight.engine.state.PublicGameState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Guards field names and discriminators published in FRONTEND_CONTRACT.md. */
class FrontendContractSerializationTest {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @Test
    void serializesStableStatePrivateEventAndReceiptShapes() throws Exception {
        var state = new PublicGameState(
            GamePhase.PRE_FLOP, 3, true, List.of(), List.of(), java.util.Map.of(), 75, 50, 50, "p1");
        var stateJson = json.readTree(json.writeValueAsBytes(state));
        assertThat(stateJson.path("phase").asText()).isEqualTo("PRE_FLOP");
        assertThat(stateJson.path("handNumber").asLong()).isEqualTo(3);
        assertThat(stateJson.path("currentPlayerId").asText()).isEqualTo("p1");
        assertThat(stateJson.path("revealedHoleCards").isObject()).isTrue();

        var cards = new GameEvent.CardsDealt("p1", new HoleCards(
            new Card(Rank.ACE, Suit.SPADES), new Card(Rank.KING, Suit.HEARTS)));
        var eventJson = json.readTree(json.writeValueAsBytes(
            new SocketEnvelope("CARDS_DEALT", cards)));
        assertThat(eventJson.path("type").asText()).isEqualTo("CARDS_DEALT");
        assertThat(eventJson.at("/payload/cards/first/rank").asText()).isEqualTo("ACE");
        assertThat(eventJson.at("/payload/cards/second/suit").asText()).isEqualTo("HEARTS");

        var receiptJson = json.readTree(json.writeValueAsBytes(
            new GameSocketModels.CommandReceipt("command-1", true, "FLOP", 3)));
        assertThat(receiptJson.path("commandId").asText()).isEqualTo("command-1");
        assertThat(receiptJson.path("replayed").asBoolean()).isTrue();
        assertThat(receiptJson.path("phase").asText()).isEqualTo("FLOP");
    }
}
