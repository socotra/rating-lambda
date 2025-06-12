package com.socotra.lambda;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.socotra.coremodel.RatingSet;
import com.socotra.deployment.customer.RatePlugin;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import java.util.Map;

public class RatingLambdaHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final EarnixRatingPlugin plugin = new EarnixRatingPlugin();

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try {
            context.getLogger().log("Raw input: " + input + "\n");

            String rawBody = (String) input.get("body");
            if (rawBody == null || rawBody.isEmpty()) {
                context.getLogger().log("Missing 'body' in payload\n");
                return Map.of("error", "Missing 'body' in payload");
            }

            JsonNode inputNode = MAPPER.readTree(rawBody);
            context.getLogger().log("Parsed body: " + inputNode + "\n");

            RatingSet result;

            if (inputNode.has("quote")) {
                context.getLogger().log("Detected 'quote' request\n");
                RatePlugin.PersonalAutoQuoteRequest request = null;
                 request =
                        MAPPER.treeToValue(inputNode, RatePlugin.PersonalAutoQuoteRequest.class);
                 context.getLogger().log("Request looks like " + request.toString());

                result = plugin.rate(request, context.getLogger());
            } else if (inputNode.has("segment")) {
                context.getLogger().log("Detected 'segment' request\n");
                RatePlugin.PersonalAutoRequest request =
                        MAPPER.treeToValue(inputNode, RatePlugin.PersonalAutoRequest.class);
                result = plugin.rate(request, context.getLogger());
            } else {
                context.getLogger().log("Invalid payload\n");
                ObjectNode error = MAPPER.createObjectNode();
                error.put("error", "Invalid payload: must contain 'quote' or 'segment'");
                return MAPPER.convertValue(error, Map.class);
            }

            return Map.of("ratingSet", result);

        } catch (Exception e) {
            context.getLogger().log("Error: " + e.toString() + "\n");
            ObjectNode error = MAPPER.createObjectNode();
            error.put("error", e.toString());
            return MAPPER.convertValue(error, Map.class);
        }
    }
}
