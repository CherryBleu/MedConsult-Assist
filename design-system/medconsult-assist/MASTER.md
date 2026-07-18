# MedConsult Assist Design System

> Page-specific rules in `pages/<page-name>.md` override this file. This is an
> application design system, not a landing-page pattern.

## Product Context

- Product: role-based medical consultation and hospital operations application.
- Audiences: patients, doctors, pharmacy administrators, and hospital administrators.
- Primary goals: clinical safety, fast scanning, predictable workflows, and accessible use.
- Visual direction: accessible clinical operations. Patient views may use more breathing room;
  staff views use higher information density without reducing touch or text legibility.
- Stack: Vue 3, Pinia, Vue Router, Element Plus.

## Design Tokens

### Color

| Role | Value | Usage |
| --- | --- | --- |
| Primary | `#0284C7` | Primary commands, active navigation, links |
| Primary dark | `#075985` | Hover/pressed states and high-contrast text |
| Positive | `#15803D` | Confirmed, completed, clinically stable |
| Warning | `#B45309` | Attention required, pending review |
| Critical | `#B91C1C` | Emergency guidance, destructive actions, failures |
| Information | `#0369A1` | Neutral clinical guidance and system notices |
| Page | `#F8FAFC` | Main page background |
| Surface | `#FFFFFF` | Tool surfaces, dialogs, repeated records |
| Text | `#0F172A` | Primary text |
| Text muted | `#475569` | Secondary text while preserving contrast |
| Border | `#CBD5E1` | Dividers, inputs, table boundaries |
| Focus | `#0EA5E9` | Visible keyboard focus ring |

Risk must never be conveyed by color alone. Pair status colors with an icon and explicit text.

### Typography

Use the local system stack to keep Chinese text reliable and avoid a render-blocking font request:

```css
font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "Microsoft YaHei", sans-serif;
```

- Body: 16px / 1.5 on patient workflows; 14px / 1.5 on dense staff tables.
- Compact metadata may use 12px only when it is supplementary, never for primary content.
- Page title: 24px / 1.3. Panel title: 16-18px / 1.4.
- Letter spacing is always `0`.
- Do not scale font size with viewport width.

### Layout And Spacing

Use a 4px base scale: `4, 8, 12, 16, 24, 32, 48`.

- Repeated operational records may be cards; page sections remain unframed full-width layouts.
- Do not nest cards or turn every section into a floating container.
- Card/tool radius: 6-8px. Input/button radius: 4-6px. Modal radius: 8px.
- Touch targets: at least 44x44px, with at least 8px between adjacent targets.
- Stable grids use explicit min/max tracks to prevent state changes from shifting layout.
- Staff tables favor responsive columns, priority labels, pagination, and mobile record layouts.

### Elevation

Use borders for structure and reserve shadow for overlays:

```css
--shadow-surface: 0 1px 2px rgba(15, 23, 42, 0.06);
--shadow-overlay: 0 12px 28px rgba(15, 23, 42, 0.16);
```

No glassmorphism, backdrop blur, radial decoration, gradient orbs, or colored card shadows.

## Component Rules

### Commands

- Use Element Plus icons for familiar actions; icon-only buttons require `aria-label` and tooltip.
- Primary buttons are limited to the main action in a local workflow.
- Destructive commands require explicit wording and confirmation.
- Loading disables duplicate submission and preserves the button width.

### Navigation

- Desktop uses a stable side navigation; mobile uses a drawer rather than a permanently collapsed rail.
- The menu trigger is a semantic button with dynamic `aria-expanded` and `aria-controls`.
- Provide a skip link to the main content and a predictable route-level back path where needed.
- Notification items, profile controls, and menu actions must support keyboard activation.

### Clinical Risk

- Emergency advice appears before explanatory or RAG evidence content.
- Critical state uses a visible heading, emergency icon, explicit action, and telephone guidance.
- Suggested departments and possible causes are structured lists, not hidden in prose.
- AI evidence is collapsible after the safety summary; uncertainty and source are always visible.
- AI output must state that it does not replace clinician diagnosis.

### Forms And Feedback

- Every input has a visible label; placeholder text is supplemental.
- Validation appears next to the field and moves focus to the first invalid control on submit.
- All async views implement loading, empty, error, retry, and last-success states.
- Preserve user input and last successful data after recoverable failures.

### Medical Images

- Image previews require meaningful `alt` text.
- Canvas annotations require an adjacent textual finding list containing label, confidence, and region.
- Reserve image dimensions to prevent layout shift.

## Motion

- Interaction feedback: 150-220ms. Drawer/dialog: 180-260ms. No animation above 300ms in routine workflows.
- Animate opacity and transform only; avoid width/height animation and decorative looping motion.
- Use motion for state change, navigation continuity, loading, and confirmation only.
- `prefers-reduced-motion: reduce` disables nonessential transitions and smooth scrolling.

## Responsive Contract

Verify at `375`, `768`, `1024`, and `1440` CSS pixels.

- No page-level horizontal scrolling.
- Mobile navigation is fully operable without hover.
- Text and controls never overlap or truncate clinical risk wording.
- Fixed-format boards, counters, and toolbars use stable dimensions and responsive constraints.
- Tables use prioritized columns or record layouts below 768px; do not merely shrink typography.

## Accessibility Contract

- WCAG AA contrast, visible focus, semantic headings, labels, and landmarks.
- All workflows are keyboard operable with logical focus order and no keyboard traps.
- Icon-only controls expose accessible names.
- Live loading/error/success feedback uses appropriate ARIA live regions without duplicate announcements.
- Automated axe checks supplement, but do not replace, keyboard and screen-reader-oriented review.

## Pre-Delivery Checklist

- [ ] Critical medical guidance is visible before evidence/details.
- [ ] All actions work with keyboard and expose accessible names.
- [ ] Touch targets are at least 44x44px.
- [ ] Loading, empty, error, retry, and success states are implemented.
- [ ] Images and canvas findings have text alternatives.
- [ ] Reduced-motion preference is respected.
- [ ] No nested cards, decorative blobs, glass blur, or layout-shifting hover effects.
- [ ] No horizontal overflow at 375, 768, 1024, or 1440px.
- [ ] Desktop and mobile screenshots show no overlap or clipped labels.
