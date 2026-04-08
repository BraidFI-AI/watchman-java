package io.braid.daywatcher.report;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.TextBlockParam;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.braid.daywatcher.config.AutoClearanceConfig;
import io.braid.daywatcher.config.SearchConfig;
import io.braid.daywatcher.config.SimilarityConfig;
import io.braid.daywatcher.config.WeightConfig;
import io.braid.daywatcher.model.ScoreBreakdown;
import io.braid.daywatcher.trace.ScoringEvent;
import io.braid.daywatcher.trace.ScoringTrace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Post-screening analysis tool. Takes a completed ScoringTrace and asks Claude
 * to explain exactly why the entity received its score.
 *
 * Claude is given the full source code of the scoring engine plus live runtime
 * configuration values so every claim it makes is traceable to a specific
 * method and config field. Nothing is hidden.
 *
 * Requires ANTHROPIC_API_KEY env var. Disabled gracefully if not set.
 * Source files are read from {@code watchman.narrative.source-root} (default: src/main/java).
 *
 * The system prompt (instructions + source code) is prompt-cached so repeated
 * calls during a tuning session are fast — only the trace and live config vary.
 */
@Service
public class ScoreNarrativeService {

    private static final Logger logger = LoggerFactory.getLogger(ScoreNarrativeService.class);

    /**
     * Source files fed to Claude so it understands every scoring decision path.
     * Listed in dependency order: scorer first, then its dependencies.
     */
    private static final List<String> SOURCE_FILES = List.of(
            "io/braid/daywatcher/search/EntityScorerImpl.java",
            "io/braid/daywatcher/config/WeightConfig.java",
            "io/braid/daywatcher/config/SimilarityConfig.java",
            "io/braid/daywatcher/config/SearchConfig.java",
            "io/braid/daywatcher/config/AutoClearanceConfig.java",
            "io/braid/daywatcher/similarity/JaroWinklerSimilarity.java",
            "io/braid/daywatcher/similarity/NameScorer.java",
            "io/braid/daywatcher/scorer/AddressComparer.java",
            "io/braid/daywatcher/scorer/DateComparer.java",
            "io/braid/daywatcher/scorer/AffiliationComparer.java",
            "io/braid/daywatcher/similarity/SupportingInfoComparer.java",
            "io/braid/daywatcher/similarity/EntityTitleComparer.java"
    );

    private static final String SYSTEM_INSTRUCTIONS = """
            You are an expert analyst for Day Watcher, a BSA/AML sanctions screening system.

            You have been given the complete Java source code for the scoring engine and all \
            configuration classes. Your job is to explain EXACTLY why an entity received its score.

            Style rules — be a code reviewer, not a narrator:
            - One bullet per phase. One sentence per bullet. No hedging, no speculation.
            - Every bullet MUST name the method called, the config field that controlled it, \
              and the live value. Format: `method()` — field=value — result.
            - If a phase score is 0.0 due to absent query data, say so in five words or fewer \
              (e.g., "No address provided — absence of evidence").
            - For aggregation, show the formula inline: bestNameScore=max(x,y)=z → z×nameWeight/nameWeight=1.0.
            - keySignals: the 3–5 factors that most determined the outcome. One line each.
            - tuningFlags: config fields that, if changed, would shift this result. One line each.

            Response format — return a JSON object ONLY, no markdown, no preamble:
            {
              "narrative": "- Bullet 1\\n- Bullet 2\\n- Bullet 3\\n...",
              "keySignals": [
                "method() — field=value — one-line impact",
                "..."
              ],
              "tuningFlags": [
                "fieldName (current: X) — raise/lower to change result in this direction",
                "..."
              ]
            }

            ## SCORING ENGINE SOURCE CODE

            """;

    private final WeightConfig weightConfig;
    private final SimilarityConfig similarityConfig;
    private final SearchConfig searchConfig;
    private final AutoClearanceConfig autoClearanceConfig;
    private final ObjectMapper objectMapper;
    private final String sourceRoot;
    private final AnthropicClient anthropicClient;

    public ScoreNarrativeService(
            WeightConfig weightConfig,
            SimilarityConfig similarityConfig,
            SearchConfig searchConfig,
            AutoClearanceConfig autoClearanceConfig,
            ObjectMapper objectMapper,
            @Value("${watchman.narrative.source-root:src/main/java}") String sourceRoot) {
        this.weightConfig = weightConfig;
        this.similarityConfig = similarityConfig;
        this.searchConfig = searchConfig;
        this.autoClearanceConfig = autoClearanceConfig;
        this.objectMapper = objectMapper;
        this.sourceRoot = sourceRoot;
        this.anthropicClient = initClient();
    }

