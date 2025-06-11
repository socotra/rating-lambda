package com.socotra.lambda;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.socotra.coremodel.ElementCategory;
import com.socotra.coremodel.RatingSet;
import com.socotra.coremodel.RatingItem;
import com.socotra.deployment.customer.*;
import com.socotra.deployment.customer.RatePlugin;
import com.socotra.platform.tools.ULID;
import com.socotra.coremodel.Element;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RatingLambdaHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final MyRatingPlugin plugin = new MyRatingPlugin();

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
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
                RatePlugin.PersonalAutoQuoteRequest request =
                        MAPPER.treeToValue(inputNode, RatePlugin.PersonalAutoQuoteRequest.class);

                // Inject mock element if needed
                for (PersonalVehicle pv : request.quote().personalVehicles()) {
                    if (pv instanceof PersonalVehicleQuote pvq) {
                        Fire fire = pvq.fire();
                        if (fire instanceof FireQuote fq && fq.element() == null) {
                            Element mockElement = Element.builder()
                                    .type("FireQuote")
                                    .locator(ULID.generate())
                                    .tenantLocator(UUID.randomUUID())
                                    .category(ElementCategory.coverage)
                                    .build();

                            FireQuote fixed = fq.toBuilder().element(mockElement).build();
                            PersonalVehicleQuote fixedVehicle = pvq.toBuilder().fire(fixed).build();
                            // replace the fire quote in the list (you may need to rebuild the full quote)
                            // or if this is a copy, inject later before calling rate()
                        }
                    }
                }

                result = plugin.rate(request);
            } else if (inputNode.has("segment")) {
                context.getLogger().log("Detected 'segment' request\n");
                RatePlugin.PersonalAutoRequest request =
                        MAPPER.treeToValue(inputNode, RatePlugin.PersonalAutoRequest.class);
                result = plugin.rate(request);
            } else {
                context.getLogger().log("Invalid payload\n");
                ObjectNode error = MAPPER.createObjectNode();
                error.put("error", "Invalid payload: must contain 'quote' or 'segment'");
                return MAPPER.convertValue(error, Map.class);
            }

            return MAPPER.convertValue(result, Map.class);

        } catch (Exception e) {
            context.getLogger().log("Error: " + e.toString() + "\n");
            ObjectNode error = MAPPER.createObjectNode();
            error.put("error", e.toString());
            return MAPPER.convertValue(error, Map.class);
        }
    }
}
