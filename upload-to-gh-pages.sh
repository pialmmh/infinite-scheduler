#!/bin/bash
set -e

# === CONFIG ===
ARTIFACT_ID="infinite-scheduler"
GROUP_ID="com.telcobright.scheduler"
VERSION="1.0.0"

PROJECT_DIR=~/telcobright-projects/$ARTIFACT_ID
REPO_DIR=~/telcobright-projects/telcobright-maven-repo
GROUP_PATH=$(echo "$GROUP_ID" | tr '.' '/')/$ARTIFACT_ID/$VERSION
TARGET_DIR="$REPO_DIR/$GROUP_PATH"

# === BUILD JAR ===
echo "📦 Building $ARTIFACT_ID version $VERSION..."
cd "$PROJECT_DIR"
mvn clean package -DskipTests

# === COPY POM to target directory ===
echo "📝 Generating .pom file..."
cp pom.xml "target/$ARTIFACT_ID-$VERSION.pom"

# === COPY ARTIFACTS ===
echo "📁 Copying artifacts to local Maven repo structure..."
mkdir -p "$TARGET_DIR"
cp "target/$ARTIFACT_ID-$VERSION.jar" "$TARGET_DIR/"
cp "target/$ARTIFACT_ID-$VERSION.pom" "$TARGET_DIR/"

# === GIT COMMIT AND PUSH ===
cd "$REPO_DIR"
git add "$GROUP_PATH"
git commit -m "Update $ARTIFACT_ID $VERSION on $(date)"
git push origin gh-pages

# === DONE ===
echo "✅ Upload complete."
echo "🌐 Published at: https://pialmmh.github.io/telcobright-maven-repo/$GROUP_PATH/"
