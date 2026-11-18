# UI V1 — Design Handoff Baseline

Status: **UI V1** is the first frozen UI baseline. It is the single source of
truth for the UI V1 productization stage. A future **UI V2** may supersede it.

This document records only design decisions that are already confirmed. It must
not be expanded with speculative or undecided decisions.

---

## 1. Layout & Responsiveness

- **Mobile First.**
- **One responsive web**, not two separate sites (no separate mobile/desktop
  builds, no `m.` subdomain, no device detection).
- Design reference breakpoints:
  - **Mobile** `< 768px`
  - **Tablet** `768px – 1199px`
  - **Desktop** `>= 1200px`
- Visual direction: **mature / clean / modern / content-platform**.

## 2. Icons

- **Icon for operation, text for understanding.**
  Icons carry actions; text carries meaning. Do not rely on an icon alone where
  a label is needed to understand the function.
- Icon set: **Hugeicons Stroke Rounded**.
- Icons are colourless and use **`currentColor`** so they inherit the
  surrounding text colour.
- Icon geometry baseline: `viewBox="0 0 24 24"`, `fill="none"`,
  `stroke="currentColor"`, default `stroke-width="1.5"`, rounded line caps and
  joins. Colour must not be hardcoded inside the SVG.
- Do not replace the confirmed Hugeicons Stroke Rounded set with a different
  icon library.

## 3. Media

- **Do not disable the browser's native image context menu.**
- **Feed = Preview.**
- **Detail = Complete View.**
- **MediaFrame** reserves media dimensions before the media loads, to reduce
  page layout shift (CLS / jumping).
- **Feed media carousel** behaviour: horizontal **free drag with slight snap**.

## 4. Interactions

- **Favorite uses a Star**, not a Bookmark.
- **Empty State uses a Small Ghost.**
- **Ghost is not used for Loading or Error states.**

## 5. Settings

- **Mobile:** a list.
- **Desktop:** sidebar + detail pane.

## 6. Global Navigation — first batch

The first batch of Global Navigation destinations is:

1. Home
2. Search
3. Notifications
4. Profile
5. Settings

## 7. Product Honesty

- **Do not fabricate functionality the backend does not support.**
  UI must only expose capabilities that actually exist in the API/backend.

---

## 8. Scope Notes

- This baseline covers UI V1 only.
- Global Navigation, MediaFrame, carousel, and the
  Notifications / Profile / Settings pages are **defined here as targets**, but
  are implemented in later, separately-approved stages. Their presence in this
  document is not authorisation to build them in the current stage.
- Recorded first-batch navigation destinations are limited to the five listed in
  section 6. No additional design decisions are implied.
