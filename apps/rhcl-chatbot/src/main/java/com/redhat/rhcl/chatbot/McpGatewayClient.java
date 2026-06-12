package com.redhat.rhcl.chatbot;

import com.fasterxml.jackson.databind.JsonNode;
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
 * Client for communicating with MCP Gateway.
 * Implements MCP protocol: initialize -> tools/call
 *
 * IMPORTANT: Does NOT send notifications/initialized to avoid SSE mode issue in RHCL 1.3.3
 */
@ApplicationScoped
@Unremovable
public class McpGatewayClient {

    private static final Logger LOG = Logger.getLogger(McpGatewayClient.class);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final JsonUtil json;

    @ConfigProperty(name = "rhcl.mcp.base-url")
    String baseUrl;

    @ConfigProperty(name = "rhcl.mcp.timeout-seconds", defaultValue = "10")
    int timeoutSeconds;

    public McpGatewayClient(JsonUtil json) {
        this.json = json;
    }

    /**
     * Call an MCP tool and return the result as JSON.
     *
     * @param toolName  MCP tool name (e.g., "nba_scoreboard")
     * @param arguments Tool arguments (e.g., {"dates": "20260601-20260607"})
     * @return Tool result JSON
     * @throws Exception if communication fails
     */
    public JsonNode callToolJson(String toolName, ObjectNode arguments) throws Exception {
        LOG.debugf("Calling MCP tool: %s with args: %s", toolName, arguments);

        // 1. Initialize session
        String sessionId = initialize();

        try {
            // 2. Call tool
            return toolsCall(sessionId, toolName, arguments);
        } finally {
            // No explicit session close required for demo
            // The gateway expires sessions automatically
        }
    }

    /**
     * Call an MCP tool and return the text result.
     *
     * @param toolName  MCP tool name
     * @param arguments Tool arguments
     * @return Tool result text
     * @throws Exception if communication fails
     */
    public String callToolText(String toolName, ObjectNode arguments) throws Exception {
        JsonNode res = callToolJson(toolName, arguments);

        // Extract text from result.content[0].text
        String text = res.at("/result/content/0/text").asText("");
        if (!text.isBlank()) {
            return text;
        }

        // Fallback: return full JSON if structure is different
        LOG.warnf("Unexpected MCP response structure, returning full JSON: %s", res);
        return res.toString();
    }

    /**
     * Initialize MCP session.
     *
     * @return Session ID (from mcp-session-id header)
     * @throws Exception if initialization fails
     */
    private String initialize() throws Exception {
        ObjectNode params = json.obj();
        params.put("protocolVersion", "2025-11-25");
        params.set("capabilities", json.obj());

        ObjectNode clientInfo = json.obj();
        clientInfo.put("name", "rhcl-chatbot");
        clientInfo.put("version", "1.0.0");
        params.set("clientInfo", clientInfo);

        ObjectNode req = json.obj();
        req.put("jsonrpc", "2.0");
        req.put("id", 1);
        req.put("method", "initialize");
        req.set("params", params);

        LOG.debugf("MCP initialize request: %s", req);
        HttpResponse<String> res = post(null, req);

        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new RuntimeException("MCP initialize failed: status=" + res.statusCode() + " body=" + safe(res.body()));
        }

        String sid = header(res, "mcp-session-id");
        if (sid.isBlank()) {
            throw new RuntimeException("MCP initialize failed: missing mcp-session-id header");
        }

        LOG.infof("MCP session initialized: %s", sid.substring(0, Math.min(20, sid.length())) + "...");

        // IMPORTANT: DO NOT send notifications/initialized
        // This causes MCP Gateway in RHCL 1.3.3 to switch to SSE mode
        // See: MCP_GATEWAY_ISSUE_RHCL1.3.3.md

        return sid;
    }

    /**
     * Call an MCP tool.
     *
     * @param sessionId MCP session ID
     * @param toolName  Tool name
     * @param arguments Tool arguments
     * @return Tool result JSON
     * @throws Exception if call fails
     */
    private JsonNode toolsCall(String sessionId, String toolName, ObjectNode arguments) throws Exception {
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("MCP tools/call requires a tool name");
        }

        ObjectNode params = json.obj();
        params.put("name", toolName);
        params.set("arguments", arguments == null ? json.obj() : arguments);

        ObjectNode req = json.obj();
        req.put("jsonrpc", "2.0");
        req.put("id", 2);
        req.put("method", "tools/call");
        req.set("params", params);

        LOG.debugf("MCP tools/call request: tool=%s args=%s", toolName, arguments);
        HttpResponse<String> res = post(sessionId, req);

        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new RuntimeException("MCP tools/call failed: status=" + res.statusCode() + " body=" + safe(res.body()));
        }

        String contentType = res.headers().firstValue("content-type").orElse("unknown");
        LOG.debug(String.format("MCP tools/call response: status=%d contentType=%s bodyLength=%d",
                   res.statusCode(), contentType, res.body().length()));

        // Verify we got JSON, not SSE
        if (!contentType.contains("application/json")) {
            LOG.warnf("Unexpected Content-Type: %s (expected application/json)", contentType);
        }

        return json.readTree(res.body());
    }

    /**
     * Send HTTP POST request to MCP Gateway.
     *
     * @param sessionId MCP session ID (null for initialize)
     * @param body      Request body
     * @return HTTP response
     * @throws Exception if request fails
     */
    private HttpResponse<String> post(String sessionId, ObjectNode body) throws Exception {
        URI uri = URI.create(stripTrailingSlash(baseUrl));

        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(Math.max(3, timeoutSeconds)))
                .header("Content-Type", "application/json")
                // IMPORTANT: Include both accept types for Backend MCP Server compatibility
                .header("Accept", "application/json, text/event-stream");

        if (sessionId != null && !sessionId.isBlank()) {
            builder.header("mcp-session-id", sessionId);
        }

        HttpRequest req = builder.POST(HttpRequest.BodyPublishers.ofString(json.write(body))).build();

        return http.send(req, HttpResponse.BodyHandlers.ofString());
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

    private static String header(HttpResponse<?> res, String name) {
        if (res == null || name == null) return "";
        return res.headers().firstValue(name).orElse("").trim();
    }
}
