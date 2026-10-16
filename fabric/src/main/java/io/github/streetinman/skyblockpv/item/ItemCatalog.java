package io.github.streetinman.skyblockpv.item;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.ItemStack;

import io.github.streetinman.skyblockpv.SkyblockPvClient;
import io.github.streetinman.skyblockpv.core.items.ItemRepository;
import io.github.streetinman.skyblockpv.core.items.RepoDownloader;
import io.github.streetinman.skyblockpv.core.items.RepoItem;
import io.github.streetinman.skyblockpv.core.model.SkyblockItem;
import io.github.streetinman.skyblockpv.core.nbt.NbtCompound;
import io.github.streetinman.skyblockpv.core.nbt.Snbt;
import io.github.streetinman.skyblockpv.core.parse.ItemDecoder;

/**
 * Every SkyBlock item and recipe, loaded once in the background from the NEU item repository.
 * Icons are built only when drawn and kept in a small LRU cache, so the full ~6,000 items never
 * sit in memory as ItemStacks at once.
 */
public final class ItemCatalog {
	private static final int ICON_CACHE_SIZE = 600;

	private static CompletableFuture<ItemRepository> loading;
	private static final Map<String, ItemStack> ICONS = new LinkedHashMap<>(256, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, ItemStack> eldest) {
			return size() > ICON_CACHE_SIZE;
		}
	};

	private ItemCatalog() {
	}

	/** Starts (or returns) the download and parse. Safe to call every time a screen opens. */
	public static synchronized CompletableFuture<ItemRepository> repository() {
		if (loading == null || loading.isCompletedExceptionally()) {
			Path zip = FabricLoader.getInstance().getConfigDir().resolve("slugggaddons/neu-repo.zip");
			loading = new RepoDownloader().ensure(SkyblockPvClient.config().itemRepoUrl, zip).thenApplyAsync(path -> {
				try {
					return ItemRepository.load(path);
				} catch (java.io.IOException e) {
					throw new java.io.UncheckedIOException(e);
				}
			});
		}
		return loading;
	}

	/** The item's icon with its name and lore as the tooltip. Call on the render thread. */
	public static ItemStack icon(RepoItem item) {
		ItemStack cached = ICONS.get(item.id());
		if (cached != null) return cached;
		ItemStack stack = build(item);
		ICONS.put(item.id(), stack);
		return stack;
	}

	/** An icon for an ID the repository doesn't have, such as a removed item. */
	public static ItemStack missing(String id) {
		return ItemStacks.placeholder("§c" + id);
	}

	private static ItemStack build(RepoItem item) {
		try {
			Map<String, Object> slot = new LinkedHashMap<>();
			slot.put("id", item.minecraftId());
			slot.put("Count", (byte) 1);
			slot.put("Damage", (short) item.damage());
			NbtCompound tag = item.nbt() == null || item.nbt().isBlank() ? null : Snbt.parseCompound(item.nbt());
			if (tag != null) slot.put("tag", tag);
			SkyblockItem decoded = ItemDecoder.decodeItem(new NbtCompound(slot));
			// The repository's lore is the cleanest copy; prefer it and the listed name over the tag's.
			decoded = new SkyblockItem(decoded.legacyId(), 1, decoded.damage(), item.id(), item.name(),
					item.lore().isEmpty() ? decoded.lore() : List.copyOf(item.lore()), decoded.uuid(), decoded.skullTexture(), decoded.nbt());
			return ItemStacks.convert(decoded);
		} catch (RuntimeException | LinkageError e) {
			SkyblockPvClient.LOGGER.debug("Couldn't build icon for {}", item.id(), e);
			return ItemStacks.placeholder(item.name());
		}
	}
}
