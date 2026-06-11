package com.redhat.rhcl.chatbot;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Main chatbot service that handles user messages.
 * Selects appropriate MCP tool, calls it, and formats the response using LLM.
 */
@ApplicationScoped
@Unremovable
public class ChatbotService {

    private static final Logger LOG = Logger.getLogger(ChatbotService.class);

    private final JsonUtil json;
    private final McpGatewayClient mcp;
    private final OpenAiClient llm;

    @ConfigProperty(name = "rhcl.chatbot.default-dates-range", defaultValue = "7")
    int defaultDatesRange;

    public ChatbotService(JsonUtil json, McpGatewayClient mcp, OpenAiClient llm) {
        this.json = json;
        this.mcp = mcp;
        this.llm = llm;
    }

    /**
     * Process user message and return chatbot response.
     *
     * @param userMessage User's message
     * @return Chatbot response with tool information
     */
    public ChatResponse chat(String userMessage) {
        try {
            LOG.infof("Processing user message: %s", userMessage);

            // 1. Detect language
            String language = detectLanguage(userMessage);
            LOG.debugf("Detected language: %s", language);

            // 2. Select appropriate MCP tool
            String toolName = selectTool(userMessage);
            LOG.infof("Selected MCP tool: %s", toolName);

            // 3.5. Extract specific game count request (e.g., "直近の10試合")
            int requestedGameCount = extractGameCount(userMessage);
            LOG.debugf("Requested game count: %d", requestedGameCount);

            // 3.6. Extract week/round number (e.g., "第38節", "week 38")
            int requestedWeek = extractWeekNumber(userMessage);
            LOG.debugf("Requested week: %d", requestedWeek);

            // 3.7. Extract team name
            String requestedTeam = extractTeamName(userMessage);
            LOG.debugf("Extracted team name: '%s'", requestedTeam);

            // If team is specified but sport is still default (NBA), check if sport was explicitly mentioned
            if (requestedTeam != null && !requestedTeam.isBlank() && toolName.equals("nba_scoreboard")) {
                // If no sport keyword was found in the message, keep NBA as default
                // The team filtering will work regardless of sport
                LOG.infof("Team '%s' specified, using %s", requestedTeam, toolName);
            }

            // Soccer leagues don't have week numbers in ESPN API
            if (requestedWeek > 0 && (toolName.equals("epl_scoreboard") || toolName.equals("laliga_scoreboard"))) {
                String errorMsg = (detectLanguage(userMessage).equals("ja"))
                    ? "申し訳ございません。サッカーリーグでは節番号指定は現在サポートされていません。期間指定（例: 「10月の」「2025年の」）をご利用ください。"
                    : "Sorry, week/round number filtering is not currently supported for soccer leagues. Please use date-based queries (e.g., \"in October\", \"in 2025\").";
                return new ChatResponse(errorMsg, toolName, "Week filtering not supported for soccer");
            }

            // 3. Prepare tool arguments
            ObjectNode args = json.obj();
            String dates = extractDatesOrDefault(userMessage, requestedWeek);
            if (!dates.isBlank()) {
                args.put("dates", dates);
            }
            LOG.debugf("Tool arguments: %s", args);

            // 4. Call MCP tool
            String toolResult = mcp.callToolText(toolName, args);
            LOG.infof("MCP tool result length: %d bytes", toolResult.length());

            // Log the full MCP response for debugging
            LOG.infof("MCP tool response:\n%s", toolResult);

            // Count events in the result
            int totalEvents = toolResult.split("\"id\":").length - 1;
            LOG.infof("MCP returned %d events in JSON", totalEvents);

            // 4.5. Filter events based on team, week number, or game count
            // Priority: team > week > game count
            String filteredResult;

            // Step 1: Filter by team if specified
            if (requestedTeam != null && !requestedTeam.isBlank()) {
                filteredResult = filterEventsByTeam(toolResult, requestedTeam);
                LOG.infof("Filtered to team '%s' events, new length: %d bytes", requestedTeam, filteredResult.length());
            } else if (requestedWeek > 0) {
                // Filter by week number
                filteredResult = filterEventsByWeek(toolResult, requestedWeek);
                LOG.infof("Filtered to week %d events, new length: %d bytes", requestedWeek, filteredResult.length());
            } else {
                filteredResult = toolResult;
            }

            // Step 2: Further filter by game count if specified (after team/week filtering)
            if (requestedGameCount > 0) {
                filteredResult = filterRecentEvents(filteredResult, requestedGameCount);
                LOG.infof("Filtered to recent %d events, new length: %d bytes", requestedGameCount, filteredResult.length());
            } else if (requestedTeam == null && requestedWeek == 0) {
                // No team, no week, no game count specified - use default max 20
                filteredResult = filterRecentEvents(filteredResult, 20);
                LOG.infof("Filtered to recent 20 events (default), new length: %d bytes", filteredResult.length());
            }
            // Note: If team or week is specified without game count, show ALL matching events (no 20-event cap)

            // 4.6. Count events in filtered result
            int eventCount = countEventsInJson(filteredResult);

            // No diagnostic info in production

            // 5. Format result with LLM
            String response;
            if (llm.enabled()) {
                response = llm.formatToolResult(userMessage, toolName, filteredResult, language);

                // 5.5. Add search period and event count prefix
                String prefix = buildResponsePrefix(dates, eventCount, language);
                response = prefix + response;
            } else {
                // Fallback: return raw tool result if LLM not available
                response = filteredResult;
            }

            return new ChatResponse(response, toolName, null);

        } catch (Exception e) {
            LOG.errorf(e, "Error processing user message: %s", userMessage);
            String errorMsg = (detectLanguage(userMessage).equals("ja"))
                    ? "申し訳ございません。エラーが発生しました: " + e.getMessage()
                    : "Sorry, an error occurred: " + e.getMessage();
            return new ChatResponse(errorMsg, null, e.getMessage());
        }
    }

