package com.pokernight.web.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pokernight.application.room.RoomCode;
import com.pokernight.engine.model.PlayerAction;
import com.pokernight.web.session.InMemorySessionService;
import com.pokernight.web.socket.GameSocketController;
import com.pokernight.web.socket.GameSocketModels.GameControlCommand;
import com.pokernight.web.socket.GameSocketModels.PlayerActionCommand;
import com.pokernight.web.socket.StompPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Acceptance test crossing REST registration, sessions, commands, bots, engine, and recovery reads. */
@SpringBootTest
@AutoConfigureMockMvc
class BackendAcceptanceTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired InMemorySessionService sessions;
    @Autowired GameSocketController commands;

    @Test
    void createJoinApprovePlayWithBotRetryRefreshHistoryAndRoomIsolation() throws Exception {
        JsonNode first = createRoom("Host One", 1, 2);
        String code = first.path("roomCode").asText();
        String hostToken = first.path("hostToken").asText();
        JsonNode joined = join(code, "Alice");
        String playerId = joined.path("playerId").asText();
        String playerToken = joined.path("playerToken").asText();
        approve(code, playerId, hostToken, 0);

        var roomCode = new RoomCode(code);
        var host = new StompPrincipal(sessions.requireHost(bearer(hostToken), roomCode));
        var player = new StompPrincipal(sessions.requirePlayer(bearer(playerToken), roomCode));

        var started = commands.startHand(code, new GameControlCommand("start-hand-1"), host);
        var duplicateStart = commands.startHand(code, new GameControlCommand("start-hand-1"), host);
        assertThat(started.replayed()).isFalse();
        assertThat(duplicateStart.replayed()).isTrue();

        JsonNode refreshed = getMe(code, playerToken);
        assertThat(refreshed.at("/privateState/holeCards").isObject()).isTrue();
        assertThat(refreshed.at("/game/players").size()).isEqualTo(2);

        if (refreshed.at("/game/handInProgress").asBoolean()) {
            assertThat(refreshed.at("/privateState/validActions").toString())
                .contains(PlayerAction.FOLD.name());
            var folded = commands.action(code,
                new PlayerActionCommand("alice-fold-1", PlayerAction.FOLD, null), player);
            var duplicateFold = commands.action(code,
                new PlayerActionCommand("alice-fold-1", PlayerAction.FOLD, null), player);
            assertThat(folded.replayed()).isFalse();
            assertThat(duplicateFold.replayed()).isTrue();
        }

        mvc.perform(get("/api/v1/rooms/{code}/state", code))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.botPlayerIds.length()").value(1))
            .andExpect(jsonPath("$.game.handInProgress").value(false))
            .andExpect(jsonPath("$.game.phase").value("HAND_COMPLETE"));
        mvc.perform(get("/api/v1/rooms/{code}/history", code)
                .header("Authorization", bearer(playerToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entries[0].eventType").value("HAND_COMPLETED"));

        JsonNode second = createRoom("Host Two", 0, 2);
        mvc.perform(get("/api/v1/rooms/{code}/state", second.path("roomCode").asText()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.game.handNumber").value(0))
            .andExpect(jsonPath("$.game.phase").value("WAITING_FOR_PLAYERS"));
    }

    private JsonNode createRoom(String hostName, int bots, int maximumPlayers) throws Exception {
        String response = mvc.perform(post("/api/v1/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"hostName":"%s","startingChips":1000,"smallBlind":25,"bigBlind":50,
                     "maximumPlayers":%d,"botCount":%d}
                    """.formatted(hostName, maximumPlayers, bots)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    private JsonNode join(String code, String name) throws Exception {
        String response = mvc.perform(post("/api/v1/rooms/{code}/join-requests", code)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"%s\"}".formatted(name)))
            .andExpect(status().isAccepted())
            .andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    private void approve(String code, String playerId, String hostToken, int seat) throws Exception {
        mvc.perform(post("/api/v1/rooms/{code}/members/{playerId}/approval", code, playerId)
                .header("Authorization", bearer(hostToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"seatNumber\":%d}".formatted(seat)))
            .andExpect(status().isOk());
    }

    private JsonNode getMe(String code, String token) throws Exception {
        String response = mvc.perform(get("/api/v1/rooms/{code}/me", code)
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    private static String bearer(String token) { return "Bearer " + token; }
}
