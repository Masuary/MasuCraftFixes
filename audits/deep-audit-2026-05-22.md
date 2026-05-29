# Deep Audit - MasuCraftFixes (wolds branch changes)

**Scope:** changes | **Date:** 2026-05-22 | **SHA:** 539b667 (base) + uncommitted | **Verdict:** GREEN

Snapshot from one audit pass. Findings reflect the code as of the SHA above; line numbers and severities will drift as fixes land. Do not treat this as a living spec - re-run `/deep-audit` for a current view.

## MEDIUM

- [x] [src/main/java/com/masuary/masucraftfixes/mixin/TieredSkillMixin.java:26] All players' TieredSkills update on the same tick - `getGameTime() % 20L` is global, creating micro-spikes. Use per-player offset like `(getGameTime() + player.getId()) % 20L`.

## LOW

- [ ] [src/main/java/com/masuary/masucraftfixes/mixin/TieredSkillMixin.java:20] Gear-granted bonusTier for unlearned skills (tier 0) no longer stored preemptively - gets set within 1 second of learning. Imperceptible behavior change.
- [x] [gradle/wrapper/gradle-wrapper.properties] Gradle 7.6.4 is EOL. Works but won't receive further fixes. (Accepted - upgrading further would require ForgeGradle compatibility work)

## Recommended next actions

1. Apply per-player tick offset to distribute load
2. Deploy and re-profile to measure improvement
3. ~~Bump mod version from 1.5.0~~ Done - bumped to 1.6.0
