package com.pokernight.web.socket;

import com.pokernight.application.command.IdempotencyConflictException;
import com.pokernight.application.command.IdempotentCommandExecutor;
import com.pokernight.application.room.GameCommandResult;
import com.pokernight.application.room.RoomCode;
import com.pokernight.application.room.RoomGameService;
import com.pokernight.engine.model.GamePhase;
import com.pokernight.engine.model.PlayerAction;
import com.pokernight.engine.state.PublicGameState;
import com.pokernight.web.session.ApiPrincipal;
import com.pokernight.web.session.SessionRole;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameSocketControllerTest {
    @Test
    void duplicateActionIsAcknowledgedButExecutedAndBroadcastOnlyOnce() {
        RoomGameService games = mock(RoomGameService.class);
        GameplayEventRouter router = mock(GameplayEventRouter.class);
        BotTurnScheduler bots = mock(BotTurnScheduler.class);
        var controller = new GameSocketController(games, router, new IdempotentCommandExecutor(100), bots);
        var code = new RoomCode("ABC123");
        var principal = new StompPrincipal(new ApiPrincipal("p1", code, SessionRole.PLAYER));
        var result = result(code);
        when(games.submitAction(code, "p1", PlayerAction.CALL, null)).thenReturn(result);
        var command = new GameSocketModels.PlayerActionCommand("cmd-1", PlayerAction.CALL, null);

        var first = controller.action(code.value(), command, principal);
        var retry = controller.action(code.value(), command, principal);

        assertThat(first.replayed()).isFalse();
        assertThat(retry.replayed()).isTrue();
        verify(games, times(1)).submitAction(code, "p1", PlayerAction.CALL, null);
        verify(router, times(1)).route(result);
        verify(bots, times(1)).scheduleAfter(result);
    }

    @Test
    void sameIdCannotRepresentDifferentActions() {
        RoomGameService games = mock(RoomGameService.class);
        var controller = new GameSocketController(
            games, mock(GameplayEventRouter.class), new IdempotentCommandExecutor(100), mock(BotTurnScheduler.class)
        );
        var code = new RoomCode("ABC123");
        var principal = new StompPrincipal(new ApiPrincipal("p1", code, SessionRole.PLAYER));
        when(games.submitAction(code, "p1", PlayerAction.CALL, null)).thenReturn(result(code));
        controller.action(code.value(),
            new GameSocketModels.PlayerActionCommand("cmd-1", PlayerAction.CALL, null), principal);

        assertThatThrownBy(() -> controller.action(code.value(),
            new GameSocketModels.PlayerActionCommand("cmd-1", PlayerAction.FOLD, null), principal))
            .isInstanceOf(IdempotencyConflictException.class);
    }

    private static GameCommandResult result(RoomCode code) {
        return new GameCommandResult(code, new PublicGameState(
            GamePhase.PRE_FLOP, 1, true, List.of(), List.of(), java.util.Map.of(), 75, 50, 50, "p1"
        ), List.of());
    }
}
