# Self-hosted application fonts

Unmodified variable TTF files from the official
[Google Fonts repository](https://github.com/google/fonts), pinned to commit
`bd8f81ddb5c74d5c8897b36ad88b440266245103` on 11 October 2026.
`provenance.json` records exact upstream URLs, SHA-256 hashes and shipped filenames.

- Plus Jakarta Sans: normal weights 200–800, by the Plus Jakarta Sans Project Authors.
- JetBrains Mono: normal weights 100–800, by the JetBrains Mono Project Authors.

Both use SIL Open Font License 1.1; their full copyright/license notices are
included alongside these files. No font modification/subsetting was performed.
`src/index.css` declares the faces; Vite bundles fingerprinted same-origin assets.
This restores the families previously requested through Google Fonts without
loosening CSP or making runtime font requests to a third party. Existing family,
weight and fallback tokens are unchanged. The original import requested normal
styles; browser synthesis of italic text remains the existing behavior.
