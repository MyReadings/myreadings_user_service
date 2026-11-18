package org.modular.playground.user.infrastructure.messaging;

import org.modular.playground.user.core.domain.User;
import org.modular.playground.user.core.usecases.UserService;
import org.modular.playground.user.infrastructure.persistence.postgres.mapper.UserMapper;
import org.modular.playground.user.infrastructure.persistence.postgres.mapper.UserMapperImpl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.modular.playground.user.web.dto.UserCreatedEvent;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;

@ExtendWith(MockitoExtension.class)
public class KeycloakUserEventListenerTest {

    @InjectMocks 
    KeycloakUserEventListener keycloakUserEventListener;

    @Spy
    UserMapper userMapper = new UserMapperImpl();

    @Mock 
    UserService userService;
    
    @Spy
    Jsonb jsonb = JsonbBuilder.create();

    @Mock
    Emitter<UserCreatedEvent> userCreatedEmitter;

    @AfterEach
    void closeJsonb() throws Exception { jsonb.close(); }

    @Test
    public void testProcessValidUserEvent() {
        String validEventJson = "{\n" +
                "  \"@class\" : \"com.github.aznamier.keycloak.event.provider.EventClientNotificationMqMsg\",\n" +
                "  \"time\" : 1742495768687,\n" +
                "  \"type\" : \"REGISTER\",\n" +
                "  \"realmId\" : \"my-readings\",\n" +
                "  \"clientId\" : \"myreadings-client\",\n" +
                "  \"userId\" : \"a9a2e247-f06f-4023-82e5-a0a07cee5c63\",\n" +
                "  \"ipAddress\" : \"10.89.1.7\",\n" +
                "  \"details\" : {\n" +
                "    \"auth_method\" : \"openid-connect\",\n" +
                "    \"auth_type\" : \"code\",\n" +
                "    \"register_method\" : \"form\",\n" +
                "    \"last_name\" : \"Rossi\",\n" +
                "    \"redirect_uri\" : \"http://localhost:3000/\",\n" +
                "    \"first_name\" : \"Daniele\",\n" +
                "    \"code_id\" : \"d4aa538e-5025-4ac2-a149-dbeff00090e1\",\n" +
                "    \"email\" : \"drossi@redhat.com\",\n" +
                "    \"username\" : \"drossi\"\n" +
                "  }\n" +
                "}";

        when(userService.createUserProfile(any(User.class))).thenAnswer(call -> call.getArgument(0));
        when(userCreatedEmitter.send(any(UserCreatedEvent.class))).thenReturn(CompletableFuture.completedFuture(null));
        keycloakUserEventListener.processUserEvent(validEventJson.getBytes(StandardCharsets.UTF_8)).toCompletableFuture().join();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userService, times(1)).createUserProfile(userCaptor.capture());
        User capturedUser = userCaptor.getValue();

        assertEquals("drossi", capturedUser.getUsername());
        assertEquals("drossi@redhat.com", capturedUser.getEmail());
        assertEquals("Daniele", capturedUser.getFirstName());
        assertEquals("Rossi", capturedUser.getLastName());
        assertNotNull(capturedUser.getKeycloakUserId());
    }

    @Test
    public void testProcessEventWithoutDetails() {
        String eventWithoutDetails = "{\"userId\":\"some-uuid\",\"type\":\"REGISTER\"}";

        assertThrows(CompletionException.class, () -> keycloakUserEventListener
                .processUserEvent(eventWithoutDetails.getBytes(StandardCharsets.UTF_8)).toCompletableFuture().join());

        verify(userService, never()).createUserProfile(any(User.class));
    }

    @Test
    public void testProcessCorruptEvent() {
        String corruptEvent = "this is not valid json";

        assertThrows(CompletionException.class, () -> keycloakUserEventListener
                .processUserEvent(corruptEvent.getBytes(StandardCharsets.UTF_8)).toCompletableFuture().join());

        verify(userService, never()).createUserProfile(any(User.class));
    }
}