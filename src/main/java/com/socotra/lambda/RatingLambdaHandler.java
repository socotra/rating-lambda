package com.socotra.lambda;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.socotra.coremodel.RatingSet;
import com.socotra.deployment.customer.ChargeType;
import com.socotra.deployment.customer.RatePlugin;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.socotra.platform.tools.ULID;

import java.io.IOException;
import java.util.Map;

public class RatingLambdaHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .registerModule(new Jdk8Module())
            .registerModule(ulidModule())
            .registerModule(chargeTypeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final EarnixRatingPlugin plugin = new EarnixRatingPlugin();

    /**
     * Jackson module to (de)serialize ULID as a plain string.
     */
    private static com.fasterxml.jackson.databind.Module ulidModule() {
        SimpleModule m = new SimpleModule("ULIDModule");
        m.addSerializer(ULID.class, new JsonSerializer<ULID>() {
            @Override
            public void serialize(ULID value, JsonGenerator gen, SerializerProvider serializers)
                    throws IOException {
                gen.writeString(value.toString());
            }
        });
        m.addDeserializer(ULID.class, new JsonDeserializer<ULID>() {
            @Override
            public ULID deserialize(JsonParser p, DeserializationContext ctxt)
                    throws IOException {
                String text = p.getValueAsString();
                // if the JSON was the string "null", or really null/empty,
                // return a Java null instead of calling ULID.from(...)
                if (text == null || text.isBlank() || "null".equals(text)) {
                    return null;
                }
                return ULID.from(text);
            }
        });
        return m;
    }

    private static SimpleModule chargeTypeModule() {
        SimpleModule m = new SimpleModule("ChargeTypeModule");
        // write the ChargeType out as its .name() (or .toString())
        m.addSerializer(ChargeType.class, new JsonSerializer<ChargeType>() {
            @Override
            public void serialize(ChargeType value, JsonGenerator gen, SerializerProvider serializers)
                    throws IOException {
                gen.writeString(value.name());
            }
        });
        // read a JSON string back into a ChargeType via valueOf(...)
        m.addDeserializer(ChargeType.class, new JsonDeserializer<ChargeType>() {
            @Override
            public ChargeType deserialize(JsonParser p, DeserializationContext ctxt)
                    throws IOException {
                String text = p.getValueAsString();
                // if you ever see "null" or empty, return null
                if (text == null || text.isBlank() || "null".equals(text)) {
                    return null;
                }
                return ChargeType.valueOf(text);
            }
        });
        return m;
    }


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

            JsonNode wrapper = MAPPER.readTree(rawBody);
            JsonNode payload;
            if (wrapper.has("body")) {
                // unwrap the embedded JSON string
                payload = MAPPER.readTree(wrapper.get("body").asText());
            } else {
                payload = wrapper;
            }

            RatingSet result;

            if (payload.has("quote")) {
                context.getLogger().log("Detected 'quote' request\n");
                RatePlugin.PersonalAutoQuoteRequest request = null;
                 request =
                        MAPPER.treeToValue(payload, RatePlugin.PersonalAutoQuoteRequest.class);
                 context.getLogger().log("Request looks like " + request.toString());

                result = plugin.rate(request, context.getLogger());
            } else if (payload.has("segment")) {
                context.getLogger().log("Detected 'segment' request\n");
                RatePlugin.PersonalAutoRequest request =
                        MAPPER.treeToValue(payload, RatePlugin.PersonalAutoRequest.class);
                result = plugin.rate(request, context.getLogger());
            } else {
                context.getLogger().log("Invalid payload\n");
                ObjectNode error = MAPPER.createObjectNode();
                error.put("error", "Invalid payload: must contain 'quote' or 'segment'");
                return MAPPER.convertValue(error, Map.class);
            }

            String json = MAPPER.writeValueAsString(Map.of("ratingSet", result));
            context.getLogger().log("JSON Response: " + result);
            return Map.of(
                    "statusCode", 200,
                    "headers", Map.of("Content-Type","application/json"),
                    "body", json,
                    "isBase64Encoded", false
            );
        } catch (Exception e) {
            context.getLogger().log("Error: " + e.toString() + "\n");
            ObjectNode error = MAPPER.createObjectNode();
            error.put("error", e.toString());
            return MAPPER.convertValue(error, Map.class);
        }
    }
}
