# Handoff v6

This change is stacked on `feat/handoff-v5` / PR #77. It does not replace the native converters, provider, draft persistence or stable pack identity.

## Implementation

- Editor uses five columns at ordinary phone widths, more on wide screens and fewer for narrow / enlarged-text configurations. Sizing uses measured content width.
- Tile controls use vector icons and small surfaces. Full-size cover/delete actions are available after selecting a tile; accessibility actions expose delete, cover and reorder. This avoids overlapping 48dp controls on a roughly 60dp tile.
- Header actions occupy a layout slot. Count/subtitle have a separate row and cannot collide with settings or overflow.
- Editor and build actions are outside the scroll container; existing root system-bar / IME inset handling is retained.
- Build has compact summary/progress, actual counts and Overview / Files / Journal. Overview prioritizes errors/current/skipped items, then latest successes, and never exceeds six rows. Files contains all items. Rows show source types, not generated names or codec diagnostics.
- Journal records failures, retries, skipped items, cancellation and finalization, with a bounded history.
- Presentation receives genuine item-start and completion events. Fast successes share a 1.2–1.5-second batch reveal deadline followed by a 250ms completion phase. No worker sleeps, chained per-file delays or fake success. Errors/cancellation bypass holds. Animator scale zero removes decorative holds and stagger. Tests use a deterministic clock.
- Motion includes compact dialog entry, search reveal, bounded grid insertion stagger, reorder reflow, cover emphasis, sliding segment selection, theme fade, and sync indicator. Final completion has at most one haptic.

## Automated checks

- Existing v5 integration suite remains in place, including mixed static/animated conversion, persistence and WhatsApp version-state transitions.
- New JVM coverage: normal/wide/narrow/large-font grid policy; 30-file bounded reveal; no hold before actual start; immediate interruption/reduced motion; slow batch and reset.
- New Android coverage: 30-item overview limit/full files; exact stats; clean journal; no technical output labels; sticky actions and non-overlapping editor header.
- CI also exports `v6-screenshots` in instrumentation reports: editor with 30 items, build overview and files.
- Run lint, JVM tests, debug/release builds and complete API35 instrumentation suite before merge. See PR checks for final results.

## Device acceptance still required

CI disables system animations; clock-policy tests verify timing logic, not the perceived animation on a physical device. Check motion at normal/reduced animator scale, Samsung gallery/document chooser, large display/text scaling and keyboard/insets on a phone. Verify add/update/cancel in installed WhatsApp and WhatsApp Business, preserving the same pack ID and refreshing its image-data version. No claim of a real WhatsApp round-trip is made by emulator-only checks.
