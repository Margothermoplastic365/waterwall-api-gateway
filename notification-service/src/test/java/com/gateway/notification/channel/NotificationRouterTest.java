package com.gateway.notification.channel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationRouterTest {

    // ── route ───────────────────────────────────────────────────────────

    @Test
    void route_fansOutToAllEnabledChannels() {
        NotificationChannel slack = mockChannel("slack", true);
        NotificationChannel teams = mockChannel("teams", true);

        NotificationRouter router = new NotificationRouter(List.of(slack, teams));

        Map<String, Object> metadata = Map.of("key", "val");
        router.route("Alert", "Latency high", "ERROR", metadata);

        verify(slack).send("Alert", "Latency high", "ERROR", metadata);
        verify(teams).send("Alert", "Latency high", "ERROR", metadata);
    }

    @Test
    void route_skipsDisabledChannels() {
        NotificationChannel enabled = mockChannel("slack", true);
        NotificationChannel disabled = mockChannel("teams", false);

        NotificationRouter router = new NotificationRouter(List.of(enabled, disabled));

        router.route("Title", "Body", "INFO", Map.of());

        verify(enabled).send(anyString(), anyString(), anyString(), anyMap());
        verify(disabled, never()).send(anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    void route_exceptionInOneChannel_doesNotAffectOthers() {
        NotificationChannel failing = mockChannel("slack", true);
        doThrow(new RuntimeException("Slack API down"))
                .when(failing).send(anyString(), anyString(), anyString(), anyMap());
        NotificationChannel working = mockChannel("teams", true);

        NotificationRouter router = new NotificationRouter(List.of(failing, working));

        router.route("Title", "Body", "WARN", Map.of());

        verify(failing).send(anyString(), anyString(), anyString(), anyMap());
        verify(working).send("Title", "Body", "WARN", Map.of());
    }

    @Test
    void route_noChannels_handlesGracefully() {
        NotificationRouter router = new NotificationRouter(List.of());
        // Should not throw
        router.route("Title", "Body", "INFO", Map.of());
    }

    @Test
    void route_allDisabled_nothingSent() {
        NotificationChannel c1 = mockChannel("slack", false);
        NotificationChannel c2 = mockChannel("teams", false);

        NotificationRouter router = new NotificationRouter(List.of(c1, c2));

        router.route("Title", "Body", "INFO", Map.of());

        verify(c1, never()).send(anyString(), anyString(), anyString(), anyMap());
        verify(c2, never()).send(anyString(), anyString(), anyString(), anyMap());
    }

    // ── routeTo ─────────────────────────────────────────────────────────

    @Test
    void routeTo_onlyRoutesToNamedChannels() {
        NotificationChannel slack = mockChannel("slack", true);
        NotificationChannel teams = mockChannel("teams", true);
        NotificationChannel webhook = mockChannel("webhook", true);

        NotificationRouter router = new NotificationRouter(List.of(slack, teams, webhook));

        router.routeTo(List.of("slack", "webhook"), "Title", "Body", "INFO", Map.of());

        verify(slack).send("Title", "Body", "INFO", Map.of());
        verify(webhook).send("Title", "Body", "INFO", Map.of());
        verify(teams, never()).send(anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    void routeTo_skipsDisabledEvenIfNamed() {
        NotificationChannel slack = mockChannel("slack", false);
        NotificationChannel teams = mockChannel("teams", true);

        NotificationRouter router = new NotificationRouter(List.of(slack, teams));

        router.routeTo(List.of("slack", "teams"), "Title", "Body", "CRITICAL", Map.of());

        verify(slack, never()).send(anyString(), anyString(), anyString(), anyMap());
        verify(teams).send("Title", "Body", "CRITICAL", Map.of());
    }

    @Test
    void routeTo_exceptionInOneChannel_doesNotAffectOthers() {
        NotificationChannel slack = mockChannel("slack", true);
        doThrow(new RuntimeException("fail")).when(slack)
                .send(anyString(), anyString(), anyString(), anyMap());
        NotificationChannel teams = mockChannel("teams", true);

        NotificationRouter router = new NotificationRouter(List.of(slack, teams));

        router.routeTo(List.of("slack", "teams"), "T", "B", "INFO", Map.of());

        verify(slack).send(anyString(), anyString(), anyString(), anyMap());
        verify(teams).send("T", "B", "INFO", Map.of());
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private NotificationChannel mockChannel(String name, boolean enabled) {
        NotificationChannel channel = mock(NotificationChannel.class);
        lenient().when(channel.channelName()).thenReturn(name);
        lenient().when(channel.isEnabled()).thenReturn(enabled);
        return channel;
    }
}
