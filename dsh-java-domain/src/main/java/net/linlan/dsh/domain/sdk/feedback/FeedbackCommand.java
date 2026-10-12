package net.linlan.dsh.domain.sdk.feedback;

import net.linlan.dsh.domain.agent.service.compaction.SlashCommand;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class FeedbackCommand implements SlashCommand {

    private final MessageFeedbackService service;

    public FeedbackCommand(MessageFeedbackService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public String name() {
        return "/feedback";
    }

    @Override
    public String description() {
        return "Rate or note feedback for a message: /feedback <messageId> <rating|note:text>";
    }

    @Override
    public CompletableFuture<String> execute(String args, SlashCommandContext context) {
        Objects.requireNonNull(args, "args");
        Objects.requireNonNull(context, "context");
        if (args.isBlank()) {
            return CompletableFuture.completedFuture("usage: /feedback <messageId> <rating|note:text>");
        }
        String[] parts = args.trim().split("\\s+", 2);
        if (parts.length < 2) {
            return CompletableFuture.completedFuture("usage: /feedback <messageId> <rating|note:text>");
        }
        String messageId = parts[0];
        String action = parts[1];
        try {
            if (action.startsWith("note:")) {
                String text = action.substring("note:".length());
                return CompletableFuture.completedFuture(service.note(messageId, text));
            }
            int rating = Integer.parseInt(action);
            return CompletableFuture.completedFuture(service.rate(messageId, rating));
        } catch (NumberFormatException e) {
            return CompletableFuture.completedFuture("invalid rating: " + action);
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture("error: " + e.getMessage());
        }
    }
}
