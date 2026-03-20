package io.braid.daywatcher.search;

import io.braid.daywatcher.model.ScoreBreakdown;

/**
 * Lightweight result of scoring one entity: breakdown + matched alias.
 *
 * Replaces ScoringContext.enabled() metadata extraction in the hot search path.
 * Using ScoringContext.enabled() per entity allocates an ArrayList(100), HashMap,
 * and Instant.now() for every candidate — pure overhead when only matchedAlias is needed.
 */
record ScoringResult(ScoreBreakdown breakdown, String matchedAlias) {}