    /**
     * Detect language from user message (simple heuristic).
     *
     * @param message User message
     * @return "ja" for Japanese, "en" for English
     */
    private String detectLanguage(String message) {
        if (message == null || message.isBlank()) {
            return "en";
        }

        // Simple heuristic: check for Japanese characters
        String s = message.toLowerCase();
        boolean hasJapanese = s.matches(".*[\\u3040-\\u309F\\u30A0-\\u30FF\\u4E00-\\u9FFF]+.*");

        return hasJapanese ? "ja" : "en";
    }

    /**
     * Select appropriate MCP tool based on user message keywords.
     *
     * @param message User message
     * @return MCP tool name
     */
    private String selectTool(String message) {
        if (message == null || message.isBlank()) {
            return "nba_scoreboard"; // Default
        }

        String s = message.toLowerCase(Locale.ROOT);

        // NBA keywords + team names (check first to avoid conflicts like "グリズリーズ" containing "リーズ")
        if (containsAny(s, "nba", "バスケ", "バスケット", "basketball",
                        // English team names
                        "pistons", "celtics", "knicks", "cavaliers", "cavs", "raptors",
                        "hawks", "76ers", "sixers", "magic", "hornets", "heat",
                        "bucks", "bulls", "nets", "pacers", "wizards", "thunder",
                        "spurs", "nuggets", "lakers", "rockets", "timberwolves",
                        "suns", "blazers", "trail blazers", "clippers", "warriors",
                        "pelicans", "mavericks", "mavs", "grizzlies", "kings", "jazz",
                        // Japanese team names
                        "ピストンズ", "セルティックス", "ニックス", "キャバリアーズ", "キャブス",
                        "ラプターズ", "ホークス", "セブンティシクサーズ", "シクサーズ",
                        "マジック", "ホーネッツ", "ヒート", "バックス", "ブルズ", "ネッツ",
                        "ペイサーズ", "ウィザーズ", "サンダー", "スパーズ", "ナゲッツ",
                        "レイカーズ", "ロケッツ", "ティンバーウルブズ", "ウルブズ", "サンズ",
                        "トレイルブレイザーズ", "ブレイザーズ", "クリッパーズ", "ウォリアーズ",
                        "ペリカンズ", "マーベリックス", "マブズ", "グリズリーズ", "キングス", "ジャズ")) {
            return "nba_scoreboard";
        }

        // Premier League keywords + team names (check before general soccer)
        if (containsAny(s, "premier league", "epl", "プレミア", "プレミアリーグ", "イングランド",
                        // English team names
                        "arsenal", "manchester", "man city", "man utd", "aston villa", "liverpool",
                        "bournemouth", "sunderland", "brighton", "brentford", "chelsea",
                        "fulham", "newcastle", "everton", "leeds", "crystal palace",
                        "nottingham", "tottenham", "west ham", "burnley", "wolverhampton", "wolves",
                        // Japanese team names
                        "アーセナル", "マンチェスター", "マンシティ", "マンu", "アストンヴィラ", "アストンビラ",
                        "リヴァプール", "リバプール", "ボーンマス", "サンダーランド", "ブライトン",
                        "ブレントフォード", "チェルシー", "フラム", "ニューカッスル", "エヴァートン",
                        "リーズ", "クリスタルパレス", "フォレスト", "ノッティンガム", "トッテナム",
                        "ウェストハム", "バーンリー", "ウルヴァーハンプトン", "ウルブズ")) {
            return "epl_scoreboard";
        }

        // La Liga keywords + team names (check before general soccer)
        if (containsAny(s, "la liga", "laliga", "ラリーガ", "リーガ", "スペイン", "spain", "spanish",
                        // English team names
                        "barcelona", "real madrid", "villarreal", "atletico", "betis", "real betis",
                        "celta", "getafe", "rayo", "valencia", "real sociedad", "espanyol",
                        "athletic bilbao", "sevilla", "alaves", "elche", "levante",
                        "osasuna", "mallorca", "girona", "real oviedo", "almeria",
                        // Japanese team names
                        "バルセロナ", "バルサ", "レアルマドリード", "レアル・マドリード", "レアル",
                        "ビジャレアル", "アトレティコ", "アトレティコマドリード", "アトレティコ・マドリード",
                        "ベティス", "セルタ", "ヘタフェ", "ラージョ", "バレンシア",
                        "レアルソシエダ", "レアル・ソシエダ", "エスパニョール", "ビルバオ",
                        "セビージャ", "セビリア", "アラベス", "エルチェ", "レバンテ",
                        "オサスナ", "マジョルカ", "ジローナ", "レアルオビエド", "レアル・オビエド")) {
            return "laliga_scoreboard";
        }

        // General soccer keywords (after specific leagues)
        if (containsAny(s, "soccer", "サッカー", "football") && !containsAny(s, "nfl", "american")) {
            // If no specific league mentioned, default to Premier League
            return "epl_scoreboard";
        }

        // NFL keywords + team names
        if (containsAny(s, "nfl", "アメフト", "アメリカンフットボール", "american football",
                        "patriots", "cowboys", "packers", "49ers", "chiefs", "eagles",
                        "ペイトリオッツ", "カウボーイズ", "パッカーズ",
                        "steelers", "seahawks", "broncos", "raiders", "rams", "chargers",
                        "giants", "jets", "bills", "dolphins", "ravens", "bengals",
                        "browns", "titans", "colts", "jaguars", "texans", "bears",
                        "vikings", "lions", "saints", "falcons", "panthers", "buccaneers",
                        "cardinals", "commanders", "washington")) {
            return "nfl_scoreboard";
        }

        // NHL keywords + team names
        if (containsAny(s, "nhl", "ホッケー", "アイスホッケー", "hockey", "ice hockey",
                        "maple leafs", "canadiens", "bruins", "rangers", "blackhawks",
                        "メープルリーフス", "カナディアンズ",
                        "penguins", "capitals", "flyers", "devils", "islanders",
                        "lightning", "panthers", "hurricanes", "blue jackets", "sabres",
                        "red wings", "avalanche", "stars", "predators", "blues",
                        "wild", "jets", "oilers", "flames", "canucks", "kings",
                        "ducks", "sharks", "golden knights", "kraken", "coyotes")) {
            return "nhl_scoreboard";
        }

        // Default: NBA
        return "nba_scoreboard";
    }

