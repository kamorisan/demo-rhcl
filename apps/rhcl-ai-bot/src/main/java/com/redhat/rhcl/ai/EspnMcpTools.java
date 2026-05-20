package com.redhat.rhcl.ai;

import com.fasterxml.jackson.databind.JsonNode;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;

import jakarta.inject.Inject;

public class EspnMcpTools {

  @Inject
  EspnTool espn;

  @Inject
  JsonUtil json;

  @Tool(description = "Fetch NBA (National Basketball Association) scoreboard for American professional basketball. Returns game scores, live updates, and schedules for teams like Lakers, Warriors, Celtics, Heat.")
  public String nba_scoreboard(
      @ToolArg(description = "Optional date range in format YYYYMMDD-YYYYMMDD (e.g., 20260501-20260507). Leave empty for today's games.", defaultValue = "") String dates
  ) {
    try {
      JsonNode sb = espn.nbaScoreboard(dates == null ? "" : dates);
      return json.write(sb);
    } catch (Exception e) {
      throw new ToolCallException("ESPN tool failed: " + String.valueOf(e.getMessage()));
    }
  }

  @Tool(description = "Fetch English Premier League (EPL) scoreboard for top-tier English soccer/football. Returns match results, live games, and fixtures for teams like Manchester United, Liverpool, Arsenal, Chelsea, Manchester City.")
  public String epl_scoreboard(
      @ToolArg(description = "Optional date range in format YYYYMMDD-YYYYMMDD (e.g., 20260501-20260507). Leave empty for today's matches.", defaultValue = "") String dates
  ) {
    try {
      JsonNode sb = espn.eplScoreboard(dates == null ? "" : dates);
      return json.write(sb);
    } catch (Exception e) {
      throw new ToolCallException("ESPN tool failed: " + String.valueOf(e.getMessage()));
    }
  }

  @Tool(description = "Fetch LaLiga (Spanish La Liga) scoreboard for top Spanish soccer/football league. Returns match results and schedules for teams like Real Madrid, Barcelona, Atletico Madrid, Sevilla.")
  public String laliga_scoreboard(
      @ToolArg(description = "Optional date range in format YYYYMMDD-YYYYMMDD (e.g., 20260501-20260507). Leave empty for today's matches.", defaultValue = "") String dates
  ) {
    try {
      JsonNode sb = espn.laligaScoreboard(dates == null ? "" : dates);
      return json.write(sb);
    } catch (Exception e) {
      throw new ToolCallException("ESPN tool failed: " + String.valueOf(e.getMessage()));
    }
  }

  @Tool(description = "Fetch NFL (National Football League) scoreboard for American football. Returns game results and schedules for teams like Patriots, Cowboys, Chiefs, 49ers, Eagles.")
  public String nfl_scoreboard(
      @ToolArg(description = "Optional date range in format YYYYMMDD-YYYYMMDD (e.g., 20260501-20260507). Leave empty for today's games.", defaultValue = "") String dates
  ) {
    try {
      JsonNode sb = espn.nflScoreboard(dates == null ? "" : dates);
      return json.write(sb);
    } catch (Exception e) {
      throw new ToolCallException("ESPN tool failed: " + String.valueOf(e.getMessage()));
    }
  }

  @Tool(description = "Fetch NHL (National Hockey League) scoreboard for professional ice hockey. Returns game scores and schedules for teams like Maple Leafs, Canadiens, Bruins, Rangers, Blackhawks.")
  public String nhl_scoreboard(
      @ToolArg(description = "Optional date range in format YYYYMMDD-YYYYMMDD (e.g., 20260501-20260507). Leave empty for today's games.", defaultValue = "") String dates
  ) {
    try {
      JsonNode sb = espn.nhlScoreboard(dates == null ? "" : dates);
      return json.write(sb);
    } catch (Exception e) {
      throw new ToolCallException("ESPN tool failed: " + String.valueOf(e.getMessage()));
    }
  }
}