    private AnthropicClient initClient() {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            logger.warn("ANTHROPIC_API_KEY not set — ScoreNarrativeService disabled");
            return null;
        }
        return AnthropicOkHttpClient.fromEnv();
    }

    /**
     * Analyze a scoring trace and return a full AI-generated explanation.
     *
     * @param trace     the completed scoring trace
     * @param queryName the name that was searched (for context in the prompt)
     */
    public ScoreNarrative analyze(ScoringTrace trace) {
        String queryName = trace.metadata() != null && trace.metadata().containsKey("entityName")
                ? (String) trace.metadata().get("entityName")
                : "unknown";
        if (anthropicClient == null) {
            return ScoreNarrative.unavailable(trace.sessionId(),
                    "ANTHROPIC_API_KEY not configured — set the env var and restart");
        }

        long start = System.currentTimeMillis();

        String sourceContext = loadSourceContext();
        String systemPrompt = SYSTEM_INSTRUCTIONS + sourceContext;
        String userMessage = buildUserMessage(queryName, trace);

        MessageCreateParams params = MessageCreateParams.builder()
                .model(Model.CLAUDE_OPUS_4_6)
                .maxTokens(8192L)
                // No thinking — we need strict JSON output; thinking blocks interfere with parsing.
                // Cache the system prompt (instructions + source code) — source rarely changes,
                // so repeated calls in a tuning session hit cache instead of re-sending all source.
                .systemOfTextBlockParams(List.of(
                        TextBlockParam.builder()
                                .text(systemPrompt)
                                .cacheControl(CacheControlEphemeral.builder().build())
                                .build()))
                .addUserMessage(userMessage)
                .build();

        logger.info("Calling Claude for narrative analysis: sessionId={}, queryName={}",
                trace.sessionId(), queryName);

        Message response = anthropicClient.messages().create(params);

        String text = response.content().stream()
                .flatMap(b -> b.text().stream())
                .map(t -> t.text())
                .collect(Collectors.joining());

        long analysisMs = System.currentTimeMillis() - start;
        logger.info("Narrative analysis complete: sessionId={}, analysisMs={}", trace.sessionId(), analysisMs);

        return parseResponse(trace.sessionId(), text, analysisMs);
    }

    /**
     * Loads each source file from the filesystem.
     * Files not found are noted in the output rather than causing failure —
     * this service runs locally where source is available, but degrades
     * gracefully in environments where it's not.
     */
    private String loadSourceContext() {
        StringBuilder sb = new StringBuilder();
        for (String relativePath : SOURCE_FILES) {
            Path filePath = Paths.get(sourceRoot, relativePath);
            String filename = Paths.get(relativePath).getFileName().toString();
            sb.append("### ").append(filename).append("\n```java\n");
            try {
                sb.append(Files.readString(filePath));
            } catch (IOException e) {
                sb.append("[File not found at ").append(filePath).append(" — source unavailable]");
                logger.warn("Source file not found for narrative context: {}", filePath);
            }
            sb.append("\n```\n\n");
        }
        return sb.toString();
    }

    /**
     * Serializes live WeightConfig and SimilarityConfig values so Claude
     * sees exactly what the system is configured to do right now —
     * not the defaults in the source, but the actual values at runtime.
     */
    private String buildConfigSnapshot() {
        StringBuilder sb = new StringBuilder();

        sb.append("### WeightConfig — watchman.weights.* (54 params)\n");
        appendConfigMap(sb, weightConfig);

        sb.append("\n### SimilarityConfig — watchman.similarity.* (18 params)\n");
        appendConfigMap(sb, similarityConfig);

        sb.append("\n### SearchConfig — watchman.search.* (7 params)\n");
        appendConfigMap(sb, searchConfig);

        sb.append("\n### AutoClearanceConfig — watchman.auto-clearance.* (3 params)\n");
        appendConfigMap(sb, autoClearanceConfig);

        return sb.toString();
    }

    private void appendConfigMap(StringBuilder sb, Object config) {
        try {
            Map<String, Object> map = objectMapper.convertValue(config, new TypeReference<>() {});
            new TreeMap<>(map).forEach((k, v) -> sb.append(k).append(": ").append(v).append("\n"));
        } catch (Exception e) {
            sb.append("[Error serializing config: ").append(e.getMessage()).append("]\n");
            logger.warn("Failed to serialize config for narrative: {}", e.getMessage());
        }
    }

    private String formatTrace(ScoringTrace trace, String queryName) {
        StringBuilder sb = new StringBuilder();

        sb.append("**Query:** \"").append(queryName).append("\"\n\n");

        Map<String, Object> meta = trace.metadata();
        if (meta != null && !meta.isEmpty()) {
            if (meta.containsKey("entityName")) {
                sb.append("**Matched Entity:** ").append(meta.get("entityName")).append("\n");
            }
            if (meta.containsKey("aliases")) {
                sb.append("**Entity Aliases:** ").append(meta.get("aliases")).append("\n");
            }
            if (meta.containsKey("matchedAlias")) {
                sb.append("**Matched Alias:** ").append(meta.get("matchedAlias")).append("\n");
            }
            sb.append("\n");
        }

        ScoreBreakdown b = trace.breakdown();
        if (b != null) {
            sb.append("**Score Breakdown:**\n");
            sb.append("  nameScore:          ").append(b.nameScore()).append("\n");
            sb.append("  altNamesScore:      ").append(b.altNamesScore()).append("\n");
            sb.append("  addressScore:       ").append(b.addressScore()).append("\n");
            sb.append("  governmentIdScore:  ").append(b.governmentIdScore()).append("\n");
            sb.append("  cryptoAddressScore: ").append(b.cryptoAddressScore()).append("\n");
            sb.append("  contactScore:       ").append(b.contactScore()).append("\n");
            sb.append("  dateScore:          ").append(b.dateScore()).append("\n");
            sb.append("  totalWeightedScore: ").append(b.totalWeightedScore()).append("\n\n");
        }

        sb.append("**Phase Events (in order):**\n");
        for (ScoringEvent event : trace.events()) {
            sb.append("  [").append(event.phase()).append("] ").append(event.description());
            if (!event.data().isEmpty()) {
                sb.append(" | ").append(event.data());
            }
            sb.append("\n");
        }

        sb.append("\nDuration: ").append(trace.durationMs()).append("ms\n");
        return sb.toString();
    }

    private String buildUserMessage(String queryName, ScoringTrace trace) {
        return "## CURRENT RUNTIME CONFIGURATION\n\n"
                + buildConfigSnapshot()
                + "\n## SCORING TRACE\n\n"
                + formatTrace(trace, queryName)
                + """

                ## TASK

                Return one bullet per scoring phase explaining why totalWeightedScore is what it is.
                Each bullet: method called — config field=live value — score/outcome.
                Zero scores: state the reason in ≤5 words.
                Aggregation bullet: show the formula with actual numbers inline.

                Return JSON only. No preamble.
                """;
    }

    private ScoreNarrative parseResponse(String sessionId, String responseText, long analysisMs) {
        String json = responseText.trim();

        // Strip thinking block XML if present (adaptive thinking can embed these in text content)
        json = json.replaceAll("(?s)<thinking>.*?</thinking>", "").trim();

        // Strip markdown code fences if present
        if (json.startsWith("```")) {
            int firstNewline = json.indexOf('\n');
            int lastFence = json.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                json = json.substring(firstNewline + 1, lastFence).trim();
            }
        }

        // Find JSON object if preamble text is present before the opening brace
        int jsonStart = json.indexOf('{');
        if (jsonStart > 0) {
            json = json.substring(jsonStart);
        }

        // Trim any trailing content after the closing brace
        int jsonEnd = json.lastIndexOf('}');
        if (jsonEnd >= 0 && jsonEnd < json.length() - 1) {
            json = json.substring(0, jsonEnd + 1);
        }

        try {
            NarrativeResponse nr = objectMapper.readValue(json, NarrativeResponse.class);
            return new ScoreNarrative(sessionId, nr.narrative(), nr.keySignals(), nr.tuningFlags(), analysisMs);
        } catch (Exception e) {
            logger.warn("Could not parse Claude response as JSON — returning raw text. Error: {}", e.getMessage());
            return new ScoreNarrative(sessionId, responseText, List.of(), List.of(), analysisMs);
        }
    }

    private record NarrativeResponse(
            String narrative,
            @JsonProperty("keySignals") List<String> keySignals,
            @JsonProperty("tuningFlags") List<String> tuningFlags
    ) {}
}