    /**
     * Extract date range from user message, or generate default range.
     * Intelligently adjusts range based on user's time keywords.
     *
     * @param message User message
     * @param requestedWeek Week number if specified (0 if not)
     * @return Date range string (YYYYMMDD-YYYYMMDD) or empty string
     */
    private String extractDatesOrDefault(String message, int requestedWeek) {
        // Normalize full-width digits to half-width
        String normalizedMessage = normalizeDigits(message);

        // Try to extract explicit date range from message
        // Format: dates=YYYYMMDD-YYYYMMDD
        Pattern pattern = Pattern.compile("dates?=(\\d{8}-\\d{8})");
        var matcher = pattern.matcher(normalizedMessage);
        if (matcher.find()) {
            return matcher.group(1);
        }

        LocalDate today = LocalDate.now();
        String s = normalizedMessage == null ? "" : normalizedMessage.toLowerCase();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMdd");

        // Pattern 0a: Week number specified (e.g., "第38節", "week 10")
        // Return full season range to ensure week data is available
        if (requestedWeek > 0) {
            // For week-based queries, search the entire current season
            // Assume season starts in August of previous year
            int currentYear = today.getYear();
            int currentMonth = today.getMonthValue();

            // If we're in Jan-Jul, the season started last August
            // If we're in Aug-Dec, the season started this August
            int seasonStartYear = (currentMonth < 8) ? currentYear - 1 : currentYear;

            LocalDate seasonStart = LocalDate.of(seasonStartYear, 8, 1);
            LocalDate seasonEnd = LocalDate.of(seasonStartYear + 1, 7, 31);

            return seasonStart.format(fmt) + "-" + seasonEnd.format(fmt);
        }

        // Pattern 0b: Specific game count (e.g., "直近の10試合", "last 5 games")
        // In this case, return a wide date range (past 60 days)
        // The actual filtering will be done later based on game count
        if (s.matches(".*(?:直近|最新|過去)の?\\d+試合.*") ||
            s.matches(".*(?:last|recent|latest)\\s+\\d+\\s+(?:games?|matches?).*")) {
            LocalDate start = today.minusDays(60);  // Wide range to ensure enough games
            return start.format(fmt) + "-" + today.format(fmt);
        }

        // Pattern 1a: "This year" / "今年"
        if (containsAny(s, "今年", "this year")) {
            int year = today.getYear();
            LocalDate startOfYear = LocalDate.of(year, 1, 1);
            LocalDate endOfYear = LocalDate.of(year, 12, 31);
            return startOfYear.format(fmt) + "-" + endOfYear.format(fmt);
        }

        // Pattern 1b: Year specification (e.g., "2026年の", "in 2026")
        Pattern yearPattern = Pattern.compile("(\\d{4})年の?");
        var yearMatcher = yearPattern.matcher(message);
        if (yearMatcher.find()) {
            int year = Integer.parseInt(yearMatcher.group(1));
            LocalDate startOfYear = LocalDate.of(year, 1, 1);
            LocalDate endOfYear = LocalDate.of(year, 12, 31);
            return startOfYear.format(fmt) + "-" + endOfYear.format(fmt);
        }
        Pattern yearPatternEn = Pattern.compile("in (\\d{4})");
        var yearMatcherEn = yearPatternEn.matcher(s);
        if (yearMatcherEn.find()) {
            int year = Integer.parseInt(yearMatcherEn.group(1));
            LocalDate startOfYear = LocalDate.of(year, 1, 1);
            LocalDate endOfYear = LocalDate.of(year, 12, 31);
            return startOfYear.format(fmt) + "-" + endOfYear.format(fmt);
        }

        // Pattern 2a: Date range (e.g., "6月5日-6月7日", "6月5日-7日")
        // Format: "N月M日-N月K日" or "N月M日-K日"
        Pattern dateRangePattern = Pattern.compile("(\\d+)月(\\d+)日[‐－-〜~]((?:\\d+)月)?(\\d+)日");
        var dateRangeMatcher = dateRangePattern.matcher(normalizedMessage);
        if (dateRangeMatcher.find()) {
            int startMonth = Integer.parseInt(dateRangeMatcher.group(1));
            int startDay = Integer.parseInt(dateRangeMatcher.group(2));
            String endMonthWithSuffix = dateRangeMatcher.group(3); // "6月" or null
            int endMonth;
            if (endMonthWithSuffix != null && !endMonthWithSuffix.isEmpty()) {
                // Extract number from "6月"
                endMonth = Integer.parseInt(endMonthWithSuffix.replaceAll("[^0-9]", ""));
            } else {
                endMonth = startMonth; // Same month
            }
            int endDay = Integer.parseInt(dateRangeMatcher.group(4));

            int year = (startMonth > today.getMonthValue()) ? today.getYear() - 1 : today.getYear();
            LocalDate startDate = LocalDate.of(year, startMonth, startDay);
            LocalDate endDate = LocalDate.of(year, endMonth, endDay);
            return startDate.format(fmt) + "-" + endDate.format(fmt);
        }

        // Pattern 2b: Specific date (e.g., "6月11日", "June 11") - Must check AFTER range pattern
        Pattern specificDatePattern = Pattern.compile("(\\d+)月(\\d+)日");
        var specificDateMatcher = specificDatePattern.matcher(normalizedMessage);
        if (specificDateMatcher.find()) {
            int month = Integer.parseInt(specificDateMatcher.group(1));
            int day = Integer.parseInt(specificDateMatcher.group(2));
            int year = (month > today.getMonthValue()) ? today.getYear() - 1 : today.getYear();
            LocalDate date = LocalDate.of(year, month, day);
            return date.format(fmt) + "-" + date.format(fmt);
        }

        // Pattern 3: Specific month (e.g., "3月の", "in March")
        Pattern specificMonthPattern = Pattern.compile("(\\d+)月の?(?!以降)");
        var specificMonthMatcher = specificMonthPattern.matcher(normalizedMessage);
        if (specificMonthMatcher.find()) {
            int month = Integer.parseInt(specificMonthMatcher.group(1));
            int year = (month > today.getMonthValue()) ? today.getYear() - 1 : today.getYear();
            LocalDate startOfMonth = LocalDate.of(year, month, 1);
            LocalDate endOfMonth = startOfMonth.withDayOfMonth(startOfMonth.lengthOfMonth());
            return startOfMonth.format(fmt) + "-" + endOfMonth.format(fmt);
        }
        Pattern specificMonthPatternEn = Pattern.compile("in (january|february|march|april|may|june|july|august|september|october|november|december)(?!.*(?:since|from))", Pattern.CASE_INSENSITIVE);
        var specificMonthMatcherEn = specificMonthPatternEn.matcher(s);
        if (specificMonthMatcherEn.find()) {
            String monthName = specificMonthMatcherEn.group(1).toLowerCase();
            int month = getMonthNumber(monthName);
            int year = (month > today.getMonthValue()) ? today.getYear() - 1 : today.getYear();
            LocalDate startOfMonth = LocalDate.of(year, month, 1);
            LocalDate endOfMonth = startOfMonth.withDayOfMonth(startOfMonth.lengthOfMonth());
            return startOfMonth.format(fmt) + "-" + endOfMonth.format(fmt);
        }

        // Pattern 4: "Since month" (e.g., "5月以降", "since May")
        Pattern sinceMonthPattern = Pattern.compile("(\\d+)月以降");
        var sinceMonthMatcher = sinceMonthPattern.matcher(normalizedMessage);
        if (sinceMonthMatcher.find()) {
            int month = Integer.parseInt(sinceMonthMatcher.group(1));
            LocalDate startOfMonth = LocalDate.of(today.getYear(), month, 1);
            return startOfMonth.format(fmt) + "-" + today.format(fmt);
        }
        Pattern sinceMonthPatternEn = Pattern.compile("(?:since|from)\\s+(january|february|march|april|may|june|july|august|september|october|november|december)", Pattern.CASE_INSENSITIVE);
        var sinceMonthMatcherEn = sinceMonthPatternEn.matcher(s);
        if (sinceMonthMatcherEn.find()) {
            String monthName = sinceMonthMatcherEn.group(1).toLowerCase();
            int month = getMonthNumber(monthName);
            LocalDate startOfMonth = LocalDate.of(today.getYear(), month, 1);
            return startOfMonth.format(fmt) + "-" + today.format(fmt);
        }

        int daysBefore;
        int daysAfter;

        // Intelligent date range based on user keywords
        if (containsAny(s, "today", "今日", "本日")) {
            daysBefore = 0;
            daysAfter = 0;
        } else if (containsAny(s, "latest", "最新", "newest", "新しい")) {
            // Latest games: look back 7 days (to catch recent finals)
            daysBefore = 7;
            daysAfter = 0;
        } else if (containsAny(s, "this week", "今週", "週末")) {
            daysBefore = 3;
            daysAfter = 4;
        } else if (containsAny(s, "last week", "先週", "前週")) {
            daysBefore = 14;
            daysAfter = 0;
        } else if (containsAny(s, "last 7 days", "last week", "過去7日", "過去1週間")) {
            daysBefore = 7;
            daysAfter = 0;
        } else if (containsAny(s, "last 14 days", "last 2 weeks", "過去14日", "過去2週間")) {
            daysBefore = 14;
            daysAfter = 0;
        } else if (containsAny(s, "last 30 days", "last month", "過去30日", "過去1ヶ月")) {
            daysBefore = 30;
            daysAfter = 0;
        } else if (containsAny(s, "recent", "最近")) {
            daysBefore = 7;
            daysAfter = 3;
        } else {
            // Default: ±N days from today (from config)
            daysBefore = defaultDatesRange;
            daysAfter = defaultDatesRange;
        }

        LocalDate start = today.minusDays(daysBefore);
        LocalDate end = today.plusDays(daysAfter);

        return start.format(fmt) + "-" + end.format(fmt);
    }

