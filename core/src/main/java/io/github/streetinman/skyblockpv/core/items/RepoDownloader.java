package io.github.streetinman.skyblockpv.core.items;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * Keeps a local copy of the item repository zip. It's re-downloaded when older than
 * {@link #MAX_AGE}; if that fails, the old copy is used.
 */
public final class RepoDownloader {
	public static final Duration MAX_AGE = Duration.ofDays(3);

	private final HttpClient http = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NORMAL)
			.connectTimeout(Duration.ofSeconds(15))
			.build();

	/** @return the zip path once it's present and fresh enough */
	public CompletableFuture<Path> ensure(String url, Path zip) {
		try {
			if (Files.exists(zip) && Files.getLastModifiedTime(zip).toInstant().isAfter(Instant.now().minus(MAX_AGE))) {
				return CompletableFuture.completedFuture(zip);
			}
			Files.createDirectories(zip.getParent());
		} catch (IOException e) {
			return CompletableFuture.failedFuture(e);
		}
		Path partial = zip.resolveSibling(zip.getFileName() + ".part");
		HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(3))
				.header("User-Agent", "slugggaddons").GET().build();
		return http.sendAsync(request, HttpResponse.BodyHandlers.ofFile(partial)).handle((response, error) -> {
			try {
				if (error == null && response.statusCode() == 200) {
					Files.move(partial, zip, StandardCopyOption.REPLACE_EXISTING);
					return zip;
				}
				Files.deleteIfExists(partial);
				if (Files.exists(zip)) return zip;
				throw new IOException(error != null ? "Couldn't download items: " + error.getMessage()
						: "Item download returned HTTP " + response.statusCode());
			} catch (IOException e) {
				throw new java.io.UncheckedIOException(e);
			}
		});
	}
}
