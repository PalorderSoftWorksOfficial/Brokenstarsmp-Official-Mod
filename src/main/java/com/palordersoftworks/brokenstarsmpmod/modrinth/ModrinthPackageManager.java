package com.palordersoftworks.brokenstarsmpmod.modrinth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class ModrinthPackageManager {
    private static final URI API_BASE = URI.create("https://api.modrinth.com/v2/");
    private static final HttpClient CLIENT = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path MODS_DIR = FabricLoader.getInstance().getGameDir().resolve("mods");
    private static final Path MANIFEST_FILE = MODS_DIR.resolve(".brokenstarsmp-apt.json");
    private static final String GAME_VERSION = SharedConstants.getGameVersion().toString();
    private static final String LOADER = "fabric";

    private ModrinthPackageManager() {
    }

    public static List<SearchHit> search(String query) throws IOException {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        JsonObject root = requestObject(endpoint("/search?query=" + encoded + "&limit=5"));
        JsonArray hits = root.getAsJsonArray("hits");
        if (hits == null || hits.isEmpty()) {
            return Collections.emptyList();
        }

        List<SearchHit> results = new ArrayList<>();
        for (JsonElement element : hits) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject hit = element.getAsJsonObject();
            results.add(new SearchHit(
                    getString(hit, "project_id"),
                    getString(hit, "slug"),
                    getString(hit, "title"),
                    getString(hit, "author"),
                    getString(hit, "description")
            ));
        }
        return results;
    }

    public static InstallResult install(String query) throws IOException {
        ResolvedProject project = resolveProject(query);
        ResolvedVersion version = resolveLatestCompatibleVersion(project.projectId());
        downloadVersion(version);
        upsertManifest(new InstalledPackage(
                project.projectId(),
                project.slug(),
                project.title(),
                version.versionId(),
                version.versionNumber(),
                version.filename(),
                Instant.now().toString()
        ));
        return new InstallResult(project.title(), project.slug(), version.versionNumber(), version.filename(), MODS_DIR.resolve(version.filename()));
    }

    public static List<InstalledPackage> listInstalled() throws IOException {
        return readManifest();
    }

    public static boolean remove(String identifier) throws IOException {
        List<InstalledPackage> installed = new ArrayList<>(readManifest());
        Optional<InstalledPackage> match = installed.stream().filter(pkg -> matches(pkg, identifier)).findFirst();
        if (match.isEmpty()) {
            return false;
        }

        InstalledPackage pkg = match.get();
        installed.remove(pkg);
        writeManifest(installed);
        Files.deleteIfExists(MODS_DIR.resolve(pkg.filename()));
        return true;
    }

    public static List<UpdateResult> updateAll() throws IOException {
        List<InstalledPackage> installed = new ArrayList<>(readManifest());
        if (installed.isEmpty()) {
            return Collections.emptyList();
        }

        List<UpdateResult> updates = new ArrayList<>();
        boolean changed = false;
        for (int i = 0; i < installed.size(); i++) {
            InstalledPackage pkg = installed.get(i);
            ResolvedVersion latest = resolveLatestCompatibleVersion(pkg.projectId());
            if (pkg.versionId().equals(latest.versionId())) {
                continue;
            }

            downloadVersion(latest);
            Files.deleteIfExists(MODS_DIR.resolve(pkg.filename()));
            InstalledPackage replacement = new InstalledPackage(
                    pkg.projectId(),
                    pkg.slug(),
                    pkg.title(),
                    latest.versionId(),
                    latest.versionNumber(),
                    latest.filename(),
                    Instant.now().toString()
            );
            installed.set(i, replacement);
            updates.add(new UpdateResult(pkg.title(), pkg.versionNumber(), latest.versionNumber(), latest.filename()));
            changed = true;
        }

        if (changed) {
            writeManifest(installed);
        }
        return updates;
    }

    public static Optional<InstalledPackage> findInstalled(String identifier) throws IOException {
        return readManifest().stream().filter(pkg -> matches(pkg, identifier)).findFirst();
    }

    private static ResolvedProject resolveProject(String query) throws IOException {
        String trimmed = query.trim();
        Optional<JsonObject> direct = fetchProjectObject(trimmed);
        if (direct.isPresent()) {
            JsonObject object = direct.get();
            return new ResolvedProject(getString(object, "id"), getString(object, "slug"), getString(object, "title"));
        }

        List<SearchHit> hits = search(trimmed);
        if (hits.isEmpty()) {
            throw new IOException("No Modrinth project found for: " + query);
        }
        SearchHit hit = hits.get(0);
        return new ResolvedProject(hit.projectId(), hit.slug(), hit.title());
    }

    private static Optional<JsonObject> fetchProjectObject(String projectIdOrSlug) throws IOException {
        HttpResponse<String> response = request(endpoint("/project/" + encodePath(projectIdOrSlug)));
        if (response.statusCode() == 404) {
            return Optional.empty();
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Modrinth project request failed: " + response.statusCode() + " " + response.body());
        }
        JsonElement element = JsonParser.parseString(response.body());
        return element.isJsonObject() ? Optional.of(element.getAsJsonObject()) : Optional.empty();
    }

    private static ResolvedVersion resolveLatestCompatibleVersion(String projectId) throws IOException {
        JsonArray compatible = requestVersionArray(projectId, true);
        if (!compatible.isEmpty()) {
            return parseVersion(compatible.get(0).getAsJsonObject());
        }

        JsonArray fabricOnly = requestVersionArray(projectId, false);
        if (!fabricOnly.isEmpty()) {
            return parseVersion(fabricOnly.get(0).getAsJsonObject());
        }

        throw new IOException("No compatible Fabric versions found for " + projectId + " on Minecraft " + GAME_VERSION);
    }

    private static JsonArray requestVersionArray(String projectId, boolean restrictGameVersion) throws IOException {
        StringBuilder query = new StringBuilder("/project/")
                .append(encodePath(projectId))
                .append("/version?loaders=")
                .append(encodeJsonArray(LOADER));
        if (restrictGameVersion) {
            query.append("&game_versions=").append(encodeJsonArray(GAME_VERSION));
        }
        JsonElement element = requestElement(endpoint(query.toString()));
        return element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
    }

    private static ResolvedVersion parseVersion(JsonObject version) throws IOException {
        String versionId = getString(version, "id");
        String versionNumber = getString(version, "version_number");
        JsonArray files = version.getAsJsonArray("files");
        if (files == null || files.isEmpty()) {
            throw new IOException("Modrinth version has no downloadable files: " + versionId);
        }

        JsonObject file = null;
        for (JsonElement element : files) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject candidate = element.getAsJsonObject();
            if (candidate.has("primary") && candidate.get("primary").getAsBoolean()) {
                file = candidate;
                break;
            }
            if (file == null) {
                file = candidate;
            }
        }
        if (file == null) {
            throw new IOException("Modrinth version has no valid file entry: " + versionId);
        }

        return new ResolvedVersion(versionId, versionNumber, getString(file, "filename"), URI.create(getString(file, "url")));
    }

    private static void downloadVersion(ResolvedVersion version) throws IOException {
        try {
            Files.createDirectories(MODS_DIR);
            Path target = MODS_DIR.resolve(version.filename());
            Path temp = Files.createTempFile(MODS_DIR, "modrinth-", ".part");
            HttpResponse<Path> response = CLIENT.send(
                    HttpRequest.newBuilder(version.downloadUri()).GET().build(),
                    HttpResponse.BodyHandlers.ofFile(temp)
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                Files.deleteIfExists(temp);
                throw new IOException("Failed to download Modrinth file: " + response.statusCode());
            }
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Download interrupted", exception);
        }
    }

    private static List<InstalledPackage> readManifest() throws IOException {
        Files.createDirectories(MODS_DIR);
        if (!Files.exists(MANIFEST_FILE)) {
            return new ArrayList<>();
        }

        try (Reader reader = Files.newBufferedReader(MANIFEST_FILE, StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (!element.isJsonObject()) {
                return new ArrayList<>();
            }
            JsonArray array = element.getAsJsonObject().getAsJsonArray("installed");
            if (array == null || array.isEmpty()) {
                return new ArrayList<>();
            }

            List<InstalledPackage> installed = new ArrayList<>();
            for (JsonElement pkgElement : array) {
                if (!pkgElement.isJsonObject()) {
                    continue;
                }
                JsonObject pkg = pkgElement.getAsJsonObject();
                installed.add(new InstalledPackage(
                        getString(pkg, "projectId"),
                        getString(pkg, "slug"),
                        getString(pkg, "title"),
                        getString(pkg, "versionId"),
                        getString(pkg, "versionNumber"),
                        getString(pkg, "filename"),
                        getString(pkg, "installedAt")
                ));
            }
            installed.sort(Comparator.comparing(InstalledPackage::title, String.CASE_INSENSITIVE_ORDER));
            return installed;
        }
    }

    private static void writeManifest(List<InstalledPackage> installed) throws IOException {
        Files.createDirectories(MODS_DIR);
        JsonObject root = new JsonObject();
        root.addProperty("schema", 1);
        JsonArray array = new JsonArray();
        for (InstalledPackage pkg : installed) {
            JsonObject object = new JsonObject();
            object.addProperty("projectId", pkg.projectId());
            object.addProperty("slug", pkg.slug());
            object.addProperty("title", pkg.title());
            object.addProperty("versionId", pkg.versionId());
            object.addProperty("versionNumber", pkg.versionNumber());
            object.addProperty("filename", pkg.filename());
            object.addProperty("installedAt", pkg.installedAt());
            array.add(object);
        }
        root.add("installed", array);
        Files.writeString(MANIFEST_FILE, GSON.toJson(root), StandardCharsets.UTF_8);
    }

    private static void upsertManifest(InstalledPackage replacement) throws IOException {
        List<InstalledPackage> installed = new ArrayList<>(readManifest());
        installed.removeIf(pkg -> Objects.equals(pkg.projectId(), replacement.projectId()) || Objects.equals(pkg.slug(), replacement.slug()));
        installed.add(replacement);
        writeManifest(installed);
    }

    private static boolean matches(InstalledPackage pkg, String identifier) {
        String normalized = identifier.trim().toLowerCase(Locale.ROOT);
        return pkg.projectId().equalsIgnoreCase(identifier)
                || pkg.slug().equalsIgnoreCase(identifier)
                || pkg.title().equalsIgnoreCase(identifier)
                || pkg.projectId().toLowerCase(Locale.ROOT).contains(normalized)
                || pkg.slug().toLowerCase(Locale.ROOT).contains(normalized)
                || pkg.title().toLowerCase(Locale.ROOT).contains(normalized);
    }

    private static HttpResponse<String> request(URI uri) throws IOException {
        try {
            HttpResponse<String> response = CLIENT.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return response;
            }
            return response;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Modrinth request interrupted", exception);
        }
    }

    private static JsonObject requestObject(URI uri) throws IOException {
        HttpResponse<String> response = request(uri);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Modrinth request failed: " + response.statusCode() + " " + response.body());
        }
        JsonElement element = JsonParser.parseString(response.body());
        if (!element.isJsonObject()) {
            throw new IOException("Expected JSON object from Modrinth: " + uri);
        }
        return element.getAsJsonObject();
    }

    private static JsonElement requestElement(URI uri) throws IOException {
        HttpResponse<String> response = request(uri);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Modrinth request failed: " + response.statusCode() + " " + response.body());
        }
        return JsonParser.parseString(response.body());
    }

    private static String getString(JsonObject object, String key) throws IOException {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            throw new IOException("Missing Modrinth field: " + key);
        }
        return object.get(key).getAsString();
    }

    private static URI endpoint(String suffix) {
        return API_BASE.resolve(suffix.startsWith("/") ? suffix.substring(1) : suffix);
    }

    private static String encodePath(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String encodeJsonArray(String value) {
        return URLEncoder.encode("[\"" + value + "\"]", StandardCharsets.UTF_8);
    }

    public record SearchHit(String projectId, String slug, String title, String author, String description) {
    }

    public record InstalledPackage(String projectId, String slug, String title, String versionId, String versionNumber, String filename, String installedAt) {
    }

    public record InstallResult(String title, String slug, String versionNumber, String filename, Path path) {
    }

    public record UpdateResult(String title, String oldVersion, String newVersion, String filename) {
    }

    private record ResolvedProject(String projectId, String slug, String title) {
    }

    private record ResolvedVersion(String versionId, String versionNumber, String filename, URI downloadUri) {
    }
}