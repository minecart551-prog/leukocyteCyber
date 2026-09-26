package xyz.nucleoid.leukocyte.build;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public final class AccountVerifier {
    public enum Result {
        OFFICIAL,
        CRACKED,
        UNKNOWN
    }

    private static final Pattern PREMIUM_NAME = Pattern.compile("[a-zA-Z0-9_]{3,16}");
    private static final Map<String, Result> CACHE = new ConcurrentHashMap<>();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        var thread = new Thread(r, "leukocyte-account-verifier");
        thread.setDaemon(true);
        return thread;
    });
    private static volatile HttpClient httpClient;

    private AccountVerifier() {
    }

    public static CompletableFuture<Result> verify(String username) {
        String key = username.toLowerCase();

        if (!PREMIUM_NAME.matcher(username).matches()) {
            return CompletableFuture.completedFuture(Result.CRACKED);
        }

        Result cached = CACHE.get(key);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        return CompletableFuture.supplyAsync(() -> doVerify(key), EXECUTOR)
            .thenApply(result -> {
                if (result != Result.UNKNOWN) {
                    CACHE.put(key, result);
                }
                return result;
            })
            .exceptionally(ex -> Result.UNKNOWN);
    }

    private static Result doVerify(String username) {
        try {
            var request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + username))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

            var response = httpClient().send(request, HttpResponse.BodyHandlers.ofString());

            return switch (response.statusCode()) {
                case 200 -> parseProfile(response.body());
                case 404 -> Result.CRACKED;
                default -> Result.UNKNOWN;
            };
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.UNKNOWN;
        } catch (Exception e) {
            return Result.UNKNOWN;
        }
    }

    private static Result parseProfile(String body) {
        try {
            var json = JsonParser.parseString(body);
            if (json.isJsonObject()) {
                var obj = (JsonObject) json;
                if (obj.has("id") && obj.has("name")) {
                    return Result.OFFICIAL;
                }
            }
            return Result.UNKNOWN;
        } catch (Exception e) {
            return Result.UNKNOWN;
        }
    }

    private static HttpClient httpClient() {
        if (httpClient == null) {
            synchronized (AccountVerifier.class) {
                if (httpClient == null) {
                    httpClient = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .build();
                }
            }
        }
        return httpClient;
    }
}
