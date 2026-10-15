package divbridge;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Sends chat to a Discord webhook so messages show up as the player (name + head). */
public class WebhookSender {

    private final DIVbridge plugin;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public WebhookSender(DIVbridge plugin) {
        this.plugin = plugin;
    }

    private String url() {
        return plugin.getConfig().getString("chat.webhook-url", "").trim();
    }

    public boolean isConfigured() {
        String u = url();
        return u.startsWith("https://discord.com/api/webhooks/")
                || u.startsWith("https://discordapp.com/api/webhooks/");
    }

    /** @param onFailure runs if Discord rejects the message (used to fall back to plain text) */
    public void send(String username, String avatarUrl, String content, Runnable onFailure) {
        if (content.length() > 1900) content = content.substring(0, 1900) + "…";
        if (username.length() > 80) username = username.substring(0, 80);

        StringBuilder json = new StringBuilder("{");
        json.append("\"content\":").append(quote(content));
        json.append(",\"username\":").append(quote(username));
        if (avatarUrl != null && !avatarUrl.isBlank()) {
            json.append(",\"avatar_url\":").append(quote(avatarUrl));
        }
        json.append(",\"allowed_mentions\":{\"parse\":[]}}"); // no @everyone / role / user pings

        HttpRequest request = HttpRequest.newBuilder(URI.create(url()))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                .build();

        http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((res, err) -> {
            if (err != null) {
                plugin.getLogger().warning("Could not reach the Discord webhook: " + err.getMessage());
                onFailure.run();
            } else if (res.statusCode() / 100 != 2) {
                // 429 = rate limited. 400 can happen for names Discord blocks (e.g. containing "discord").
                plugin.getLogger().warning("Discord webhook returned HTTP " + res.statusCode() + ": " + res.body());
                onFailure.run();
            }
        });
    }

    private static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }
}
