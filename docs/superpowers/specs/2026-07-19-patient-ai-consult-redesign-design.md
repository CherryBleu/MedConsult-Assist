# Patient AI Consult Redesign Design

> Date: 2026-07-19
> Scope: `frontend/src/views/patient/ai-service/AiConsult.vue`, `frontend/src/store/modules/aiChat.js`, AI mock data, and focused Playwright checks.
> Basis: `design-system/medconsult-assist/MASTER.md`, current Vue 3 + Pinia + Vue Router + Element Plus stack, and the RAG runtime baseline updated on 2026-07-19.

## 1. Problem

The patient AI consult page already sends symptom messages and renders RAG citations/vector matches, but the UI still behaves like a generic chat card:

- Medical safety summary is not visually and semantically separated from conversational text.
- Emergency/risk guidance is not guaranteed to appear before evidence/details.
- Error recovery is a plain AI message, not an announced recoverable state with retry.
- Mobile acceptance gates are under-tested for this page: no horizontal overflow, 44px touch targets, keyboard access, and evidence disclosure behavior.
- The page uses decorative glass/gradient styling that conflicts with the clinical operations design baseline.

## 2. Options Considered

| Option | Summary | Tradeoff |
| --- | --- | --- |
| A. Polish existing chat only | Keep current layout, add minor CSS and a few labels | Lowest risk, but it does not create a durable clinical AI interaction model |
| B. Safety-first consult workspace | Keep chat flow, add structured safety summary, evidence drawer/sections, retry and mobile gates | Best fit: meaningful UI refactor while staying scoped to one patient workflow |
| C. Full AI hub rewrite | Redesign consult, triage, imaging, and feedback together | Too broad for one commit; higher regression risk before current page gates are stable |

Recommended: Option B. It advances the required front-end refactor and AI/RAG objective without mixing unrelated patient pages.

## 3. Target Experience

The first screen remains the actual consult tool, not a landing page.

Desktop layout:

- Full-width unframed page area with two work zones:
  - Primary column: message timeline and input composer.
  - Supporting column: current safety summary, suggested departments, and session facts.
- Repeated messages and evidence entries may be cards with 6-8px radius. The page itself must not be a floating card.

Mobile layout:

- Single-column timeline with a sticky bottom composer.
- Safety summary appears directly above the related AI answer.
- Evidence is collapsed by default after the safety summary.
- Quick questions wrap without horizontal scrolling.

## 4. Message Model

Normalize each AI reply into a presentational shape:

```js
{
  role: 'ai',
  content,
  riskLevel,
  emergencyAdvice,
  possibleCauses,
  suggestedDepartments,
  answerSource,
  citations,
  vectorMatches,
  failed,
  retryText
}
```

Rules:

- `riskLevel` and `emergencyAdvice` drive the safety summary, not decorative color.
- Critical/high risk summaries use explicit heading text, an icon, and `role="alert"`.
- Non-critical result updates use `aria-live="polite"`.
- Evidence never appears before emergency advice.
- Empty evidence shows a clear degraded/uncertain state instead of silently hiding provenance.

## 5. Interaction Rules

- The input has a visible label and uses `textarea` semantics for longer symptom descriptions.
- `Enter` sends only when the composer behavior is clear; multiline input must remain possible. If `Ctrl+Enter` is used, expose it in `aria-keyshortcuts`, not visible instructional text.
- Send, retry, quick question, and evidence disclosure controls are at least 44x44px.
- Loading disables duplicate send while preserving button width and input state.
- Failed send preserves the patient message and offers a retry button tied to the original text.
- Evidence disclosure uses native `details/summary` or Element Plus components with synced `aria-expanded`.

## 6. Visual Direction

Use the existing clinical design system:

- Primary blue `#0284C7`, positive green `#15803D`, warning `#B45309`, critical red `#B91C1C`.
- No purple/pink AI gradients, glass blur, radial backgrounds, decorative blobs, or oversized hero treatment.
- Compact headings: page title 24px, panel titles 16-18px, body text 16px on patient-facing content.
- Animations are opacity/transform only, 150-220ms, disabled for `prefers-reduced-motion`.

## 7. Test Plan

Write failing Playwright tests before implementation:

1. Mobile consult page has no horizontal overflow at 390px and all composer/quick/retry controls are at least 44px.
2. A high-risk mock symptom renders an emergency safety summary before RAG evidence and exposes `role="alert"`.
3. RAG evidence still renders citation disease, matched field, vector match, and answer source after the refactor.
4. A simulated send failure preserves the user symptom, announces an error, and retry sends the same text.
5. Keyboard can focus and toggle evidence disclosure, then send another message without a trap.
6. Axe has no serious or critical violations on the patient AI consult page.

## 8. Out Of Scope

- Reworking triage, imaging, doctor AI tools, or admin AI governance pages in the same commit.
- Changing backend RAG scoring, prompt policy, or database schema.
- Running destructive data cleanup.
- Introducing a new UI library; use Vue 3, Pinia, Element Plus, and existing design tokens.

## 9. Implementation Order After Approval

1. Add focused Playwright tests and confirm the first test run fails for the expected missing behavior.
2. Refactor `aiChat.js` normalization and retry/error state.
3. Refactor `AiConsult.vue` template into safety summary, evidence, timeline, and composer sections.
4. Replace page-level glass/gradient styling with clinical tokens and responsive constraints.
5. Update mock AI responses for low-risk, high-risk, and fail-once scenarios.
6. Run focused E2E, axe check, production build, and document the changed frontend status.
