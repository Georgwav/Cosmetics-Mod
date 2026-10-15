#!/bin/sh
# Makes the "Sol for kept mods" pack's pictures from Sol's own icon set (run from the repository root).
set -e
GSON=$(find "$HOME/.gradle/caches" -name 'gson-2*.jar' | head -1)
OUT=$(mktemp -d)
javac -d "$OUT" -cp "$GSON" src/main/java/io/github/georgwav/cosmetics/sol/ui/kit/IconRaster.java tools/sol-assets/KeptModIcons.java
java -cp "$OUT:$GSON" KeptModIcons
rm -rf "$OUT"
