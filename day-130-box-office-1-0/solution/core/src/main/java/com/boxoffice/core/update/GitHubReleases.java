package com.boxoffice.core.update;

import com.boxoffice.core.net.ResilientHttp;
import com.boxoffice.core.release.SemVer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads the newest release from GitHub's API, the same releases the Day 129
 * workflow publishes:
 *
 * <pre>
 * GET https://api.github.com/repos/sagebeme/box-office-30-days/releases/latest
 * </pre>
 *
 * GitHub leaves drafts and pre-releases out of "latest", so a half-finished
 * release never reaches a gate.
 */
public final class GitHubReleases implements ReleaseFeed {

    public static final URI API = URI.create("https://api.github.com");

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ResilientHttp http;
    private final URI api;
    private final String repository;

    /** @param repository "owner/name", e.g. "sagebeme/box-office-30-days" */
    public GitHubReleases(ResilientHttp http, URI api, String repository) {
        if (!repository.matches("[\\w.-]+/[\\w.-]+")) {
            throw new IllegalArgumentException("A repository is owner/name, like sagebeme/box-office-30-days");
        }
        this.http = http;
        this.api = api;
        this.repository = repository;
    }

    @Override
    public Optional<Release> latest() {
        HttpRequest request = HttpRequest.newBuilder(api.resolve("/repos/" + repository + "/releases/latest"))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .GET()
                .build();
        HttpResponse<String> response = http.send(request);
        if (response.statusCode() == 404) {
            return Optional.empty();
        }
        if (response.statusCode() != 200) {
            throw new UpdateException("GitHub answered HTTP " + response.statusCode() + " about " + repository);
        }
        return Optional.of(parse(response.body()));
    }

    static Release parse(String body) {
        try {
            JsonNode release = JSON.readTree(body);
            List<Asset> assets = new ArrayList<>();
            for (JsonNode asset : release.path("assets")) {
                assets.add(new Asset(asset.path("name").asText(), URI.create(asset.path("browser_download_url").asText()),
                        asset.path("size").asLong()));
            }
            return new Release(SemVer.parse(release.path("tag_name").asText()), release.path("body").asText(""), assets);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new UpdateException("GitHub's answer about the latest release didn't make sense", e);
        }
    }
}
