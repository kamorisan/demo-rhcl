package com.redhat.rhcl.chatbot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Client for OpenAI-compatible LLM API.
 * Used to format MCP tool results into natural language responses.
 */
@ApplicationScoped
@Unremovable
public class OpenAiClient {

    private static final Logger LOG = Logger.getLogger(OpenAiClient.class);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final JsonUtil json;

    @ConfigProperty(name = "rhcl.openai.base-url")
    String baseUrl;

    @ConfigProperty(name = "rhcl.openai.model")
    String model;

    @ConfigProperty(name = "rhcl.openai.api-key")
    String apiKey;

    @ConfigProperty(name = "rhcl.openai.timeout-seconds", defaultValue = "15")
    int timeoutSeconds;

    public OpenAiClient(JsonUtil json) {
        this.json = json;
    }

    /**
     * Check if LLM API is enabled (API key is set).
     *
     * @return true if enabled
     */
    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Send a chat completion request to LLM API.
     *
     * @param messages Array of message objects [{"role": "...", "content": "..."}]
     * @return LLM response JSON
     * @throws Exception if request fails
     */
    public JsonNode chat(ArrayNode messages) throws Exception {
        if (!enabled()) {
            throw new IllegalStateException("OpenAI API key not configured");
        }

        ObjectNode req = json.obj();
        req.put("model", model);
        req.set("messages", messages);
        req.put("stream", false);
        req.put("temperature", 0.1);  // Lower temperature to reduce reasoning output
        req.put("max_tokens", 5000);  // Allow longer responses for multiple events

        LOG.debug(String.format("LLM chat request: model=%s messages=%d", model, messages.size()));

        URI uri = URI.create(stripTrailingSlash(baseUrl) + "/chat/completions");

        HttpRequest httpReq = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(Math.max(5, timeoutSeconds)))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(json.write(req)))
                .build();

        HttpResponse<String> res = http.send(httpReq, HttpResponse.BodyHandlers.ofString());

        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            LOG.errorf("LLM chat failed: status=%d body=%s", res.statusCode(), safe(res.body()));
            throw new RuntimeException("LLM API error: status=" + res.statusCode());
        }

        LOG.debug(String.format("LLM chat response: status=%d bodyLength=%d", res.statusCode(), res.body().length()));
        return json.readTree(res.body());
    }

    /**
     * Format tool result into natural language using LLM.
     *
     * @param userQuestion Original user question
     * @param toolName     MCP tool name used
     * @param toolResult   Tool result (JSON string)
     * @param language     Language hint ("ja" or "en")
     * @return Natural language response
     * @throws Exception if formatting fails
     */
    public String formatToolResult(String userQuestion, String toolName, String toolResult, String language) throws Exception {
        ArrayNode messages = json.arr();

        // System prompt with structured formatting instructions
        ObjectNode systemMsg = json.obj();
        systemMsg.put("role", "system");
        String systemPrompt = language.equals("ja")
                ? """
                あなたはスポーツ情報アシスタントです。与えられた試合データを以下のフォーマットで整形してください。

                フォーマット例:
                - [Final] · 2026-06-11 09:30 GMT
                  away: Team A 105
                  home: Team B 104

                - [Live] · 2026-06-09 09:30 GMT
                  away: Team C 58
                  home: Team D 62

                - [Upcoming] · 2026-06-14 09:30 GMT
                  away: Team E 0
                  home: Team F 0

                重要なポイント:
                - 各試合を「-」で始める
                - **試合と試合の間には必ず空行を1行入れる**
                - 試合状態の判定:
                  * status.typeId が "3" の場合: [Final]
                  * status.typeId が "2" の場合: [Live]
                  * status.typeId が "1" の場合: [Upcoming]
                - 日時はGMT表記: YYYY-MM-DD HH:MM GMT
                - スコアは数値のみ（[]で囲まない）
                - away（アウェイ）とhome（ホーム）を明確に分ける
                - スコアは必ず正確に表示（JSONから取得した実際のスコア）
                - 簡潔で見やすく整形
                - 試合はJSONに記載されている順序で表示（既に日付順にソート済み）

                スポーツ別の注意点:
                - NBA/NFL/NHL: 引き分けなし、勝敗が決まる
                - サッカー（Premier League/La Liga）: 引き分けあり、両チーム同点の場合もある
                - サッカーの場合、勝者判定は不要（スコアのみ表示）

                試合がない場合:
                - プレミアリーグ（EPL）: シーズンは8月〜5月（6月〜7月はオフシーズン）
                - ラ・リーガ（LaLiga）: シーズンは8月〜5月（6月〜7月はオフシーズン）
                - NBA: シーズンは10月〜6月（プレイオフファイナルは6月）
                - NFL: シーズンは9月〜2月（スーパーボウルは2月、3月〜8月はオフシーズン）
                - NHL: シーズンは10月〜6月（プレイオフファイナルは6月）

                試合データが空の場合は、「該当期間に試合がありません。」とシーズン情報を簡潔に説明してください。

                ESPN APIデータ構造の理解（実際のJSONパス）:
                - date: 試合日時（ISO 8601形式 UTC）→ GMT表記に変換
                - competitors[]: 2チーム（away と home）
                - competitors[].team: チーム名
                - competitors[].score: スコア（文字列）
                - competitors[].homeAway: "away" または "home"
                - status.typeId: **重要** 試合状態ID
                  * "3" = Final（試合終了）
                  * "2" = Live（試合中）
                  * "1" = Upcoming（試合前）
                - status.shortDetail: 状態の短い説明（参考用）

                日時とスコアの処理:
                - 日時: ISO 8601形式（2026-06-11T00:30Z）をGMT表記（2026-06-11 00:30 GMT）に変換
                - スコア: 数値のみ表示、[]で囲まない
                - 試合前（Upcoming）の場合、score は "0"

                重要な指示:
                - JSON内の**全ての試合**を表示してください（省略しないこと）
                - 試合数のカウントは不要です（別途表示されます）
                - 推論プロセス、説明、前置きは一切含めないでください
                - 英語での説明や "Alright", "Let's", "Looking at", "Found" などのフレーズは使用禁止です
                - 日本語のユーザーには日本語のみで回答してください
                - 「-」で始まる試合データのフォーマットをすぐに開始してください
                """
                : """
                You are a sports information assistant. Format the game data using this structure:

                Format example:
                - [Final] · 2026-06-11 09:30 GMT
                  away: Team A 105
                  home: Team B 104

                - [Live] · 2026-06-09 09:30 GMT
                  away: Team C 58
                  home: Team D 62

                - [Upcoming] · 2026-06-14 09:30 GMT
                  away: Team E 0
                  home: Team F 0

                Key points:
                - Start each game with "-"
                - **Add a blank line between each game**
                - Game status determination:
                  * status.typeId is "3": [Final]
                  * status.typeId is "2": [Live]
                  * status.typeId is "1": [Upcoming]
                - Date/time in GMT format: YYYY-MM-DD HH:MM GMT
                - Score as number only (no brackets)
                - Clearly separate away and home teams
                - Show 0 for upcoming games
                - Keep it concise and readable
                - Display games in the order they appear in JSON (already sorted by date)

                Sport-specific notes:
                - NBA/NFL/NHL: No draws, there's always a winner
                - Soccer (Premier League/La Liga): Draws are possible, both teams can have equal scores
                - For soccer, no need to indicate winner (just show scores)

                Season schedules (when no events found):
                - Premier League (EPL): Season runs August-May (June-July is off-season)
                - La Liga (LaLiga): Season runs August-May (June-July is off-season)
                - NBA: Season runs October-June (playoffs/finals in June)
                - NFL: Season runs September-February (Super Bowl in February, March-August is off-season)
                - NHL: Season runs October-June (playoffs/finals in June)

                If no events are found, explain "No events in the requested period." and briefly mention the season schedule.

                ESPN API data structure (actual JSON paths):
                - date: Game date/time (ISO 8601 UTC) → convert to GMT format
                - competitors[]: Two teams (away and home)
                - competitors[].team: Team name
                - competitors[].score: Score (string type)
                - competitors[].homeAway: "away" or "home"
                - status.typeId: **IMPORTANT** Game status ID
                  * "3" = Final (game ended)
                  * "2" = Live (in progress)
                  * "1" = Upcoming (scheduled)
                - status.shortDetail: Short status description (reference only)

                Date/time and score processing:
                - Date/time: Convert ISO 8601 (2026-06-11T00:30Z) to GMT format (2026-06-11 00:30 GMT)
                - Score: Display number only, no brackets
                - For upcoming games, score is "0"

                Important instructions:
                - Display **ALL events** from the JSON (do NOT omit any)
                - Event count is not needed (displayed separately)
                - DO NOT include any reasoning, explanation, or preamble
                - Phrases like "Alright", "Let's", "Looking at", "First", "Next", "Found" are FORBIDDEN
                - Start immediately with the game data formatted with "-"
                - Output only the formatted game data
                - For English users, respond in English only
                """;
        systemMsg.put("content", systemPrompt);
        messages.add(systemMsg);

        // Count events in the JSON
        int eventCount = countEvents(toolResult);
        LOG.infof("Counted %d events in JSON (length=%d bytes) to pass to LLM", eventCount, toolResult.length());

        // User prompt with explicit formatting instruction
        ObjectNode userMsg = json.obj();
        userMsg.put("role", "user");
        String formatInstruction = language.equals("ja")
                ? String.format("\n\n出力ルール:\n1. JSONには%d試合が含まれています - 全て表示してください\n2. **各試合のstatus.typeIdを必ず確認して状態を判定してください**\n   - typeId=\"3\" → [Final]\n   - typeId=\"2\" → [Live]\n   - typeId=\"1\" → [Upcoming]\n3. 「-」で始まる試合データから出力を開始すること（試合数は不要）\n4. 推論、説明、前置きは絶対に含めないこと\n5. 英語の文章は一切使用禁止\n6. すぐにフォーマットされたデータのみを出力すること", eventCount)
                : String.format("\n\nOutput rules:\n1. The JSON contains %d events - display ALL of them\n2. **Check status.typeId for EACH game to determine its status**\n   - typeId=\"3\" → [Final]\n   - typeId=\"2\" → [Live]\n   - typeId=\"1\" → [Upcoming]\n3. Start immediately with the game data formatted with \"-\" (event count not needed)\n4. Absolutely NO reasoning, explanation, or preamble\n5. Output ONLY the formatted data immediately\n6. Do NOT use any explanatory sentences", eventCount);

        String userPrompt = String.format(
                "User question: %s\n\nTool used: %s\n\nTool result (JSON):\n%s%s",
                safe(userQuestion, 200),
                toolName,
                safe(toolResult, 100000),  // Allow full JSON after filtering (max 20 events)
                formatInstruction
        );
        userMsg.put("content", userPrompt);
        messages.add(userMsg);

        LOG.debugf("Formatting tool result with LLM: toolName=%s language=%s", toolName, language);

        JsonNode llmRes = chat(messages);
        String answer = llmRes.at("/choices/0/message/content").asText("");

        if (answer.isBlank()) {
            LOG.warn("LLM returned empty answer, falling back to raw tool result");
            return toolResult;
        }

        // Clean up LLM response: remove <think> tags and internal reasoning
        String cleanAnswer = sanitizeAnswer(answer);

        return cleanAnswer.trim();
    }

    /**
     * Remove internal reasoning tags and extract final answer from LLM response.
     * DeepSeek R1 and similar models output reasoning in various formats.
     *
     * @param text Raw LLM response
     * @return Cleaned response text
     */
    private static String sanitizeAnswer(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String s = text;

        // Remove <think>...</think> blocks (including content)
        s = s.replaceAll("(?s)<think>.*?</think>", "");

        // Remove orphaned <think> or </think> tags
        s = s.replace("<think>", "").replace("</think>", "");

        // Strategy 1: Find the FIRST occurrence of answer start patterns
        // Remove everything before the first game
        String[] answerStartPatterns = {
            "- [Final]",
            "- [Live]",
            "- [Upcoming]",
            "該当期間に試合がありません",
            "この期間で試合はありませんでした",
            "No events in the requested period"
        };

        int bestIdx = -1;

        // Find FIRST occurrence of any pattern
        for (String pattern : answerStartPatterns) {
            int idx = s.indexOf(pattern);
            if (idx >= 0 && (bestIdx < 0 || idx < bestIdx)) {
                bestIdx = idx;
            }
        }

        if (bestIdx > 0) {
            s = s.substring(bestIdx);
        }

        // Remove duplicate first game (if reasoning text appears between duplicates)
        // Pattern: "- [Final] ... \n<reasoning text>\n- [Final] ..." → keep only after second occurrence
        if (s.startsWith("- [Final]") || s.startsWith("- [Live]") || s.startsWith("- [Upcoming]")) {
            // Find the second occurrence of the same pattern
            String firstLine = s.substring(0, Math.min(50, s.length()));
            int secondOccurrence = s.indexOf("\n\n- [", 10);  // Look for next game after some text
            if (secondOccurrence > 0 && secondOccurrence < 500) {  // Within first 500 chars
                // Check if there's reasoning text between first and second game
                String between = s.substring(0, secondOccurrence);
                if (between.contains("I should") || between.contains("I'll") || between.contains("also check")) {
                    // Remove everything before the second game
                    s = s.substring(secondOccurrence + 2);  // +2 to skip "\n\n"
                }
            }
        }

        // Special handling for "no games" case: Keep only the LAST occurrence
        if (s.contains("該当期間に試合がありません")) {
            // Find the LAST occurrence of the no-games message
            int lastIdx = s.lastIndexOf("該当期間に試合がありません");
            if (lastIdx > 0) {
                // Keep from the last occurrence onward
                s = s.substring(lastIdx);
            }
        } else if (s.contains("この期間で試合はありませんでした")) {
            int lastIdx = s.lastIndexOf("この期間で試合はありませんでした");
            if (lastIdx > 0) {
                s = s.substring(lastIdx);
            }
        }

        // Remove intermediate reasoning text (between games)
        // Pattern: any paragraph that contains reasoning keywords
        s = s.replaceAll("(?m)^[^-][^\n]*(?:I'll repeat|I'll ensure|ensuring|Finally,|Lastly,)[^\n]*\n+", "");

        // Remove common reasoning phrases and enumeration sentences
        String[] reasoningPhrasesToRemove = {
            // Continuation phrases
            "I'll repeat this process",
            "ensuring the correct team",
            "their scores are accurately",
            "Finally,",
            "Lastly,",
            // No-games responses
            "最後に、フォーマット例に従い、",
            "これにより、ユーザーが",
            "試合がない旨を日本語で簡潔に説明します。",
            "理解しやすい形で情報提供できます。",
            // Status mapping reasoning
            "- \"3\" → [Final]",
            "- \"2\" → [Live]",
            "- \"1\" → [Upcoming]",
            "Hmm, the JSON here",
            "That's a problem because",
            "according to the user's instructions",
            "I should map based on",
            "Since none of the events",
            "I might have to infer",
            "The shortDetail is",
            "which usually stands for",
            "indicating the match",
            "So, I'll map these to",
            "Now, looking at the competitors",
            "each has a homeAway field",
            "I need to list the away team first",
            "For example, in the first event",
            "So, it should be formatted as",
            "I'll need to do this for each",
            "Also, the user mentioned",
            "the JSON is already sorted",
            "so I don't need to reorder",
            "Another point is that",
            "I'll make sure to format",
            "without any additional",
            "Lastly, since all",
            "which runs from",
            "the user is within",
            "So, I don't need to add",
            "Putting it all together",
            "I'll process each event",
            "convert the date",
            "determine the status",
            "format the teams and scores",
            "I'll ensure that each entry",
            "followed by the status",
            "Each entry will be separated",
            "as per the instructions",
            // English reasoning about formatting
            "I also need to format",
            "I also need to",
            "I need to",
            "I'll make sure",
            "I'll process",
            "I'll format",
            "I'll compile",
            "Looking at the JSON,",
            "Looking at",
            "Now, I'll",
            "Finally, I'll",
            // Enumeration patterns
            "For the first event",
            "The first event",
            "The second event",
            "The third event",
            "The fourth",
            "The fifth",
            "For each event,",
            "Each entry"
        };

        for (String pattern : reasoningPhrasesToRemove) {
            // Remove sentences containing these patterns
            int idx = s.indexOf(pattern);
            while (idx >= 0 && idx < 500) {  // Only check first 500 chars
                int endIdx = s.indexOf("\n\n", idx);
                if (endIdx < 0) {
                    endIdx = s.indexOf(". ", idx);
                    if (endIdx >= 0) endIdx += 2;
                }

                if (endIdx > idx && endIdx < s.length()) {
                    s = s.substring(0, idx) + s.substring(endIdx);
                } else if (endIdx < 0) {
                    // Remove to the next hyphen (start of actual content)
                    int nextHyphen = s.indexOf("\n-", idx);
                    if (nextHyphen > idx) {
                        s = s.substring(0, idx) + s.substring(nextHyphen + 1);
                        break;
                    }
                }

                idx = s.indexOf(pattern, idx + 1);
                if (idx > 500) break;  // Stop if we're past the reasoning section
            }
        }

        // Remove reasoning sentences (English and Japanese patterns)
        // These often appear before the actual answer
        String[] reasoningPatterns = {
            // English patterns
            "I should also verify", "I should", "I'll make sure", "I'll",
            "I need to", "Finally,", "But in this case", "No need for",
            "just the formatted", "just the clean", "the user requested",
            // Japanese patterns
            "試合データが含まれています。", "次に、", "まず、", "その後、",
            "各試合", "試合の", "データを確認", "フォーマット", "整形",
            "必要があるので", "必要があります", "チェックします", "確かめます",
            "全ての試合を表示する", "最終的に", "ユーザーが求めた"
        };

        for (String pattern : reasoningPatterns) {
            int idx = s.indexOf(pattern);
            while (idx >= 0 && idx < 10) {  // Only remove if at the start
                // Find the end of this sentence
                int endIdx = s.indexOf("。", idx);  // Japanese period
                if (endIdx < 0) {
                    endIdx = s.indexOf("\n", idx);
                }
                if (endIdx < 0) {
                    endIdx = s.indexOf(". ", idx);
                    if (endIdx >= 0) endIdx += 2;
                }

                if (endIdx > idx && endIdx < s.length()) {
                    s = s.substring(endIdx + 1).trim();
                } else {
                    break;
                }

                idx = s.indexOf(pattern);
            }
        }

        // Remove sentences starting with common reasoning words
        String[] sentenceStarters = {
            "Alright,", "Let's see", "Looking at", "First,", "Next,",
            "So,", "The user", "They want", "They're asking",
            "I think", "Now,", "Here's", "This is"
        };

        for (String starter : sentenceStarters) {
            while (s.startsWith(starter)) {
                int dotIdx = s.indexOf(". ");
                if (dotIdx > 0 && dotIdx < 300) {
                    s = s.substring(dotIdx + 2).trim();
                } else {
                    break;
                }
            }
        }

        // Remove multiple consecutive newlines (reasoning often separated by blank lines)
        s = s.replaceAll("\n{3,}", "\n\n");

        // Some models use "FINAL:" prefix for the actual answer
        String lower = s.toLowerCase(java.util.Locale.ROOT);
        int finalIdx = lower.indexOf("final:");
        if (finalIdx >= 0 && finalIdx < 50) {
            s = s.substring(finalIdx + "final:".length());
        }

        return s.trim();
    }

    /**
     * Count events in ESPN API JSON.
     *
     * @param jsonResult JSON result from ESPN API
     * @return Number of events
     */
    private int countEvents(String jsonResult) {
        try {
            JsonNode root = json.readTree(jsonResult);
            JsonNode events = root.get("events");
            if (events != null && events.isArray()) {
                return events.size();
            }
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static String stripTrailingSlash(String s) {
        if (s == null) return "";
        String v = s.trim();
        return v.endsWith("/") ? v.substring(0, v.length() - 1) : v;
    }

    private static String safe(String s) {
        if (s == null) return "";
        return s.length() > 500 ? s.substring(0, 500) + "..." : s;
    }

    private static String safe(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
