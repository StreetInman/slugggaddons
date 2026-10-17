package io.github.streetinman.skyblockpv.core.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Talks to the Hypixel public API, or to a proxy with the same paths.
 *
 * <p>While developing, {@code baseUrl} is {@code https://api.hypixel.net} and the key is the
 * developer's own. Published builds point {@code baseUrl} at the project's proxy, which adds
 * the production key server-side, so the key supplier returns null and no key ships in the jar.
 */
public final class HypixelClient {
	public static final String HYPIXEL_BASE_URL = "https://api.hypixel.net";

	private final HttpClient http;
	private final Supplier<String> baseUrl;
	private final Supplier<String> apiKey;
	private final TtlCache<String, JsonObject> profiles = new TtlCache<>(Duration.ofMinutes(5));
	private final TtlCache<String, JsonObject> resources = new TtlCache<>(Duration.ofHours(6));

	public HypixelClient(HttpClient http, Supplier<String> baseUrl, Supplier<String> apiKey) {
		this.http = http;
		this.baseUrl = baseUrl;
		this.apiKey = apiKey;
	}

	/** {@code /v2/skyblock/profiles}: every profile the player is in, with members' data. */
	public CompletableFuture<JsonObject> skyblockProfiles(String uuid) {
		return profiles.get(uuid, u -> get("/v2/skyblock/profiles?uuid=" + u, true));
	}

	/** Static resources such as {@code skills}; these don't need a key. */
	public CompletableFuture<JsonObject> resource(String name) {
		return resources.get(name, n -> get("/v2/resources/skyblock/" + n, false));
	}

	/** {@code /v2/skyblock/bazaar}: every bazaar product's current prices. No key needed. */
	public CompletableFuture<JsonObject> bazaar() {
		return get("/v2/skyblock/bazaar", false);
	}

	/** Forget cached data for a player, e.g. when the user presses refresh. */
	public void invalidate(String uuid) {
		profiles.invalidate(uuid);
	}

	private CompletableFuture<JsonObject> get(String path, boolean needsKey) {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl.get().strip().replaceAll("/+$", "") + path))
				.timeout(Duration.ofSeconds(15))
				.header("User-Agent", "slugggaddons")
				.GET();
		String key = apiKey.get();
		if (needsKey && key != null && !key.isBlank()) {
			request.header("API-Key", key.trim());
		}
		return http.sendAsync(request.build(), HttpResponse.BodyHandlers.ofString())
				.handle((response, error) -> {
					if (error != null) {
						throw new ApiException(ApiException.Kind.NETWORK, "Couldn't reach the Hypixel API", error);
					}
					return parse(response.statusCode(), response.body());
				});
	}

	static JsonObject parse(int status, String body) {
		switch (status) {
			case 403 -> throw new ApiException(ApiException.Kind.INVALID_KEY, "API key is missing or invalid. Set it in config/slugggaddons.json");
			case 429 -> throw new ApiException(ApiException.Kind.RATE_LIMITED, "Too many requests, try again in a minute");
			default -> {
			}
		}
		JsonObject json;
		try {
			json = JsonParser.parseString(body).getAsJsonObject();
		} catch (RuntimeException e) {
			throw new ApiException(ApiException.Kind.HTTP, "Hypixel API returned HTTP " + status);
		}
		if (status != 200 || (json.has("success") && !json.get("success").getAsBoolean())) {
			String cause = json.has("cause") ? json.get("cause").getAsString() : "HTTP " + status;
			throw new ApiException(ApiException.Kind.HTTP, "Hypixel API error: " + cause);
		}
		return json;
	}
}
