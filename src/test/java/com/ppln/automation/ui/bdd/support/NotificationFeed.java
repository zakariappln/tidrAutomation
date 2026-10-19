package com.ppln.automation.ui.bdd.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.WebSocket;
import com.microsoft.playwright.WebSocketFrame;
import com.ppln.automation.core.utils.JsonUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Listens to the bell's socket.io namespace {@code /notifysocket} on one page. Celery pushes {@code
 * notify} once a minute ({@code alert_notification_server}, tasks/celery_worker.py:633) with {@code
 * [{html[], total, id[], is_notification_bell_enable}]}: {@code id} are the newest (at most 10)
 * unviewed threats. Must be attached before the page loads, because the socket is opened by
 * global.js on load.
 */
public final class NotificationFeed {

    /** One {@code notify} push. */
    public record Push(int total, Set<Long> ids, boolean bellFlag) {}

    private static final String EVENT_PREFIX = "/notifysocket,";

    private final Page page;
    private final List<Push> pushes = new ArrayList<>();

    private NotificationFeed(Page page) {
        this.page = page;
        page.onWebSocket(this::listen);
    }

    public static NotificationFeed attach(Page page) {
        return new NotificationFeed(page);
    }

    private void listen(WebSocket ws) {
        ws.onFrameReceived(this::onFrame);
    }

    private void onFrame(WebSocketFrame frame) {
        String text = frame.text();
        if (text == null) {
            return;
        }
        int at = text.indexOf(EVENT_PREFIX);
        if (at < 0) {
            return;
        }
        JsonNode message;
        try {
            message = JsonUtils.readTree(text.substring(at + EVENT_PREFIX.length()));
        } catch (RuntimeException e) {
            return;
        }
        if (!"notify".equals(message.path(0).asText())) {
            return;
        }
        JsonNode data = message.path(1).path("data").path(0);
        Set<Long> ids = new TreeSet<>();
        data.path("id").forEach(n -> ids.add(n.asLong()));
        synchronized (pushes) {
            pushes.add(
                    new Push(
                            data.path("total").asInt(),
                            ids,
                            data.path("is_notification_bell_enable").asBoolean()));
        }
    }

    public int count() {
        synchronized (pushes) {
            return pushes.size();
        }
    }

    /** The first push received so far, if any (no waiting). */
    public java.util.Optional<Push> first() {
        synchronized (pushes) {
            return pushes.isEmpty()
                    ? java.util.Optional.empty()
                    : java.util.Optional.of(pushes.get(0));
        }
    }

    /** Waits up to {@code timeoutMs} for push number {@code index} (0-based) and returns it. */
    public Push awaitPush(int index, double timeoutMs) {
        page.waitForCondition(
                () -> count() > index, new Page.WaitForConditionOptions().setTimeout(timeoutMs));
        synchronized (pushes) {
            return pushes.get(index);
        }
    }
}
