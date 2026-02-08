# Example: Consuming Wurst Development Builds

This example shows how to use Wurst development builds in your Fabric mod project.

## Quick Start

1. **Copy the configuration files**:
   ```bash
   cp build.gradle gradle.properties /path/to/your/project/
   ```

2. **Update the Wurst version** in `gradle.properties`:
   - Go to [Wurst7 Releases](https://github.com/tolland/Wurst7/releases)
   - Find the latest development build (tagged with `-develop-` or `-pr`)
   - Copy the full version string (e.g., `v7.51.2-MC1.21.8-develop-abc1234`)
   - Update `wurst_version` in `gradle.properties`

3. **Build your project**:
   ```bash
   ./gradlew build
   ```

## How It Works

The configuration uses Gradle's Ivy repository support to fetch JARs directly from GitHub Releases:

```gradle
ivy {
    url = 'https://github.com/tolland/Wurst7/releases/download'
    patternLayout {
        artifact '[revision]/[module]-[revision](-[classifier]).[ext]'
    }
}
```

When you request `net.wurstclient:Wurst-Client:v7.51.2-MC1.21.8-develop-abc1234`, Gradle will:
1. Construct the URL: `https://github.com/tolland/Wurst7/releases/download/v7.51.2-MC1.21.8-develop-abc1234/Wurst-Client-v7.51.2-MC1.21.8-develop-abc1234.jar`
2. Download the JAR file
3. Cache it in `~/.gradle/caches/`

## Updating to Latest Version

### Manual Update

1. Check the [Releases page](https://github.com/tolland/Wurst7/releases)
2. Find the latest `-develop-` release
3. Update `wurst_version` in `gradle.properties`
4. Run `./gradlew build --refresh-dependencies`

### Automated Update (Using GitHub API)

Create a script `update-wurst.sh`:

```bash
#!/bin/bash

# Get latest develop build
LATEST=$(curl -s "https://api.github.com/repos/tolland/Wurst7/releases" \
  | jq -r '[.[] | select(.tag_name | contains("-develop-"))][0].tag_name')

if [ -z "$LATEST" ]; then
  echo "Error: Could not fetch latest version"
  exit 1
fi

echo "Latest version: $LATEST"

# Update gradle.properties
if [[ "$OSTYPE" == "darwin"* ]]; then
  # macOS
  sed -i '' "s/^wurst_version=.*/wurst_version=$LATEST/" gradle.properties
else
  # Linux
  sed -i "s/^wurst_version=.*/wurst_version=$LATEST/" gradle.properties
fi

echo "Updated gradle.properties to version $LATEST"

# Refresh dependencies
./gradlew build --refresh-dependencies
```

Make it executable and run:
```bash
chmod +x update-wurst.sh
./update-wurst.sh
```

## Using in CI/CD

Example GitHub Actions workflow:

```yaml
name: Build with Latest Wurst

on: [push, pull_request]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v6

      - name: Set up JDK 21
        uses: actions/setup-java@v5
        with:
          java-version: '21'
          distribution: 'microsoft'

      - name: Get latest Wurst develop build
        id: wurst
        run: |
          LATEST=$(curl -s "https://api.github.com/repos/tolland/Wurst7/releases" \
            | jq -r '[.[] | select(.tag_name | contains("-develop-"))][0].tag_name')
          echo "version=$LATEST" >> $GITHUB_OUTPUT
          echo "Latest Wurst version: $LATEST"

      - name: Update Wurst version
        run: |
          sed -i "s/^wurst_version=.*/wurst_version=${{ steps.wurst.outputs.version }}/" gradle.properties

      - name: Build
        run: ./gradlew build

      - name: Upload artifact
        uses: actions/upload-artifact@v4
        with:
          name: mod-jar
          path: build/libs/*.jar
```

## Troubleshooting

### "Could not resolve net.wurstclient:Wurst-Client"

**Problem**: Gradle can't find the dependency.

**Solutions**:
1. Verify the version exists: Check [Releases](https://github.com/tolland/Wurst7/releases)
2. Check the version string is exact (including the `v` prefix)
3. Try refreshing dependencies: `./gradlew build --refresh-dependencies`
4. Clear Gradle cache: `rm -rf ~/.gradle/caches/`

### Gradle Using Old Version

**Problem**: Gradle keeps using an old cached version.

**Solution**:
```bash
# Force refresh
./gradlew build --refresh-dependencies

# Or mark as changing in build.gradle (already included in example)
```

### Connection Timeout

**Problem**: Download times out.

**Solution**:
- Check your internet connection
- GitHub might be experiencing issues
- Try again later or download manually and use `files('libs/...')`

## More Information

See [DEVELOPMENT_BUILDS.md](../../DEVELOPMENT_BUILDS.md) for comprehensive documentation.
