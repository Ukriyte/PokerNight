package com.pokernight.web.socket;

import com.pokernight.application.command.CommandIdentity;
import com.pokernight.application.command.IdempotencyConflictException;
import com.pokernight.application.command.IdempotentCommandExecutor;
import com.pokernight.application.command.IdempotentCommandResult;
import com.pokernight.application.room.GameCommandResult;
import com.pokernight.application.room.GameplayOperationException;
import com.pokernight.application.room.RoomCode;
import com.pokernight.application.room.RoomGameService;
import com.pokernight.web.session.ApiPrincipal;
import com.pokernight.web.session.SessionRole;
import com.pokernight.web.socket.GameSocketModels.PlayerActionCommand;
import com.pokernight.web.socket.GameSocketModels.GameControlCommand;
import com.pokernight.web.socket.GameSocketModels.CommandReceipt;
import com.pokernight.web.socket.GameSocketModels.SocketError;
import jakarta.validation.Valid;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class GameSocketController {
    private final RoomGameService games;
    private final GameplayEventRouter router;
    private final IdempotentCommandExecutor commands;
    private final BotTurnScheduler bots;

    public GameSocketController(RoomGameService games, GameplayEventRouter router,
                                IdempotentCommandExecutor commands, BotTurnScheduler bots) {
        this.games = games;
        this.router = router;
        this.commands = commands;
        this.bots = bots;
    }

    @MessageMapping("/rooms/{code}/start-hand")
    @SendToUser(destinations = "/queue/commands", broadcast = false)
    public CommandReceipt startHand(@DestinationVariable("code") String code,
                                    @Valid GameControlCommand command, Principal principal) {
        ApiPrincipal actor = require(principal, new RoomCode(code), SessionRole.HOST);
        return execute(actor, command.commandId(), "START_HAND",
            () -> games.startHand(new RoomCode(code), actor.subjectId()));
    }

    @MessageMapping("/rooms/{code}/new-game")
    @SendToUser(destinations = "/queue/commands", broadcast = false)
    public CommandReceipt newGame(@DestinationVariable("code") String code,
                                  @Valid GameControlCommand command, Principal principal) {
        ApiPrincipal actor = require(principal, new RoomCode(code), SessionRole.HOST);
        return execute(actor, command.commandId(), "NEW_GAME",
            () -> games.startNewGame(new RoomCode(code), actor.subjectId()));
    }

    @MessageMapping("/rooms/{code}/actions")
    @SendToUser(destinations = "/queue/commands", broadcast = false)
    public CommandReceipt action(
        @DestinationVariable("code") String code,
        @Valid PlayerActionCommand command,
        Principal principal
    ) {
        ApiPrincipal actor = require(principal, new RoomCode(code), SessionRole.PLAYER);
        String fingerprint = "ACTION:" + command.action() + ":" + command.amount();
        return execute(actor, command.commandId(), fingerprint,
            () -> games.submitAction(new RoomCode(code), actor.subjectId(), command.action(), command.amount()));
    }

    @MessageExceptionHandler(GameplayOperationException.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public SocketError gameplayError(GameplayOperationException exception) {
        return new SocketError(exception.failure().name(), exception.getMessage());
    }

    @MessageExceptionHandler(IdempotencyConflictException.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public SocketError idempotencyError(IdempotencyConflictException exception) {
        return new SocketError("COMMAND_ID_REUSED", exception.getMessage());
    }

    @MessageExceptionHandler(MethodArgumentNotValidException.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public SocketError validationError(MethodArgumentNotValidException exception) {
        return new SocketError("VALIDATION_ERROR", "Command payload is invalid");
    }

    private CommandReceipt execute(ApiPrincipal actor, String commandId, String fingerprint,
                                   java.util.function.Supplier<GameCommandResult> operation) {
        IdempotentCommandResult outcome = commands.execute(
            new CommandIdentity(actor.roomCode(), actor.subjectId(), commandId), fingerprint, operation
        );
        if (!outcome.replayed()) route(outcome.result());
        return new CommandReceipt(commandId, outcome.replayed(),
            outcome.summary().phase(), outcome.summary().handNumber());
    }

    private void route(GameCommandResult result) {
        router.route(result);
        bots.scheduleAfter(result);
    }

    private ApiPrincipal require(Principal principal, RoomCode roomCode, SessionRole role) {
        if (!(principal instanceof StompPrincipal stomp)) {
            throw new IllegalStateException("authenticated STOMP principal is required");
        }
        ApiPrincipal actor = stomp.apiPrincipal();
        if (!actor.roomCode().equals(roomCode) || actor.role() != role) {
            throw new IllegalStateException("socket is not authorized for this command");
        }
        return actor;
    }
}
