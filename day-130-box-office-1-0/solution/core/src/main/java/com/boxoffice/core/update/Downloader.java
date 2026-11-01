package com.boxoffice.core.update;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

/**
 * Fetches an installer and checks it against the release's SHA256SUMS before
 * anyone can run it. A file that doesn't match is deleted, never kept.
 */
public final class Downloader {

    private final HttpClient client;

    public Downloader(HttpClient client) {
        this.client = client;
    }

    /** GitHub's download links redirect to where the files really are, so redirects are followed. */
    public static Downloader forUpdates() {
        return new Downloader(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    /** Downloads the installer into the folder, checks it, and returns where it is. */
    public Path download(Release release, Asset installer, Path folder) {
        Asset sums = release.asset(Release.CHECKSUMS)
                .orElseThrow(() -> new UpdateException("Release " + release.version()
                        + " has no " + Release.CHECKSUMS + ", so there's no way to check the download"));
        String expected = Checksums.parse(fetchText(sums)).get(installer.name());
        if (expected == null) {
            throw new UpdateException(Release.CHECKSUMS + " doesn't list " + installer.name());
        }

        Path part = folder.resolve(installer.name() + ".part");
        Path done = folder.resolve(installer.name());
        try {
            Files.createDirectories(folder);
            HttpResponse<Path> response = client.send(HttpRequest.newBuilder(installer.url()).GET().build(),
                    HttpResponse.BodyHandlers.ofFile(part));
            if (response.statusCode() != 200) {
                Files.deleteIfExists(part);
                throw new UpdateException("The download answered HTTP " + response.statusCode());
            }
            if (!Checksums.sha256(part).equals(expected)) {
                Files.delete(part);
                throw new UpdateException("The download doesn't match its checksum, so it was deleted. Try again");
            }
            // Only a checked file gets the installer's name.
            return Files.move(part, done, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            deleteQuietly(part);
            throw new UpdateException("Couldn't download " + installer.name() + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            deleteQuietly(part);
            Thread.currentThread().interrupt();
            throw new UpdateException("Stopped", e);
        }
    }

    /** A half-downloaded file is no use to anyone: don't leave it lying in the folder. */
    private static void deleteQuietly(Path part) {
        try {
            Files.deleteIfExists(part);
        } catch (IOException ignored) {
            // The error being reported already says the download failed.
        }
    }

    private String fetchText(Asset asset) {
        try {
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(asset.url()).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new UpdateException(asset.name() + " answered HTTP " + response.statusCode());
            }
            return response.body();
        } catch (IOException e) {
            throw new UpdateException("Couldn't fetch " + asset.name() + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UpdateException("Stopped", e);
        }
    }
}
