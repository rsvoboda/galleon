/*
 * Copyright 2016-2018 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.galleon.maven.plugin.util;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.installation.InstallationException;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.jboss.galleon.maven.plugin.FpMavenErrors;
import org.jboss.galleon.universe.maven.MavenArtifact;
import org.jboss.galleon.universe.maven.MavenErrors;
import org.jboss.galleon.universe.maven.MavenUniverseException;
import org.jboss.galleon.universe.maven.repo.MavenArtifactVersion;
import org.jboss.galleon.universe.maven.repo.MavenArtifactVersionRange;
import org.jboss.galleon.universe.maven.repo.MavenArtifactVersionRangeParser;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;

/**
 *
 * @author jdenise@redhat.com
 */
public abstract class AbstractMavenArtifactRepositoryManager implements MavenRepoManager {

    private static final MavenArtifactVersionRangeParser versionRangeParser = new MavenArtifactVersionRangeParser();

    private final RepositorySystem repoSystem;

    public AbstractMavenArtifactRepositoryManager(final RepositorySystem repoSystem) {
        this.repoSystem = repoSystem;
    }

    protected abstract RepositorySystemSession getSession() throws MavenUniverseException;

    protected abstract List<RemoteRepository> getRepositories() throws MavenUniverseException;

    protected RepositorySystem getRepositorySystem() {
        return repoSystem;
    }

