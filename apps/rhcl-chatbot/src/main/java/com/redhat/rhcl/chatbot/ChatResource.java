package com.redhat.rhcl.chatbot;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.time.Instant;

/**
 * REST endpoint for chatbot interactions.
 * Provides a single POST /chat endpoint for user messages.
 */
@Path("/chat")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ChatResource {

    private static final Logger LOG = Logger.getLogger(ChatResource.class);

    private final ChatbotService chatbot;

    public ChatResource(ChatbotService chatbot) {
        this.chatbot = chatbot;
    }

    /**
     * Handle chat message from user.
     *
     * POST /chat
     * Request: {"message": "NBAの今日の試合は？"}
     * Response: {"response": "...", "tool_used": "nba_scoreboard", "timestamp": "..."}
     */
    @POST
    public Response chat(ChatRequest request) {
        try {
            if (request == null || request.message == null || request.message.isBlank()) {
                return Response.status(400)
                        .entity(new ErrorResponse("Message is required"))
                        .build();
            }

            LOG.infof("Received chat request: %s", request.message);

            // Process message with chatbot service
            ChatbotService.ChatResponse chatResponse = chatbot.chat(request.message);

            // Build response
            ChatApiResponse apiResponse = new ChatApiResponse(
                    chatResponse.response,
                    chatResponse.toolUsed,
                    chatResponse.error,
                    Instant.now().toString()
            );

            if (chatResponse.error != null) {
                // Return error response with 500 status
                return Response.status(500).entity(apiResponse).build();
            }

            return Response.ok(apiResponse).build();

        } catch (Exception e) {
            LOG.errorf(e, "Unexpected error in chat endpoint");
            return Response.status(500)
                    .entity(new ErrorResponse("Internal server error: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Chat request model.
     */
    public static class ChatRequest {
        public String message;

        public ChatRequest() {
        }

        public ChatRequest(String message) {
            this.message = message;
        }
    }

    /**
     * Chat response model.
     */
    public static class ChatApiResponse {
        public String response;
        public String tool_used;
        public String error;
        public String timestamp;

        public ChatApiResponse() {
        }

        public ChatApiResponse(String response, String toolUsed, String error, String timestamp) {
            this.response = response;
            this.tool_used = toolUsed;
            this.error = error;
            this.timestamp = timestamp;
        }
    }

    /**
     * Error response model.
     */
    public static class ErrorResponse {
        public String error;

        public ErrorResponse() {
        }

        public ErrorResponse(String error) {
            this.error = error;
        }
    }
}
