#!/bin/sh
# Serve the built page. Build first: npm run release (or npm run watch,
# which serves on 8379 itself).
cd "$(dirname "$0")/.." && exec python3 -m http.server "${PORT:-8379}" --directory public