    /**
     * Extract requested game count from user message.
     * Handles patterns like "直近の10試合", "last 5 games", "recent 3 matches"
     *
     * @param message User message
     * @return Requested game count (0 if not specified)
     */
    private int extractGameCount(String message) {
        if (message == null || message.isBlank()) {
            return 0;
        }

        String normalized = normalizeDigits(message);

        // Japanese patterns: "直近の10試合", "最新の5試合", "過去3試合"
        Pattern jpPattern = Pattern.compile("(?:直近|最新|過去)の?(\\d+)試合");
        var jpMatcher = jpPattern.matcher(normalized);
        if (jpMatcher.find()) {
            return Integer.parseInt(jpMatcher.group(1));
        }

        // English patterns: "last 10 games", "recent 5 games", "latest 3 matches"
        Pattern enPattern = Pattern.compile("(?:last|recent|latest)\\s+(\\d+)\\s+(?:games?|matches?)", Pattern.CASE_INSENSITIVE);
        var enMatcher = enPattern.matcher(normalized);
        if (enMatcher.find()) {
            return Integer.parseInt(enMatcher.group(1));
        }

        return 0;
    }

    /**
     * Extract requested week/round number from user message.
     * Handles patterns like "第38節", "week 38", "round 5"
     *
     * @param message User message
     * @return Week number (0 if not specified)
     */
    private int extractWeekNumber(String message) {
        if (message == null || message.isBlank()) {
            return 0;
        }

        String normalized = normalizeDigits(message);

        // Japanese patterns: "第38節", "38節"
        Pattern jpPattern = Pattern.compile("第?(\\d+)節");
        var jpMatcher = jpPattern.matcher(normalized);
        if (jpMatcher.find()) {
            return Integer.parseInt(jpMatcher.group(1));
        }

        // English patterns: "week 38", "round 5", "matchday 10"
        Pattern enPattern = Pattern.compile("(?:week|round|matchday)\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
        var enMatcher = enPattern.matcher(normalized);
        if (enMatcher.find()) {
            return Integer.parseInt(enMatcher.group(1));
        }

        return 0;
    }

    /**
     * Extract team name from user message.
     *
     * @param message User message
     * @return Team name (null if not found)
     */
    private String extractTeamName(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }

        String normalized = normalizeDigits(message);

        // Pattern 1: Match "XXXの試合/結果/試合結果" with optional particles
        // Handles: "4月のバルセロナの試合" → "バルセロナ", "Lakers の試合結果を" → "Lakers"
        Pattern pattern1 = Pattern.compile("([^、。]+?)の(?:試合結果|試合|結果)(?:を|について)?");
        var matcher1 = pattern1.matcher(normalized);

        while (matcher1.find()) {
            String fullMatch = matcher1.group(1).trim();

            // Split by "の" and take the last part (the team name)
            String[] parts = fullMatch.split("の");
            String candidate = parts[parts.length - 1].trim();

            // Filter out date patterns, sport keywords, and time expressions
            boolean isDate = candidate.matches(".*\\d+月.*");
            boolean isKeyword = candidate.matches(".*(NBA|プレミア|ラリーガ|NFL|NHL|サッカー|バスケ|アメフト|ホッケー|直近|最新|過去|今日|本日|明日|昨日).*");

            if (!candidate.isEmpty() && !isDate && !isKeyword) {
                return candidate;
            }
        }

        // Pattern 2: English pattern "Team Name games/matches/results"
        Pattern pattern2 = Pattern.compile("(?:^|\\s)([A-Z][a-z]+(?:\\s+[A-Z][a-z]+)*)\\s+(?:games?|matches?|results?)", Pattern.MULTILINE);
        var matcher2 = pattern2.matcher(normalized);

        if (matcher2.find()) {
            return matcher2.group(1).trim();
        }

        return null;
    }

