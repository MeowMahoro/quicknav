package net.quicknav.repo;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Initializes the NEU repo, which contains item metadata. This is <b>Scheme B</b>: a zero-dependency
 * implementation that uses only the JDK. The repository is downloaded as a GitHub zip archive,
 * extracted, and the latest commit hash is cached next to it so a full download only happens when
 * the repository actually changes.
 * <p>
 * Use {@link #runAsyncAfterLoad(Runnable)} to run code after the repo is initialized.
 */
public class NEURepoManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(NEURepoManager.class);
	private static final String REMOTE_REPO_URL = "https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO";
	private static final String REMOTE_ZIP_URL = REMOTE_REPO_URL + "/archive/refs/heads/master.zip";
	// Note: this must be the REST API host, not the web UI, otherwise we get an HTML page instead of JSON.
	private static final String COMMITS_API_URL = "https://api.github.com/repos/NotEnoughUpdates/NotEnoughUpdates-REPO/commits/master";
	private static final Path LOCAL_REPO_DIR = FabricLoader.getInstance().getConfigDir().resolve("quicknav-item-repo");
	private static final Path VERSION_FILE = FabricLoader.getInstance().getConfigDir().resolve("quicknav-item-repo.version");
	private static final int DOWNLOAD_ATTEMPTS = 3;

	/**
	 * A single shared client with a generous connect timeout. GitHub connectivity is often flaky
	 * (dropped SYN packets, stalled IPv6 attempts), so callers retry a few times before giving up.
	 */
	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(15))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();

	static {
		// Some networks only have working IPv4 connectivity to GitHub. If the DNS server hands out
		// AAAA records, the JDK may pick IPv6 and stall; forcing IPv4 avoids long connect hangs.
		System.setProperty("java.net.preferIPv4Stack", "true");
		// Pick up a proxy from the environment (e.g. Clash at HTTPS_PROXY=http://127.0.0.1:7890).
		// The JDK does not read these variables by itself, while most other tools do.
		applyProxyFromEnv("HTTPS_PROXY");
		applyProxyFromEnv("HTTP_PROXY");
		applyProxyFromEnv("https_proxy");
		applyProxyFromEnv("http_proxy");
	}

	private static void applyProxyFromEnv(String envName) {
		String raw = System.getenv(envName);
		if (raw == null || raw.isBlank()) return;
		try {
			String value = raw.contains("://") ? raw : "http://" + raw;
			URI uri = URI.create(value);
			String host = uri.getHost();
			if (host == null || host.isBlank()) return;
			int port = uri.getPort();
			if (port <= 0) port = 80;
			boolean https = envName.toLowerCase().contains("https");
			String propHost = https ? "https.proxyHost" : "http.proxyHost";
			String propPort = https ? "https.proxyPort" : "http.proxyPort";
			if (System.getProperty(propHost) == null) {
				System.setProperty(propHost, host);
				System.setProperty(propPort, String.valueOf(port));
				LOGGER.info("[QuickNav NEU Repo] Using {} proxy from environment: {}:{}", https ? "HTTPS" : "HTTP", host, port);
			}
		} catch (Exception e) {
			LOGGER.debug("[QuickNav NEU Repo] Ignoring invalid proxy environment variable {}={}", envName, raw);
		}
	}

	private static final List<SkyblockItem> ITEMS = new ArrayList<>();
	/**
	 * Store after load runnables so we can execute them after each time the repository is (re)loaded.
	 */
	private static final List<Runnable> afterLoadTasks = new CopyOnWriteArrayList<>();
	private static final CompletableFuture<Boolean> REPO_LOADING = loadRepository().thenApplyAsync(success -> {
		CompletableFuture.allOf(afterLoadTasks.stream()
						.map(task -> CompletableFuture.runAsync(task, Executors.newVirtualThreadPerTaskExecutor()))
						.toArray(CompletableFuture[]::new))
				.exceptionally(e -> {
					LOGGER.error("[QuickNav NEU Repo] Encountered unknown exception while running after load tasks", e);
					return null;
				});
		return success;
	}, Executors.newVirtualThreadPerTaskExecutor());

	private NEURepoManager() {}

	public static void init() {
		// Loading is kicked off by the static initializer; nothing else to do here.
	}

	public static boolean isLoading() {
		return REPO_LOADING != null && !REPO_LOADING.isDone();
	}

	private static CompletableFuture<Boolean> loadRepository() {
		return CompletableFuture.supplyAsync(() -> {
			boolean success = true;
			try {
				String remoteSha = getRemoteCommitSha();
				boolean hasLocal = hasValidLocalRepo();
				if (hasLocal && (remoteSha == null || remoteSha.equals(readVersion()))) {
					// Local copy is up to date, or the version check failed so fall back to it.
					LOGGER.info("[QuickNav NEU Repo] Using cached NEU repository");
				} else {
					downloadAndExtract();
					writeVersion(remoteSha);
					LOGGER.info("[QuickNav NEU Repo] NEU repository downloaded");
				}
			} catch (Exception e) {
				LOGGER.error("[QuickNav NEU Repo] Failed to update the NEU repository, falling back to the cached copy", e);
				success = hasValidLocalRepo();
			}

			try {
				loadItems();
			} catch (Exception e) {
				LOGGER.error("[QuickNav NEU Repo] Failed to parse the NEU repository", e);
				success = false;
			}
			return success;
		}, Executors.newVirtualThreadPerTaskExecutor());
	}

	/**
	 * @return the sha of the latest commit on master, or {@code null} if the GitHub API is
	 * unreachable or rate limited (in which case we keep using the local cache).
	 */
	private static @org.jspecify.annotations.Nullable String getRemoteCommitSha() {
		for (int attempt = 1; attempt <= DOWNLOAD_ATTEMPTS; attempt++) {
			try {
				HttpRequest request = HttpRequest.newBuilder(URI.create(COMMITS_API_URL))
						.header("User-Agent", "quicknav")
						.timeout(Duration.ofSeconds(20))
						.GET()
						.build();
				HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
				if (response.statusCode() == 200) {
					JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
					return json.get("sha").getAsString();
				}
				LOGGER.warn("[QuickNav NEU Repo] GitHub API returned status code {}", response.statusCode());
				return null;
			} catch (Exception e) {
				LOGGER.warn("[QuickNav NEU Repo] Could not check the latest commit (attempt {}/{}), using the cached repository if present", attempt, DOWNLOAD_ATTEMPTS, e);
				if (attempt < DOWNLOAD_ATTEMPTS) {
					try {
						Thread.sleep(2000L * attempt);
					} catch (InterruptedException ie) {
						Thread.currentThread().interrupt();
						return null;
					}
				}
			}
		}
		return null;
	}

	/**
	 * Downloads the repository zip archive and extracts it into {@link #LOCAL_REPO_DIR}, stripping
	 * the top-level archive folder. The archive is first extracted into a sibling directory on the
	 * same drive as the final location, so swapping it in is a fast same-volume rename. If the
	 * rename is still not possible (leftover files, locks), we fall back to a recursive copy so the
	 * cache is never left in a half-updated state.
	 */
	private static void downloadAndExtract() throws IOException, InterruptedException {
		Path tempDir = Files.createTempDirectory("quicknav-repo");
		try {
			Path zipPath = tempDir.resolve("repo.zip");
			downloadZipWithRetries(zipPath);

			Path extractDir = LOCAL_REPO_DIR.resolveSibling("quicknav-item-repo.tmp");
			deleteRecursively(extractDir);
			Files.createDirectories(extractDir);
			try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))) {
				byte[] buffer = new byte[8192];
				ZipEntry entry;
				while ((entry = zis.getNextEntry()) != null) {
					if (entry.isDirectory()) continue;

					// Strip the top-level folder (e.g. "NotEnoughUpdates-REPO-master/").
					String name = entry.getName();
					int slash = name.indexOf('/');
					String relative = slash >= 0 ? name.substring(slash + 1) : name;
					if (relative.isEmpty()) continue;

					Path out = extractDir.resolve(relative);
					Files.createDirectories(out.getParent());
					try (OutputStream os = Files.newOutputStream(out)) {
						int read;
						while ((read = zis.read(buffer)) != -1) os.write(buffer, 0, read);
					}
				}
			}

			// Swap in the fresh copy.
			deleteRecursively(LOCAL_REPO_DIR);
			try {
				Files.move(extractDir, LOCAL_REPO_DIR);
			} catch (IOException e) {
				// Renaming may fail if something is still holding files in the old location
				// (e.g. leftover files from a previous run). Copy instead so we still end up with data.
				LOGGER.warn("[QuickNav NEU Repo] Could not rename the extracted repo into place, copying instead: {}", e.toString());
				copyRecursively(extractDir, LOCAL_REPO_DIR);
				deleteRecursively(extractDir);
			}
		} finally {
			deleteRecursively(tempDir);
		}
	}

	/**
	 * Recursively copies {@code source} into {@code target} (which may not exist yet).
	 */
	private static void copyRecursively(Path source, Path target) throws IOException {
		try (Stream<Path> walk = Files.walk(source)) {
			for (Path path : walk.toList()) {
				Path relative = source.relativize(path);
				Path destination = target.resolve(relative);
				if (Files.isDirectory(path)) {
					Files.createDirectories(destination);
				} else {
					Files.createDirectories(destination.getParent());
					Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}
	}

	/**
	 * Downloads the repository zip, retrying on transient network failures. The GitHub connect phase
	 * is occasionally flaky (dropped packets, stalled connections), and a single retry usually
	 * succeeds once the connection actually goes through.
	 */
	private static void downloadZipWithRetries(Path zipPath) throws IOException, InterruptedException {
		IOException lastError = null;
		for (int attempt = 1; attempt <= DOWNLOAD_ATTEMPTS; attempt++) {
			long startNanos = System.nanoTime();
			try {
				HttpRequest request = HttpRequest.newBuilder(URI.create(REMOTE_ZIP_URL))
						.header("User-Agent", "quicknav")
						.timeout(Duration.ofMinutes(5))
						.GET()
						.build();
				try (InputStream in = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream()).body();
						OutputStream out = Files.newOutputStream(zipPath)) {
					in.transferTo(out);
				}
				LOGGER.info("[QuickNav NEU Repo] Downloaded repository zip ({} bytes) in {} ms",
						Files.size(zipPath), (System.nanoTime() - startNanos) / 1_000_000);
				return;
			} catch (IOException | InterruptedException e) {
				lastError = e instanceof IOException ? (IOException) e : new IOException(e);
				LOGGER.warn("[QuickNav NEU Repo] Download attempt {}/{} failed: {}",
						attempt, DOWNLOAD_ATTEMPTS, e.toString());
				if (attempt < DOWNLOAD_ATTEMPTS) sleep(2000L * attempt);
			}
		}
		throw lastError != null ? lastError : new IOException("Failed to download the NEU repository");
	}

	private static void sleep(long millis) throws InterruptedException {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw e;
		}
	}

	/**
	 * Parses the extracted {@code items/*.json} files into lightweight {@link SkyblockItem}s.
	 */
	private static void loadItems() throws IOException {
		ITEMS.clear();
		Path itemsDir = LOCAL_REPO_DIR.resolve("items");
		if (!Files.isDirectory(itemsDir)) throw new IOException("items directory missing in the NEU repository");

		try (Stream<Path> files = Files.list(itemsDir)) {
			files.filter(path -> path.getFileName().toString().endsWith(".json")).forEach(path -> {
				try {
					JsonObject json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
					String skyblockId = path.getFileName().toString().replaceAll("\\.json$", "");
					String displayName = json.has("displayname") ? json.get("displayname").getAsString() : skyblockId;
					String minecraftItemId = json.has("itemid") ? json.get("itemid").getAsString() : "minecraft:paper";
					int damage = json.has("damage") ? json.get("damage").getAsInt() : 0;
					String nbttag = json.has("nbttag") ? json.get("nbttag").getAsString() : "{}";
					List<String> lore = new ArrayList<>();
					if (json.has("lore") && json.get("lore").isJsonArray()) {
						json.getAsJsonArray("lore").forEach(element -> lore.add(element.getAsString()));
					}
					ITEMS.add(new SkyblockItem(skyblockId, displayName, minecraftItemId, damage, nbttag, lore));
				} catch (Exception e) {
					LOGGER.error("[QuickNav NEU Repo] Failed to parse item file: {}", path.getFileName(), e);
				}
			});
		}

		ITEMS.sort(Comparator.<SkyblockItem, String>comparing(item -> item.skyblockId().replaceAll(".\\d+$", ""))
				.thenComparingInt(item -> item.skyblockId().length())
				.thenComparing(SkyblockItem::skyblockId)
		);
		LOGGER.info("[QuickNav NEU Repo] Parsed {} items", ITEMS.size());
	}

	private static void writeVersion(@org.jspecify.annotations.Nullable String sha) throws IOException {
		Files.writeString(VERSION_FILE, sha != null ? sha : "unknown");
	}

	private static @org.jspecify.annotations.Nullable String readVersion() throws IOException {
		return Files.exists(VERSION_FILE) ? Files.readString(VERSION_FILE).trim() : null;
	}

	/**
	 * A local copy only counts as a usable cache when it actually contains item data, so an empty or
	 * partially extracted directory is never mistaken for a fresh repository.
	 */
	private static boolean hasValidLocalRepo() {
		if (!Files.isDirectory(LOCAL_REPO_DIR)) return false;
		Path itemsDir = LOCAL_REPO_DIR.resolve("items");
		if (!Files.isDirectory(itemsDir)) return false;
		try (Stream<Path> files = Files.list(itemsDir)) {
			return files.anyMatch(path -> path.getFileName().toString().endsWith(".json"));
		} catch (IOException e) {
			return false;
		}
	}

	private static void deleteRecursively(Path path) {
		try (Stream<Path> walk = Files.walk(path)) {
			walk.sorted(Comparator.reverseOrder()).forEach(p -> {
				try {
					Files.delete(p);
				} catch (IOException e) {
					LOGGER.debug("[QuickNav NEU Repo] Could not delete {}", p, e);
				}
			});
		} catch (IOException e) {
			LOGGER.debug("[QuickNav NEU Repo] Could not walk {} for deletion", path, e);
		}
	}

	/**
	 * Runs the given runnable after the NEU repo is initialized. The runnable also runs again after
	 * every subsequent (forced) reload, which is what keeps {@link ItemRepository} in sync.
	 *
	 * @param runnable the runnable to run
	 * @return a completable future of the given runnable
	 */
	public static CompletableFuture<Void> runAsyncAfterLoad(Runnable runnable) {
		return REPO_LOADING.thenRunAsync(runnable).exceptionally(e -> {
			LOGGER.error("[QuickNav NEU Repo] Encountered unknown exception while running after load task", e);
			return null;
		}).thenRun(() -> afterLoadTasks.add(runnable)); // Add to the list after so it doesn't get executed twice.
	}

	/**
	 * Iterates over every item in the repository. Only call this from after the repo has loaded
	 * (e.g. via {@link #runAsyncAfterLoad(Runnable)}).
	 */
	public static void forEachItem(Consumer<SkyblockItem> consumer) {
		ITEMS.forEach(consumer);
	}

	/**
	 * @return the local directory the repository is extracted into
	 */
	public static Path getCachePath() {
		return LOCAL_REPO_DIR;
	}
}
