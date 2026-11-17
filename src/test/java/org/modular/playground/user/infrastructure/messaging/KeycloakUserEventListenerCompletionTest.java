package org.modular.playground.user.infrastructure.messaging;

import jakarta.json.bind.Jsonb;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modular.playground.user.core.domain.UserImpl;
import org.modular.playground.user.core.usecases.UserService;
import org.modular.playground.user.infrastructure.persistence.postgres.mapper.UserMapper;
import org.modular.playground.user.web.dto.UserCreatedEvent;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KeycloakUserEventListenerCompletionTest {
    private static final String PAYLOAD = "{}";

    @Mock Jsonb jsonb;
    @Mock UserMapper userMapper;
    @Mock UserService userService;
    @Mock Emitter<UserCreatedEvent> userCreatedEmitter;
    @Mock UserImpl user;
    @InjectMocks KeycloakUserEventListener listener;

    private byte[] payload() {
        return PAYLOAD.getBytes(StandardCharsets.UTF_8);
    }

    private void arrangeValidEvent() {
        KeycloakEventDTO event = new KeycloakEventDTO();
        event.setDetails(new KeycloakEventDTO.Details());
        when(jsonb.fromJson(PAYLOAD, KeycloakEventDTO.class)).thenReturn(event);
        when(userMapper.toDomain(event)).thenReturn(user);
    }

    @Test
    void waitsForPublicationCompletion() {
        arrangeValidEvent();
        when(userService.createUserProfile(user)).thenReturn(user);
        CompletableFuture<Void> publication = new CompletableFuture<>();
        when(userCreatedEmitter.send(any(UserCreatedEvent.class))).thenReturn(publication);

        var completion = listener.processUserEvent(payload()).toCompletableFuture();

        assertFalse(completion.isDone());
        publication.complete(null);
        assertDoesNotThrow(completion::join);
    }

    @Test
    void propagatesAsynchronousPublicationFailure() {
        arrangeValidEvent();
        when(userService.createUserProfile(user)).thenReturn(user);
        CompletableFuture<Void> publication = new CompletableFuture<>();
        when(userCreatedEmitter.send(any(UserCreatedEvent.class))).thenReturn(publication);
        var completion = listener.processUserEvent(payload()).toCompletableFuture();
        RuntimeException failure = new RuntimeException("Broker unavailable");

        publication.completeExceptionally(failure);

        assertSame(failure, assertThrows(CompletionException.class, completion::join).getCause());
    }

    @Test
    void returnsFailedStageWhenPersistenceFails() {
        arrangeValidEvent();
        RuntimeException failure = new RuntimeException("Database unavailable");
        when(userService.createUserProfile(user)).thenThrow(failure);

        var completion = listener.processUserEvent(payload()).toCompletableFuture();

        assertSame(failure, assertThrows(CompletionException.class, completion::join).getCause());
        verifyNoInteractions(userCreatedEmitter);
    }

    @Test
    void returnsFailedStageForMissingDetails() {
        when(jsonb.fromJson(PAYLOAD, KeycloakEventDTO.class)).thenReturn(new KeycloakEventDTO());

        var completion = listener.processUserEvent(payload()).toCompletableFuture();

        assertInstanceOf(IllegalArgumentException.class,
                assertThrows(CompletionException.class, completion::join).getCause());
        verifyNoInteractions(userService, userCreatedEmitter);
    }

    @Test
    void returnsFailedStageForInvalidJson() {
        RuntimeException failure = new IllegalArgumentException("Invalid JSON");
        when(jsonb.fromJson(PAYLOAD, KeycloakEventDTO.class)).thenThrow(failure);

        var completion = listener.processUserEvent(payload()).toCompletableFuture();

        assertSame(failure, assertThrows(CompletionException.class, completion::join).getCause());
        verifyNoInteractions(userService, userCreatedEmitter);
    }
}