    /**
     * Detect sport tool by team name.
     *
     * @param teamName Team name
     * @return Tool name (null if not detected)
     */
    private String detectToolByTeamName(String teamName) {
        if (teamName == null) return null;

        String lower = teamName.toLowerCase();

        // NBA teams
        if (containsAny(lower,
                        // English names
                        "pistons", "celtics", "knicks", "cavaliers", "cavs", "raptors",
                        "hawks", "76ers", "sixers", "magic", "hornets", "heat",
                        "bucks", "bulls", "nets", "pacers", "wizards", "thunder",
                        "spurs", "nuggets", "lakers", "rockets", "timberwolves",
                        "suns", "blazers", "trail blazers", "clippers", "warriors",
                        "pelicans", "mavericks", "mavs", "grizzlies", "kings", "jazz",
                        // Japanese names
                        "ピストンズ", "セルティックス", "ニックス", "キャバリアーズ", "キャブス",
                        "ラプターズ", "ホークス", "セブンティシクサーズ", "シクサーズ",
                        "マジック", "ホーネッツ", "ヒート", "バックス", "ブルズ", "ネッツ",
                        "ペイサーズ", "ウィザーズ", "サンダー", "スパーズ", "ナゲッツ",
                        "レイカーズ", "ロケッツ", "ティンバーウルブズ", "ウルブズ", "サンズ",
                        "トレイルブレイザーズ", "ブレイザーズ", "クリッパーズ", "ウォリアーズ",
                        "ペリカンズ", "マーベリックス", "マブズ", "グリズリーズ", "キングス", "ジャズ")) {
            return "nba_scoreboard";
        }

        // Premier League teams
        if (containsAny(lower,
                        // English names
                        "arsenal", "manchester", "man city", "man utd", "aston villa", "liverpool",
                        "bournemouth", "sunderland", "brighton", "brentford", "chelsea",
                        "fulham", "newcastle", "everton", "leeds", "crystal palace",
                        "nottingham", "tottenham", "west ham", "burnley", "wolverhampton", "wolves",
                        // Japanese names
                        "アーセナル", "マンチェスター", "マンシティ", "マンu", "アストンヴィラ", "アストンビラ",
                        "リヴァプール", "リバプール", "ボーンマス", "サンダーランド", "ブライトン",
                        "ブレントフォード", "チェルシー", "フラム", "ニューカッスル", "エヴァートン",
                        "リーズ", "クリスタルパレス", "フォレスト", "ノッティンガム", "トッテナム",
                        "ウェストハム", "バーンリー", "ウルヴァーハンプトン", "ウルブズ")) {
            return "epl_scoreboard";
        }

        // La Liga teams
        if (containsAny(lower,
                        // English names
                        "barcelona", "real madrid", "villarreal", "atletico", "betis", "real betis",
                        "celta", "getafe", "rayo", "valencia", "real sociedad", "espanyol",
                        "athletic bilbao", "sevilla", "alaves", "elche", "levante",
                        "osasuna", "mallorca", "girona", "real oviedo",
                        // Japanese names
                        "バルセロナ", "バルサ", "レアルマドリード", "レアル・マドリード", "レアル",
                        "ビジャレアル", "アトレティコ", "アトレティコマドリード", "アトレティコ・マドリード",
                        "ベティス", "セルタ", "ヘタフェ", "ラージョ", "バレンシア",
                        "レアルソシエダ", "レアル・ソシエダ", "エスパニョール", "ビルバオ",
                        "セビージャ", "セビリア", "アラベス", "エルチェ", "レバンテ",
                        "オサスナ", "マジョルカ", "ジローナ", "レアルオビエド", "レアル・オビエド")) {
            return "laliga_scoreboard";
        }

        // NFL teams
        if (containsAny(lower, "patriots", "cowboys", "packers", "49ers", "chiefs", "eagles",
                        "ペイトリオッツ", "カウボーイズ", "パッカーズ")) {
            return "nfl_scoreboard";
        }

        // NHL teams
        if (containsAny(lower, "maple leafs", "canadiens", "bruins", "rangers", "blackhawks",
                        "メープルリーフス", "カナディアンズ")) {
            return "nhl_scoreboard";
        }

        return null;
    }

