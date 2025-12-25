# Development Builds

This fork publishes automated development builds to GitHub Releases for easy testing and distribution.

## Build Types

### Trunk Builds (Develop Branch)
- **Triggered by**: Pushes to `1.21.8-develop` branch
- **Version format**: `v7.51.2-MC1.21.8-develop-<short-sha>`
- **Example**: `v7.51.2-MC1.21.8-develop-abc1234`
- **Purpose**: Latest development version from the main development branch

### Pull Request Builds
- **Triggered by**: PR creation, updates, or reopens targeting `1.21.8-develop`
- **Version format**: `v7.51.2-MC1.21.8-pr<number>-<short-sha>`
- **Example**: `v7.51.2-MC1.21.8-pr42-xyz5678`
- **Purpose**: Test specific features from pull requests before merging

## Using Development Builds

### Option 1: Gradle with Ivy Resolver (Recommended)

Add the following to your `build.gradle`:

```gradle
repositories {
    ivy {
        url 'https://github.com/tolland/Wurst7/releases/download'
        patternLayout {
            artifact '[revision]/[module]-[revision](-[classifier]).[ext]'
        }
        metadataSources {
            artifact()
        }
    }
}

dependencies {
    // For a specific version
    modImplementation 'net.wurstclient:Wurst-Client:v7.51.2-MC1.21.8-develop-abc1234'

    // Or use a dynamic version (see below)
}
```

### Option 2: Gradle with Direct File Download

You can also download JARs directly and add them as file dependencies:

```gradle
dependencies {
    modImplementation files('libs/Wurst-Client-v7.51.2-MC1.21.8-develop-abc1234.jar')
}
```

## Finding the Latest Version

### For Trunk Builds

To always get the latest trunk build, you have a few options:

#### Manual Approach
1. Go to [Releases page](https://github.com/tolland/Wurst7/releases)
2. Find the latest release tagged with `-develop-`
3. Copy the version number

#### Automated Approach (Using GitHub API)

Create a script or use this in your CI:

```bash
# Get latest develop build version
LATEST_VERSION=$(curl -s "https://api.github.com/repos/tolland/Wurst7/releases" \
  | jq -r '[.[] | select(.tag_name | contains("-develop-"))][0].tag_name')

echo "Latest develop version: $LATEST_VERSION"
```

### For PR Builds

1. Navigate to the specific PR
2. Look for the automated comment with the build version
3. Or check the [Releases page](https://github.com/tolland/Wurst7/releases) and filter for `-pr<number>-`

## Gradle Version Resolution Strategies

### Using Changing Versions (Advanced)

If you want Gradle to check for updates more frequently:

```gradle
configurations.all {
    resolutionStrategy {
        cacheDynamicVersionsFor 10, 'minutes'
        cacheChangingModulesFor 0, 'seconds'
    }
}

dependencies {
    // Mark the dependency as changing
    modImplementation('net.wurstclient:Wurst-Client:v7.51.2-MC1.21.8-develop-abc1234') {
        changing = true
    }
}
```

### Version Catalogues (Gradle 7+)

In `gradle/libs.versions.toml`:

```toml
[versions]
wurst = "v7.51.2-MC1.21.8-develop-abc1234"

[libraries]
wurst-client = { module = "net.wurstclient:Wurst-Client", version.ref = "wurst" }
```

Then in `build.gradle`:

```gradle
dependencies {
    modImplementation libs.wurst.client
}
```

## Downloading Directly

### Via Browser
1. Go to [Releases](https://github.com/tolland/Wurst7/releases)
2. Find the version you want
3. Download the JAR file from the "Assets" section

### Via Command Line

```bash
# Download latest develop build
LATEST=$(curl -s "https://api.github.com/repos/tolland/Wurst7/releases" \
  | jq -r '[.[] | select(.tag_name | contains("-develop-"))][0].tag_name')

curl -L "https://github.com/tolland/Wurst7/releases/download/${LATEST}/Wurst-Client-${LATEST}.jar" \
  -o "Wurst-Client-${LATEST}.jar"
```

### Download Specific PR Build

```bash
# Replace 42 with your PR number and abc1234 with the commit hash
VERSION="v7.51.2-MC1.21.8-pr42-abc1234"

curl -L "https://github.com/tolland/Wurst7/releases/download/${VERSION}/Wurst-Client-${VERSION}.jar" \
  -o "Wurst-Client-${VERSION}.jar"
```

## Complete Gradle Example

Here's a complete `build.gradle` example that uses development builds:

```gradle
plugins {
    id 'fabric-loom' version '1.13-SNAPSHOT'
}

repositories {
    mavenCentral()

    // For Minecraft and Fabric
    maven {
        url 'https://maven.fabricmc.net/'
    }

    // For Wurst development builds
    ivy {
        url 'https://github.com/tolland/Wurst7/releases/download'
        patternLayout {
            artifact '[revision]/[module]-[revision](-[classifier]).[ext]'
        }
        metadataSources {
            artifact()
        }
    }
}

dependencies {
    minecraft 'com.mojang:minecraft:1.21.8'
    mappings loom.officialMojangMappings()
    modImplementation 'net.fabricmc:fabric-loader:0.18.1'

    // Wurst development build
    modImplementation 'net.wurstclient:Wurst-Client:v7.51.2-MC1.21.8-develop-abc1234'
}
```

## CI/CD Integration

### GitHub Actions Example

```yaml
name: Test with Wurst Dev Build

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v6

      - name: Get latest Wurst develop build
        id: wurst
        run: |
          LATEST=$(curl -s "https://api.github.com/repos/tolland/Wurst7/releases" \
            | jq -r '[.[] | select(.tag_name | contains("-develop-"))][0].tag_name')
          echo "version=$LATEST" >> $GITHUB_OUTPUT

      - name: Update Wurst version
        run: |
          sed -i "s/wurst_version=.*/wurst_version=${{ steps.wurst.outputs.version }}/" gradle.properties

      - name: Build
        run: ./gradlew build
```

## Troubleshooting

### Ivy Repository Not Found

If you see errors like "Could not resolve net.wurstclient:Wurst-Client":

1. Verify the release exists on the [Releases page](https://github.com/tolland/Wurst7/releases)
2. Check that the version string is exact (including `v` prefix)
3. Ensure your Ivy repository configuration matches the example above

### Gradle Cache Issues

If Gradle isn't picking up new versions:

```bash
# Clear Gradle cache
./gradlew clean --refresh-dependencies

# Or delete the cache manually
rm -rf ~/.gradle/caches/
```

### Wrong Version Downloaded

Make sure you're using the exact version string from the release tag, including:
- The `v` prefix
- The correct commit hash
- The `-develop-` or `-pr<number>-` suffix

## Release Retention

Development builds are retained indefinitely unless manually deleted. However:

- **Trunk builds**: Recommended to use the latest
- **PR builds**: May be deleted after PR is merged or closed

## Security Note

Development builds are **pre-release** versions and may contain:
- Untested features
- Breaking changes
- Security vulnerabilities

Only use development builds for testing purposes. For production, use official releases from the main Wurst repository.