    @Override
    public void resolve(MavenArtifact artifact) throws MavenUniverseException {
        if (artifact.isResolved()) {
            throw new MavenUniverseException("Artifact is already resolved");
        }
        final ArtifactRequest request = new ArtifactRequest();
        request.setArtifact(new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(), artifact.getClassifier(),
                artifact.getExtension(), artifact.getVersion()));
        Path path = doResolve(request, artifact.getCoordsAsString());
        artifact.setPath(path);
    }

    private Path doResolve(ArtifactRequest request, String coords) throws MavenUniverseException {
        request.setRepositories(getRepositories());

        final ArtifactResult result;
        try {
            result = repoSystem.resolveArtifact(getSession(), request);
        } catch (org.eclipse.aether.resolution.ArtifactResolutionException e) {
            throw new MavenUniverseException(FpMavenErrors.artifactResolution(coords), e);
        }
        if (!result.isResolved()) {
            throw new MavenUniverseException(FpMavenErrors.artifactResolution(coords));
        }
        if (result.isMissing()) {
            throw new MavenUniverseException(FpMavenErrors.artifactMissing(coords));
        }
        return Paths.get(result.getArtifact().getFile().toURI());
    }

    @Override
    public void resolveLatestVersion(MavenArtifact mavenArtifact, String lowestQualifier) throws MavenUniverseException {
        Artifact artifact = new DefaultArtifact(mavenArtifact.getGroupId(),
                mavenArtifact.getArtifactId(), mavenArtifact.getExtension(), mavenArtifact.getVersionRange());
        String version = doGetHighestVersion(artifact, lowestQualifier, mavenArtifact.getCoordsAsString());
        mavenArtifact.setVersion(version);
        resolve(mavenArtifact);
    }

    private String doGetHighestVersion(Artifact artifact, String lowestQualifier, String coords) throws MavenUniverseException {
        VersionRangeRequest rangeRequest = new VersionRangeRequest();
        rangeRequest.setArtifact(artifact);
        rangeRequest.setRepositories(getRepositories());

        VersionRangeResult rangeResult;
        try {
            rangeResult = repoSystem.resolveVersionRange(getSession(), rangeRequest);
        } catch (VersionRangeResolutionException ex) {
            throw new MavenUniverseException(ex.getLocalizedMessage(), ex);
        }
        final MavenArtifactVersion latest = rangeResult == null ? null : resolveLatest(rangeResult, lowestQualifier);
        if (latest == null) {
            throw new MavenUniverseException("No version retrieved for " + coords);
        }
        return latest.toString();
    }

    private static MavenArtifactVersion resolveLatest(VersionRangeResult rangeResult, String lowestQualifier) throws MavenUniverseException {
        return MavenArtifactVersion.getLatest(rangeResult.getVersions(), lowestQualifier);
    }

    @Override
    public String getLatestVersion(MavenArtifact mavenArtifact, String lowestQualifier) throws MavenUniverseException {
        Artifact artifact = new DefaultArtifact(mavenArtifact.getGroupId(),
                mavenArtifact.getArtifactId(), mavenArtifact.getExtension(), mavenArtifact.getVersionRange());
        return doGetHighestVersion(artifact, lowestQualifier, mavenArtifact.getCoordsAsString());
    }

    @Override
    public void install(MavenArtifact coords, Path path) throws MavenUniverseException {
        final InstallRequest request = new InstallRequest();
        request.addArtifact(new DefaultArtifact(coords.getGroupId(), coords.getArtifactId(), coords.getClassifier(),
                coords.getExtension(), coords.getVersion(), Collections.emptyMap(), path.toFile()));
        try {
            repoSystem.install(getSession(), request);
        } catch (InstallationException ex) {
            throw new MavenUniverseException("Failed to install " + coords.getCoordsAsString(), ex);
        }
    }

    @Override
    public boolean isResolved(MavenArtifact artifact) throws MavenUniverseException {
        if (artifact.isResolved()) {
            return true;
        }
        Path path = getArtifactPath(artifact);
        return Files.exists(path);
    }

    private Path getArtifactPath(MavenArtifact artifact) throws MavenUniverseException {
        if (artifact.getGroupId() == null) {
            MavenErrors.missingGroupId();
        }
        Path p = getSession().getLocalRepository().getBasedir().toPath();
        final String[] groupParts = artifact.getGroupId().split("\\.");
        for (String part : groupParts) {
            p = p.resolve(part);
        }
        final String artifactFileName = artifact.getArtifactFileName();
        return p.resolve(artifact.getArtifactId()).resolve(artifact.getVersion()).resolve(artifactFileName);
    }

    @Override
    public boolean isLatestVersionResolved(MavenArtifact artifact, String lowestQualifier) throws MavenUniverseException {
        if (artifact.isResolved()) {
            return true;
        }
        Path path = resolveLatestVersionDir(artifact, lowestQualifier);
        return Files.exists(path);
    }

    private Path resolveLatestVersionDir(MavenArtifact artifact, String lowestQualifier) throws MavenUniverseException {
        if (artifact.getGroupId() == null) {
            MavenErrors.missingGroupId();
        }
        if (artifact.getArtifactId() == null) {
            MavenErrors.missingArtifactId();
        }
        if (artifact.getVersionRange() == null) {
            throw new MavenUniverseException("Version range is missing for " + artifact.getCoordsAsString());
        }
        Path repoHome = getSession().getLocalRepository().getBasedir().toPath();
        Path artifactDir = repoHome;
        final String[] groupParts = artifact.getGroupId().split("\\.");
        for (String part : groupParts) {
            artifactDir = artifactDir.resolve(part);
        }
        artifactDir = artifactDir.resolve(artifact.getArtifactId());
        if (!Files.exists(artifactDir)) {
            throw MavenErrors.artifactNotFound(artifact, repoHome);
        }
        final MavenArtifactVersionRange range = versionRangeParser.parseRange(artifact.getVersionRange());
        if (lowestQualifier == null) {
            lowestQualifier = "";
        }
        Path latestDir = null;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(artifactDir)) {
            MavenArtifactVersion latest = null;
            for (Path versionDir : stream) {
                final MavenArtifactVersion next = new MavenArtifactVersion(versionDir.getFileName().toString());
                if (!range.includesVersion(next) || !next.isQualifierHigher(lowestQualifier, true)) {
                    continue;
                }
                if (latest == null || latest.compareTo(next) <= 0) {
                    latest = next;
                    latestDir = versionDir;
                }
            }
        } catch (Exception e) {
            throw new MavenUniverseException("Failed to determine the latest version of " + artifact.getCoordsAsString(), e);
        }
        if (latestDir == null) {
            throw new MavenUniverseException("Failed to determine the latest version of " + artifact.getCoordsAsString());
        }
        return latestDir;
    }
}