    /**
     * Filter events by team name.
     *
     * @param jsonResult ESPN API JSON result
     * @param teamName   Team name to filter
     * @return Filtered JSON with only events involving the team
     */
    private String filterEventsByTeam(String jsonResult, String teamName) {
        try {
            var root = json.readTree(jsonResult);
            var eventsNode = root.get("events");

            if (eventsNode == null || !eventsNode.isArray()) {
                LOG.warnf("No events array found in JSON");
                return jsonResult;
            }

            int total = eventsNode.size();
            LOG.infof("Filtering by team '%s': total=%d events", teamName, total);

            // Normalize team name (Japanese to English for major teams)
            String normalizedTeam = normalizeTeamName(teamName);
            LOG.debugf("Normalized team name: '%s' -> '%s'", teamName, normalizedTeam);

            var filteredEvents = json.arr();
            String teamLower = normalizedTeam.toLowerCase();

            int matchCount = 0;

            for (int i = 0; i < total; i++) {
                var event = eventsNode.get(i);
                var competition = event.path("competitions").get(0);

                if (competition != null) {
                    var competitors = competition.path("competitors");
                    boolean matchesTeam = false;

                    if (competitors.isArray()) {
                        for (int j = 0; j < competitors.size(); j++) {
                            var team = competitors.get(j).path("team");
                            String displayName = team.path("displayName").asText("").toLowerCase();
                            String name = team.path("name").asText("").toLowerCase();
                            String shortDisplayName = team.path("shortDisplayName").asText("").toLowerCase();
                            String abbreviation = team.path("abbreviation").asText("").toLowerCase();

                            // Match if team name appears in API fields
                            // Use ONE-WAY contains to avoid false positives (e.g., "cel" in "bar-CEL-ona")
                            if (displayName.contains(teamLower) ||
                                name.contains(teamLower) ||
                                shortDisplayName.contains(teamLower) ||
                                abbreviation.equalsIgnoreCase(normalizedTeam)) {
                                matchesTeam = true;
                                break;
                            }
                        }
                    }

                    if (matchesTeam) {
                        var compact = compactEvent(event);
                        filteredEvents.add(compact);
                        matchCount++;
                    }
                }
            }

            LOG.debugf("Found %d events for team '%s' (normalized: '%s')", filteredEvents.size(), teamName, normalizedTeam);

            var sortedEvents = sortEventsByDate(filteredEvents);
            var filtered = json.obj();
            filtered.set("events", sortedEvents);

            return json.write(filtered);

        } catch (Exception e) {
            LOG.errorf(e, "CRITICAL ERROR in filterEventsByTeam for team '%s': %s", teamName, e.getMessage());
            e.printStackTrace();
            // Return empty result instead of original JSON to make the error visible
            var emptyResult = json.obj();
            emptyResult.set("events", json.arr());
            try {
                return json.write(emptyResult);
            } catch (Exception ex) {
                return "{\"events\":[]}";
            }
        }
    }

    /**
     * Normalize full-width digits to half-width.
     *
     * @param text Text with possible full-width digits
     * @return Text with half-width digits
     */
    private String normalizeDigits(String text) {
        if (text == null) {
            return null;
        }
        // Full-width digits: ０１２３４５６７８９
        // Half-width digits: 0123456789
        return text.replace('０', '0')
                   .replace('１', '1')
                   .replace('２', '2')
                   .replace('３', '3')
                   .replace('４', '4')
                   .replace('５', '5')
                   .replace('６', '6')
                   .replace('７', '7')
                   .replace('８', '8')
                   .replace('９', '9');
    }

