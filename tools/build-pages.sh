#!/usr/bin/env bash
# Puts together the course website for GitHub Pages, in the folder given (default _site):
#   site/            the website itself
#   day-*/README.md  the lessons, which the website fetches as they are
#   index.html       sends the address with no path to site/
#   404.html         the page for an address that doesn't exist
# Nothing else from the repository is published. Run it locally and serve the folder with
#   jwebserver -d "$PWD/_site"
set -euo pipefail

cd "$(dirname "$0")/.."
out="${1:-_site}"

rm -rf "$out"
mkdir -p "$out"
cp -R site "$out/site"
rm -f "$out/site/DESIGN.md"
for day in day-*/; do
  mkdir -p "$out/$day"
  cp "$day/README.md" "$out/$day/README.md"
done
cp 404.html "$out/404.html"
cat > "$out/index.html" <<'HTML'
<!doctype html>
<meta charset="utf-8">
<title>Box Office in 30 Days</title>
<meta http-equiv="refresh" content="0; url=site/">
<link rel="canonical" href="site/">
<p><a href="site/">Box Office in 30 Days</a></p>
HTML

echo "Built $out: the website and $(ls -d "$out"/day-*/ | wc -l) lessons"
