package com.pokernight.web.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.pokernight.application.room.RoomCode;
import com.pokernight.application.room.RoomGameService;
import com.pokernight.web.session.InMemorySessionService;
import com.pokernight.engine.model.PlayerAction;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RoomApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RoomGameService games;
    @Autowired InMemorySessionService sessions;

    @Test
    void supportsPublicStateHostBootstrapAndPaginatedHistoryRecovery() throws Exception {
        JsonNode created = createRoom("Host", 2, 3);
        String code = created.get("roomCode").asText();
        String hostToken = created.get("hostToken").asText();
        String hostId = sessions.requireHost(bearer(hostToken), new RoomCode(code)).subjectId();
        games.startHand(new RoomCode(code), hostId);
        int botTurnGuard = 100;
        while (games.isBotTurn(new RoomCode(code)) && botTurnGuard-- > 0) {
            games.playPendingBotTurn(new RoomCode(code)).orElseThrow();
        }
        assertThat(botTurnGuard).isPositive();

        mvc.perform(get("/api/v1/rooms/{code}/state", code))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.roomCode").value(code))
            .andExpect(jsonPath("$.game.handInProgress").value(false));

        mvc.perform(get("/api/v1/rooms/{code}/host-bootstrap", code)
                .header("Authorization", bearer(hostToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.room.roomCode").value(code))
            .andExpect(jsonPath("$.lobby.roomCode").value(code))
            .andExpect(jsonPath("$.history.entries").isArray());

        String firstPageBody = mvc.perform(get("/api/v1/rooms/{code}/history", code)
                .param("limit", "2").header("Authorization", bearer(hostToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entries.length()").value(2))
            .andExpect(jsonPath("$.nextCursor").isNumber())
            .andReturn().getResponse().getContentAsString();
        long cursor = json.readTree(firstPageBody).get("nextCursor").asLong();
        String secondPageBody = mvc.perform(get("/api/v1/rooms/{code}/history", code)
                .param("limit", "100").param("cursor", String.valueOf(cursor))
                .header("Authorization", bearer(hostToken)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(secondPageBody).get("entries").get(0).get("sequence").asLong())
            .isLessThan(cursor);

        mvc.perform(get("/api/v1/rooms/{code}/history", code))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void preservesRoomAppearanceAcrossPublicHostAndPlayerBootstrap() throws Exception {
        String response = mvc.perform(post("/api/v1/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"hostName":"Host","startingChips":1000,"smallBlind":25,"bigBlind":50,
                     "maximumPlayers":6,"botCount":0,"themeId":"broadcast","cardPackId":"four-color"}
                    """))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        JsonNode created = json.readTree(response);
        String code = created.get("roomCode").asText();
        String hostToken = created.get("hostToken").asText();

        mvc.perform(get("/api/v1/rooms/{code}", code))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.config.appearance.themeId").value("broadcast"))
            .andExpect(jsonPath("$.config.appearance.cardPackId").value("four-color"));

        mvc.perform(get("/api/v1/rooms/{code}/host-bootstrap", code)
                .header("Authorization", bearer(hostToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.room.config.appearance.themeId").value("broadcast"))
            .andExpect(jsonPath("$.room.config.appearance.cardPackId").value("four-color"));

        JsonNode joined = join(code, "Alice");
        mvc.perform(get("/api/v1/rooms/{code}/me", code)
                .header("Authorization", bearer(joined.get("playerToken").asText())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.config.appearance.themeId").value("broadcast"))
            .andExpect(jsonPath("$.config.appearance.cardPackId").value("four-color"));
    }

    @Test
    void refreshDuringAPlayerTurnRestoresExactlyTheSameCardsAndLegalActions() throws Exception {
        JsonNode created = createRoom("Host", 1, 3);
        String code = created.get("roomCode").asText();
        String hostToken = created.get("hostToken").asText();
        JsonNode joined = join(code, "Alice");
        String playerId = joined.get("playerId").asText();
        String playerToken = joined.get("playerToken").asText();
        approve(code, playerId, hostToken, 0);
        String hostId = sessions.requireHost(bearer(hostToken), new RoomCode(code)).subjectId();
        games.startHand(new RoomCode(code), hostId);

        JsonNode beforeRefresh = json.readTree(mvc.perform(get("/api/v1/rooms/{code}/me", code)
                .header("Authorization", bearer(playerToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.privateState.holeCards").isMap())
            .andExpect(jsonPath("$.privateState.validActions").isNotEmpty())
            .andExpect(jsonPath("$.privateState.game.currentPlayerId").value(playerId))
            .andReturn().getResponse().getContentAsString());

        JsonNode afterRefresh = json.readTree(mvc.perform(get("/api/v1/rooms/{code}/me", code)
                .header("Authorization", bearer(playerToken)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());

        assertThat(afterRefresh.at("/privateState/holeCards"))
            .isEqualTo(beforeRefresh.at("/privateState/holeCards"));
        assertThat(afterRefresh.at("/privateState/validActions"))
            .isEqualTo(beforeRefresh.at("/privateState/validActions"));

        var privateState = games.getPrivateState(new RoomCode(code), playerId);
        PlayerAction action = privateState.validActions().contains(PlayerAction.CALL)
            ? PlayerAction.CALL : PlayerAction.CHECK;
        games.submitAction(new RoomCode(code), playerId, action, null);

        mvc.perform(get("/api/v1/rooms/{code}/me", code)
                .header("Authorization", bearer(playerToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.privateState.holeCards").isMap())
            .andExpect(jsonPath("$.privateState.game.handNumber").value(1));
    }

    @Test
    void supportsCreateJoinApproveRefreshAndKickFlow() throws Exception {
        JsonNode created = createRoom("Host");
        String code = created.get("roomCode").asText();
        String hostToken = created.get("hostToken").asText();

        mvc.perform(get("/api/v1/rooms/{code}", code))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.roomCode").value(code))
            .andExpect(jsonPath("$.members").doesNotExist())
            .andExpect(jsonPath("$.hostId").doesNotExist());

        JsonNode joined = json.readTree(mvc.perform(post("/api/v1/rooms/{code}/join-requests", code)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"Alice\"}"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andReturn().getResponse().getContentAsString());
        String playerId = joined.get("playerId").asText();
        String playerToken = joined.get("playerToken").asText();

        mvc.perform(get("/api/v1/rooms/{code}/me", code).header("Authorization", bearer(playerToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.playerId").value(playerId))
            .andExpect(jsonPath("$.status").value("PENDING"));

        mvc.perform(post("/api/v1/rooms/{code}/members/{playerId}/approval", code, playerId)
                .header("Authorization", bearer(hostToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"seatNumber\":2}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.members[0].status").value("APPROVED"))
            .andExpect(jsonPath("$.members[0].seatNumber").value(2));

        mvc.perform(get("/api/v1/rooms/{code}/me", code).header("Authorization", bearer(playerToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPROVED"))
            .andExpect(jsonPath("$.game.players[0].playerId").value(playerId))
            .andExpect(jsonPath("$.privateState.playerId").value(playerId))
            .andExpect(jsonPath("$.privateState.game.players[0].playerId").value(playerId));

        mvc.perform(delete("/api/v1/rooms/{code}/members/{playerId}", code, playerId)
                .header("Authorization", bearer(hostToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.members[0].status").value("KICKED"));

        mvc.perform(get("/api/v1/rooms/{code}/me", code).header("Authorization", bearer(playerToken)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void rejectionRevokesPendingPlayerSession() throws Exception {
        JsonNode created = createRoom("Host");
        String code = created.get("roomCode").asText();
        JsonNode joined = join(code, "Rejected player");

        mvc.perform(post("/api/v1/rooms/{code}/members/{playerId}/rejection",
                code, joined.get("playerId").asText())
                .header("Authorization", bearer(created.get("hostToken").asText())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.members[0].status").value("REJECTED"));

        mvc.perform(get("/api/v1/rooms/{code}/me", code)
                .header("Authorization", bearer(joined.get("playerToken").asText())))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void protectsHostLobbyAndAdministration() throws Exception {
        JsonNode created = createRoom("Host");
        String code = created.get("roomCode").asText();
        JsonNode joined = join(code, "Alice");

        mvc.perform(get("/api/v1/rooms/{code}/lobby", code))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        mvc.perform(get("/api/v1/rooms/{code}/lobby", code)
                .header("Authorization", bearer(joined.get("playerToken").asText())))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("NOT_HOST"));
    }

    @Test
    void preventsTokenFromCrossingRoomBoundary() throws Exception {
        JsonNode first = createRoom("First");
        JsonNode second = createRoom("Second");

        mvc.perform(get("/api/v1/rooms/{code}/lobby", second.get("roomCode").asText())
                .header("Authorization", bearer(first.get("hostToken").asText())))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("WRONG_ROOM"));
    }

    @Test
    void returnsStableValidationAndConflictProblems() throws Exception {
        mvc.perform(post("/api/v1/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"hostName":"","startingChips":10,"smallBlind":25,"bigBlind":50,
                     "maximumPlayers":9,"botCount":0}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        JsonNode created = createRoom("Host");
        String code = created.get("roomCode").asText();
        String hostToken = created.get("hostToken").asText();
        JsonNode first = join(code, "Alice");
        JsonNode second = join(code, "Bob");
        approve(code, first.get("playerId").asText(), hostToken, 0);

        mvc.perform(post("/api/v1/rooms/{code}/members/{playerId}/approval", code, second.get("playerId").asText())
                .header("Authorization", bearer(hostToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"seatNumber\":0}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SEAT_UNAVAILABLE"));
    }

    @Test
    void hostCanCloseRoom() throws Exception {
        JsonNode created = createRoom("Host");
        String code = created.get("roomCode").asText();

        mvc.perform(delete("/api/v1/rooms/{code}", code)
                .header("Authorization", bearer(created.get("hostToken").asText())))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/rooms/{code}", code))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));
    }

    private JsonNode createRoom(String hostName) throws Exception {
        return createRoom(hostName, 0, 6);
    }

    private JsonNode createRoom(String hostName, int botCount, int maximumPlayers) throws Exception {
        String body = """
            {"hostName":"%s","startingChips":1000,"smallBlind":25,"bigBlind":50,
             "maximumPlayers":%d,"botCount":%d}
            """.formatted(hostName, maximumPlayers, botCount);
        String response = mvc.perform(post("/api/v1/rooms")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.roomCode").isString())
            .andExpect(jsonPath("$.hostToken").isString())
            .andReturn().getResponse().getContentAsString();
        JsonNode result = json.readTree(response);
        assertThat(result.get("hostToken").asText()).hasSize(64);
        return result;
    }

    private JsonNode join(String code, String name) throws Exception {
        String response = mvc.perform(post("/api/v1/rooms/{code}/join-requests", code)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"%s\"}".formatted(name)))
            .andExpect(status().isAccepted())
            .andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    private void approve(String code, String playerId, String token, int seat) throws Exception {
        mvc.perform(post("/api/v1/rooms/{code}/members/{playerId}/approval", code, playerId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"seatNumber\":%d}".formatted(seat)))
            .andExpect(status().isOk());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