    /**
     * Normalize team name from Japanese to English for major teams.
     *
     * @param teamName Team name (potentially in Japanese)
     * @return Normalized team name (in English)
     */
    private String normalizeTeamName(String teamName) {
        if (teamName == null) {
            return null;
        }

        // La Liga teams (Japanese → English) - All 20 teams
        if (teamName.equals("バルセロナ") || teamName.equals("バルサ")) return "Barcelona";
        if (teamName.equals("レアルマドリード") || teamName.equals("レアル") || teamName.equals("レアル・マドリード")) return "Real Madrid";
        if (teamName.equals("ビジャレアル")) return "Villarreal";
        if (teamName.equals("アトレティコ") || teamName.equals("アトレティコマドリード") || teamName.equals("アトレティコ・マドリード")) return "Atletico Madrid";
        if (teamName.equals("ベティス")) return "Real Betis";
        if (teamName.equals("セルタ")) return "Celta Vigo";
        if (teamName.equals("ヘタフェ")) return "Getafe";
        if (teamName.equals("ラージョ")) return "Rayo Vallecano";
        if (teamName.equals("バレンシア")) return "Valencia";
        if (teamName.equals("レアルソシエダ") || teamName.equals("レアル・ソシエダ")) return "Real Sociedad";
        if (teamName.equals("エスパニョール")) return "Espanyol";
        if (teamName.equals("ビルバオ")) return "Athletic Bilbao";
        if (teamName.equals("セビージャ") || teamName.equals("セビリア")) return "Sevilla";
        if (teamName.equals("アラベス")) return "Alaves";
        if (teamName.equals("エルチェ")) return "Elche";
        if (teamName.equals("レバンテ")) return "Levante";
        if (teamName.equals("オサスナ")) return "Osasuna";
        if (teamName.equals("マジョルカ")) return "Mallorca";
        if (teamName.equals("ジローナ")) return "Girona";
        if (teamName.equals("レアルオビエド") || teamName.equals("レアル・オビエド")) return "Real Oviedo";

        // Premier League teams (Japanese → English) - All 20 teams
        if (teamName.equals("アーセナル")) return "Arsenal";
        if (teamName.equals("マンチェスターシティ") || teamName.equals("マンシティ")) return "Manchester City";
        if (teamName.equals("マンチェスターユナイテッド") || teamName.equals("マンU")) return "Manchester United";
        if (teamName.equals("アストンヴィラ") || teamName.equals("アストンビラ")) return "Aston Villa";
        if (teamName.equals("リヴァプール") || teamName.equals("リバプール")) return "Liverpool";
        if (teamName.equals("ボーンマス")) return "Bournemouth";
        if (teamName.equals("サンダーランド")) return "Sunderland";
        if (teamName.equals("ブライトン")) return "Brighton";
        if (teamName.equals("ブレントフォード")) return "Brentford";
        if (teamName.equals("チェルシー")) return "Chelsea";
        if (teamName.equals("フラム")) return "Fulham";
        if (teamName.equals("ニューカッスル")) return "Newcastle";
        if (teamName.equals("エヴァートン")) return "Everton";
        if (teamName.equals("リーズ")) return "Leeds";
        if (teamName.equals("クリスタルパレス")) return "Crystal Palace";
        if (teamName.equals("フォレスト") || teamName.equals("ノッティンガム")) return "Nottingham Forest";
        if (teamName.equals("トッテナム")) return "Tottenham";
        if (teamName.equals("ウェストハム")) return "West Ham";
        if (teamName.equals("バーンリー")) return "Burnley";
        if (teamName.equals("ウルヴァーハンプトン") || teamName.equals("ウルブズ")) return "Wolverhampton";

        // NBA teams (Japanese → English) - All 30 teams
        if (teamName.equals("ピストンズ")) return "Pistons";
        if (teamName.equals("セルティックス")) return "Celtics";
        if (teamName.equals("ニックス")) return "Knicks";
        if (teamName.equals("キャバリアーズ") || teamName.equals("キャブス")) return "Cavaliers";
        if (teamName.equals("ラプターズ")) return "Raptors";
        if (teamName.equals("ホークス")) return "Hawks";
        if (teamName.equals("セブンティシクサーズ") || teamName.equals("76ers") || teamName.equals("シクサーズ")) return "76ers";
        if (teamName.equals("マジック")) return "Magic";
        if (teamName.equals("ホーネッツ")) return "Hornets";
        if (teamName.equals("ヒート")) return "Heat";
        if (teamName.equals("バックス")) return "Bucks";
        if (teamName.equals("ブルズ")) return "Bulls";
        if (teamName.equals("ネッツ")) return "Nets";
        if (teamName.equals("ペイサーズ")) return "Pacers";
        if (teamName.equals("ウィザーズ")) return "Wizards";
        if (teamName.equals("サンダー")) return "Thunder";
        if (teamName.equals("スパーズ")) return "Spurs";
        if (teamName.equals("ナゲッツ")) return "Nuggets";
        if (teamName.equals("レイカーズ")) return "Lakers";
        if (teamName.equals("ロケッツ")) return "Rockets";
        if (teamName.equals("ティンバーウルブズ") || teamName.equals("ウルブズ")) return "Timberwolves";
        if (teamName.equals("サンズ")) return "Suns";
        if (teamName.equals("トレイルブレイザーズ") || teamName.equals("ブレイザーズ")) return "Trail Blazers";
        if (teamName.equals("クリッパーズ")) return "Clippers";
        if (teamName.equals("ウォリアーズ")) return "Warriors";
        if (teamName.equals("ペリカンズ")) return "Pelicans";
        if (teamName.equals("マーベリックス") || teamName.equals("マブズ")) return "Mavericks";
        if (teamName.equals("グリズリーズ")) return "Grizzlies";
        if (teamName.equals("キングス")) return "Kings";
        if (teamName.equals("ジャズ")) return "Jazz";

        // Return as-is if no mapping found
        return teamName;
    }

