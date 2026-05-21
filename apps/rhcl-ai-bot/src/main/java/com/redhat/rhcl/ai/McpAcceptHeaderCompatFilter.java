package com.redhat.rhcl.ai;

import io.quarkus.vertx.http.runtime.filters.Filters;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.event.Observes;

public class McpAcceptHeaderCompatFilter {

  void register(@Observes Filters filters) {
    // Run early, before application routes.
    filters.register(this::normalizeAcceptHeader, 100);
  }

  void normalizeAcceptHeader(RoutingContext rc) {
    String path = rc.request().path();
    if (path == null || !path.startsWith("/mcp")) {
      rc.next();
      return;
    }

    String accept = rc.request().getHeader("accept");
    if (accept == null || accept.isBlank()) {
      rc.request().headers().set("accept", "application/json, text/event-stream");
      rc.next();
      return;
    }

    String lower = accept.toLowerCase();

    // For streamable endpoint (/mcp), ensure application/json is present
    // MCP Gateway may send "*/*, text/event-stream" which needs normalization
    if (path.equals("/mcp") && !lower.contains("application/json")) {
      // Replace with application/json for streamable transport
      rc.request().headers().set("accept", "application/json");
      rc.next();
      return;
    }

    if (!lower.contains("text/event-stream")) {
      rc.request().headers().set("accept", accept + ", text/event-stream");
    }

    rc.next();
  }
}

