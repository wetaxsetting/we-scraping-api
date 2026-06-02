# references/figma/

Figma reference assets and design-handoff notes.

## Purpose

Per memory `feedback_typography_source`: **Figma WB365** is the typography source of truth for `wecms-web` (size / weight / leading / family) — do NOT mix with JEX live-system sizes. This folder collects Figma URLs, exported screenshots, and the per-screen handoff notes.

## What goes here

- Per-screen handoff markdown with Figma URL + last-checked date
- Exported PNG / SVG assets used by `wecms-web` (only when they cannot live inside `wecms-web/public/`)
- Design-token extraction logs (typography, colour, spacing)

## Figma-first rule

Per memory `feedback_figma_first`: when given a Figma URL for a UI task, call `get_design_context` + `get_screenshot` **before** writing any plan — never infer UI from text or embedded PNGs alone.

## Related

- Workspace CLAUDE.md — Global Typography section (the live system is still the **typography contract** across all trees; Figma is the source for new design layouts).
- Parity audits: [`../audits/`](../audits/)