    /**
     * Check if string contains any of the given keywords.
     *
     * @param s        String to search
     * @param keywords Keywords to search for
     * @return true if any keyword is found
     */
    private boolean containsAny(String s, String... keywords) {
        if (s == null || keywords == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (s.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Convert English month name to month number.
     *
     * @param monthName Month name (e.g., "january", "may")
     * @return Month number (1-12)
     */
    private int getMonthNumber(String monthName) {
        return switch (monthName.toLowerCase()) {
            case "january" -> 1;
            case "february" -> 2;
            case "march" -> 3;
            case "april" -> 4;
            case "may" -> 5;
            case "june" -> 6;
            case "july" -> 7;
            case "august" -> 8;
            case "september" -> 9;
            case "october" -> 10;
            case "november" -> 11;
            case "december" -> 12;
            default -> 1;
        };
    }

    /**
     * Filter events from ESPN API JSON to keep only events from a specific week.
     *
     * @param jsonResult ESPN API JSON result
     * @param weekNumber Week/round number to filter
     * @return Filtered JSON with only events from the specified week
     */
    private String filterEventsByWeek(String jsonResult, int weekNumber) {
        try {
            var root = json.readTree(jsonResult);
            var eventsNode = root.get("events");

            if (eventsNode == null || !eventsNode.isArray()) {
                LOG.warnf("No events array found in JSON");
                return jsonResult;
            }

            int total = eventsNode.size();
            LOG.infof("Filtering by week %d: total=%d events", weekNumber, total);

            var filteredEvents = json.arr();

            for (int i = 0; i < total; i++) {
                var event = eventsNode.get(i);
                var weekNode = event.path("week").path("number");

                if (weekNode.isInt() && weekNode.asInt() == weekNumber) {
                    // Extract and compact this event
                    var compact = compactEvent(event);
                    filteredEvents.add(compact);
                }
            }

            LOG.infof("Found %d events in week %d", filteredEvents.size(), weekNumber);

            // Sort by date
            var sortedEvents = sortEventsByDate(filteredEvents);

            var filtered = json.obj();
            filtered.set("events", sortedEvents);

            return json.write(filtered);

        } catch (Exception e) {
            LOG.warnf(e, "Failed to filter by week, returning original JSON");
            return jsonResult;
        }
    }

    /**
     * Filter events from ESPN API JSON to keep only the most recent N events.
     * Also compacts the JSON to include only essential fields for LLM processing.
     * This prevents LLM timeout when there are too many events.
     *
     * @param jsonResult ESPN API JSON result
     * @param maxEvents  Maximum number of events to keep
     * @return Filtered and compacted JSON with only the most recent events
     */
    private String filterRecentEvents(String jsonResult, int maxEvents) {
        try {
            var root = json.readTree(jsonResult);
            var eventsNode = root.get("events");

            if (eventsNode == null || !eventsNode.isArray()) {
                LOG.warnf("No events array found in JSON");
                return jsonResult;
            }

            int total = eventsNode.size();
            LOG.infof("Filtering events: total=%d maxEvents=%d", total, maxEvents);

            // Determine which events to keep
            var filteredEvents = json.arr();
            int startIndex = (total > maxEvents) ? (total - maxEvents) : 0;

            for (int i = startIndex; i < total; i++) {
                var event = eventsNode.get(i);
                var compact = compactEvent(event);
                filteredEvents.add(compact);
            }

            LOG.infof("Kept last %d events out of %d, compacted JSON", filteredEvents.size(), total);

            // Sort events by date
            var sortedEvents = sortEventsByDate(filteredEvents);

            // Reconstruct JSON with sorted events
            var filtered = json.obj();
            filtered.set("events", sortedEvents);

            String result = json.write(filtered);

            // Debug: Log first event's status
            if (sortedEvents.size() > 0) {
                LOG.infof("Sample event status: %s", sortedEvents.get(0).toString());
            }

            return result;

        } catch (Exception e) {
            LOG.warnf(e, "Failed to filter events, returning original JSON");
            return jsonResult;
        }
    }

    /**
     * Count events in JSON result.
     *
     * @param jsonResult JSON string
     * @return Number of events
     */
    private int countEventsInJson(String jsonResult) {
        try {
            var root = json.readTree(jsonResult);
            var events = root.get("events");
            if (events != null && events.isArray()) {
                return events.size();
            }
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Build response prefix with search period and event count.
     *
     * @param dates      Date range (YYYYMMDD-YYYYMMDD)
     * @param eventCount Number of events found
     * @param language   Language (ja or en)
     * @return Formatted prefix string
     */
    private String buildResponsePrefix(String dates, int eventCount, String language) {
        if (dates == null || dates.isBlank()) {
            return "";
        }

        // Parse date range (YYYYMMDD-YYYYMMDD)
        String[] parts = dates.split("-");
        if (parts.length != 2) {
            return "";
        }

        try {
            String startDate = parts[0];  // YYYYMMDD
            String endDate = parts[1];    // YYYYMMDD

            // Format as YYYY-MM-DD
            String formattedStart = startDate.substring(0, 4) + "-" +
                                   startDate.substring(4, 6) + "-" +
                                   startDate.substring(6, 8);
            String formattedEnd = endDate.substring(0, 4) + "-" +
                                 endDate.substring(4, 6) + "-" +
                                 endDate.substring(6, 8);

            String eventMessage;
            if (language.equals("ja")) {
                eventMessage = (eventCount > 0)
                    ? String.format("%d 試合が見つかりました。", eventCount)
                    : "この期間で試合はありませんでした。";
                return String.format("検索期間: %s ~ %s\n%s\n\n",
                                   formattedStart, formattedEnd, eventMessage);
            } else {
                eventMessage = (eventCount > 0)
                    ? String.format("%d event%s found.", eventCount, eventCount > 1 ? "s" : "")
                    : "No events found in this period.";
                return String.format("Search period: %s ~ %s\n%s\n\n",
                                   formattedStart, formattedEnd, eventMessage);
            }
        } catch (Exception e) {
            LOG.warnf(e, "Failed to format date range: %s", dates);
            return "";
        }
    }

    /**
     * Compact an event to essential fields only.
     *
     * @param event Event node from ESPN API
     * @return Compacted event node
     */
    private com.fasterxml.jackson.databind.node.ObjectNode compactEvent(com.fasterxml.jackson.databind.JsonNode event) {
        var compact = json.obj();
        compact.put("date", event.path("date").asText(""));
        compact.put("id", event.path("id").asText(""));

        var competition = event.path("competitions").get(0);
        if (competition != null) {
            var status = json.obj();
            status.put("shortDetail", competition.path("status").path("type").path("shortDetail").asText(""));
            status.put("typeId", competition.path("status").path("type").path("id").asText(""));
            compact.set("status", status);

            var competitors = json.arr();
            var competitorsNode = competition.path("competitors");
            if (competitorsNode.isArray()) {
                for (int j = 0; j < competitorsNode.size(); j++) {
                    var comp = competitorsNode.get(j);
                    var compactComp = json.obj();
                    compactComp.put("homeAway", comp.path("homeAway").asText(""));
                    compactComp.put("team", comp.path("team").path("displayName").asText(""));
                    compactComp.put("score", comp.path("score").asText("0"));
                    competitors.add(compactComp);
                }
            }
            compact.set("competitors", competitors);
        }

        return compact;
    }

    /**
     * Sort events by date (ascending: oldest first).
     *
     * @param events Events array
     * @return Sorted events array
     */
    private com.fasterxml.jackson.databind.node.ArrayNode sortEventsByDate(com.fasterxml.jackson.databind.node.ArrayNode events) {
        var sortedEvents = json.arr();
        var eventsList = new java.util.ArrayList<com.fasterxml.jackson.databind.JsonNode>();
        events.forEach(eventsList::add);

        eventsList.sort((a, b) -> {
            String dateA = a.path("date").asText("");
            String dateB = b.path("date").asText("");
            return dateA.compareTo(dateB);  // Ascending order
        });

        eventsList.forEach(sortedEvents::add);
        return sortedEvents;
    }

    /**
     * Chatbot response model.
     */
    public static class ChatResponse {
        public final String response;
        public final String toolUsed;
        public final String error;

        public ChatResponse(String response, String toolUsed, String error) {
            this.response = response;
            this.toolUsed = toolUsed;
            this.error = error;
        }
    }
}
