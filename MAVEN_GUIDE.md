# Maven Artifact & Publishing Guide

This guide explains how to use ChucK-Java as a dependency in your own Maven projects and how to publish new versions of the artifact.

## 📦 Using ChucK-Java in other projects

To use ChucK-Java as a dependency in your Maven project, add the following to your `pom.xml`:

```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/ludoch/chuckjava</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>org.chuck</groupId>
        <artifactId>chuck-java</artifactId>
        <version>1.0-SNAPSHOT</version>
    </dependency>
</dependencies>
```

## 🚀 Publishing a new version

Publishing is handled via GitHub Actions and must be triggered manually:

1.  (Optional) Update the version in `pom.xml` if you want to release a new version (e.g., from `1.0-SNAPSHOT` to `1.0.0`).
2.  Go to the **Actions** tab in the GitHub repository.
3.  Select the **Maven Publish** workflow.
4.  Click **Run workflow** and select the branch (usually `main`).
5.  This will build the project and deploy the artifact to GitHub Packages.
