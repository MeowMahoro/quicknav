package net.quicknav.repo;

import com.mojang.logging.LogUtils;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * A trimmed-down port of Skyblocker's {@code ItemRepository} that only keeps the item list.
 * <p>
 * Only lightweight {@link SkyblockItem} metadata is held in memory; actual {@link ItemStack}s are
 * built lazily on first use and cached in a small LRU map to keep memory usage in check.
 */
public class ItemRepository {
	protected static final Logger LOGGER = LogUtils.getLogger();

	private static final List<SkyblockItem> items = new ArrayList<>();
	private static final Map<String, SkyblockItem> itemsMap = new HashMap<>();
	private static final Map<String, ItemStack> itemStackCache = createLruCache();

	private record AfterImportTask(Runnable runnable, boolean async) {}

	/**
	 * Store callbacks so we can execute them each time the item repository finishes loading.
	 */
	private static final List<AfterImportTask> afterImportTasks = new CopyOnWriteArrayList<>();

	/**
	 * Consumers must check this field when accessing {@code items} and {@code itemsMap}, or else thread safety is not guaranteed.
	 */
	private static volatile boolean itemsImported = false;

	private ItemRepository() {}

	/**
	 * Builds a small LRU cache for lazily constructed item stacks.
	 */
	private static Map<String, ItemStack> createLruCache() {
		return new LinkedHashMap<>(256, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<String, ItemStack> eldest) {
				return size() > 768;
			}
		};
	}

	public static void init() {
		NEURepoManager.runAsyncAfterLoad(ItemRepository::importItemFiles);
	}

	/**
	 * (Re)builds the item list from the NEU repository. Called on the repo loading thread.
	 */
	public static void importItemFiles() {
		itemsImported = false;
		items.clear();
		itemsMap.clear();
		itemStackCache.clear();

		NEURepoManager.forEachItem(ItemRepository::loadItem);

		items.sort(Comparator.<SkyblockItem, String>comparing(item -> item.skyblockId().replaceAll(".\\d+$", ""))
				.thenComparingInt(item -> item.skyblockId().length())
				.thenComparing(SkyblockItem::skyblockId)
		);
		itemsImported = true;

		LOGGER.info("[QuickNav Item Repository] Loaded {} items", items.size());

		afterImportTasks.forEach(task -> {
			if (task.async) {
				CompletableFuture.runAsync(task.runnable, Executors.newVirtualThreadPerTaskExecutor()).exceptionally(e -> {
					LOGGER.error("[QuickNav Item Repository] Encountered unknown exception while running after import tasks", e);
					return null;
				});
			} else {
				try {
					task.runnable.run();
				} catch (Exception e) {
					LOGGER.error("[QuickNav Item Repository] Encountered unknown exception while running after import tasks", e);
				}
			}
		});
	}

	private static void loadItem(SkyblockItem item) {
		items.add(item);
		itemsMap.put(item.skyblockId(), item);
	}

	public static boolean itemsImported() {
		return itemsImported;
	}

	public static List<SkyblockItem> getItems() {
		return itemsImported ? items : List.of();
	}

	public static Stream<SkyblockItem> getItemsStream() {
		return itemsImported ? items.stream() : Stream.empty();
	}

	public static SkyblockItem getItem(String skyblockId) {
		return itemsImported ? itemsMap.get(skyblockId) : null;
	}

	/**
	 * Returns an {@link ItemStack} for the given item, building and caching it lazily on first use.
	 * <p>
	 * This must not be called on the render thread for an unbounded number of distinct items in a
	 * single frame, as {@link ItemStackBuilder} performs a data fixer upgrade.
	 */
	public static synchronized ItemStack getItemStack(SkyblockItem item) {
		return itemStackCache.computeIfAbsent(item.skyblockId(), _ -> ItemStackBuilder.fromSkyblockItem(item));
	}

	/**
	 * Runs the given runnable after the item repository has finished loading.
	 * If the repository is already loaded the runnable is executed immediately.
	 *
	 * @param runnable the runnable to run
	 */
	public static void runAsyncAfterImport(Runnable runnable) {
		runAfterImport(runnable, true);
	}

	/**
	 * Runs the given runnable after the item repository has finished loading.
	 * If the repository is already loaded the runnable is executed immediately.
	 *
	 * @param runnable the runnable to run
	 * @param async    whether to run the runnable asynchronously
	 */
	public static void runAfterImport(Runnable runnable, boolean async) {
		if (itemsImported) {
			if (async) {
				CompletableFuture.runAsync(runnable, Executors.newVirtualThreadPerTaskExecutor()).exceptionally(e -> {
					LOGGER.error("[QuickNav Item Repository] Encountered unknown exception while running after import task", e);
					return null;
				});
			} else {
				try {
					runnable.run();
				} catch (Exception e) {
					LOGGER.error("[QuickNav Item Repository] Encountered unknown exception while running after import task", e);
				}
			}
		}
		afterImportTasks.add(new AfterImportTask(runnable, async));
	}
}
