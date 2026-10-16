package io.github.streetinman.skyblockpv.core.networth;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.github.streetinman.skyblockpv.core.api.ApiException;
import io.github.streetinman.skyblockpv.core.api.HypixelClient;
import io.github.streetinman.skyblockpv.core.api.TtlCache;

/**
 * Loads bazaar prices from the Hypixel API and lowest BIN prices from a public price list,
 * cached for 10 minutes so opening several profiles costs one download.
 */
public final class PriceClient {
	private final HttpClient http;
	private final HypixelClient hypixel;
	private final Supplier<String> lowestBinUrl;
	private final TtlCache<String, Prices> cache = new TtlCache<>(Duration.ofMinutes(10));

	public PriceClient(HttpClient http, HypixelClient hypixel, Supplier<String> lowestBinUrl) {
		this.http = http;
		this.hypixel = hypixel;
		this.lowestBinUrl = lowestBinUrl;
	}

	public CompletableFuture<Prices> prices() {
		return cache.get("prices", k -> hypixel.bazaar().thenApply(Prices::parseBazaar)
				.thenCombine(lowestBin(), Prices::new));
	}

	private CompletableFuture<java.util.Map<String, Double>> lowestBin() {
		HttpRequest request = HttpRequest.newBuilder(URI.create(lowestBinUrl.get()))
				.timeout(Duration.ofSeconds(20))
				.header("User-Agent", "slugggaddons")
				.GET().build();
		return http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
			if (response.statusCode() != 200) {
				throw new ApiException(ApiException.Kind.HTTP, "Price list returned HTTP " + response.statusCode());
			}
			JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
			return Prices.parseLowestBin(json);
		});
	}
}
