package io.tebex.sdk.headlessapi.models;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Type;
import java.util.Locale;

/**
 * Accept both documented object links and observed array-based link payloads.
 */
public class BasketLinksTypeAdapter implements JsonDeserializer<BasketLinks> {

    @Override
    public BasketLinks deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        BasketLinks links = new BasketLinks();
        if (json == null || json.isJsonNull()) {
            return links;
        }

        if (json.isJsonObject()) {
            applyObject(json.getAsJsonObject(), links);
            return links;
        }

        if (json.isJsonArray()) {
            applyArray(json.getAsJsonArray(), links);
            return links;
        }

        return links;
    }

    private static void applyObject(@Nonnull JsonObject object, @Nonnull BasketLinks links) {
        String checkout = extractFromNamedField(object, "checkout");
        if (checkout != null && !checkout.isBlank()) {
            links.setCheckout(checkout);
        }

        String payment = extractFromNamedField(object, "payment");
        if (payment != null && !payment.isBlank()) {
            links.setPayment(payment);
        }

        // Some payloads nest links under a secondary field.
        JsonElement nested = object.get("links");
        if (nested != null && !nested.isJsonNull()) {
            if (nested.isJsonObject()) {
                applyObject(nested.getAsJsonObject(), links);
            } else if (nested.isJsonArray()) {
                applyArray(nested.getAsJsonArray(), links);
            }
        }
    }

    private static void applyArray(@Nonnull JsonArray array, @Nonnull BasketLinks links) {
        for (JsonElement entry : array) {
            if (entry == null || entry.isJsonNull()) {
                continue;
            }

            if (entry.isJsonObject()) {
                JsonObject obj = entry.getAsJsonObject();

                String rel = firstString(obj, "rel", "name", "type", "key");
                String href = firstValue(obj, "href", "url", "uri", "link", "value");
                if (href == null || href.isBlank()) {
                    // Fallback to object-style fields.
                    applyObject(obj, links);
                    continue;
                }

                if (rel != null && !rel.isBlank()) {
                    String lowerRel = rel.toLowerCase(Locale.ROOT);
                    if (lowerRel.contains("checkout")) {
                        links.setCheckout(href);
                        continue;
                    }
                    if (lowerRel.contains("payment")) {
                        links.setPayment(href);
                        continue;
                    }
                }

                // Unknown rel, still try best-effort assignment.
                if (links.getCheckout() == null || links.getCheckout().isBlank()) {
                    links.setCheckout(href);
                } else if (links.getPayment() == null || links.getPayment().isBlank()) {
                    links.setPayment(href);
                }
                continue;
            }

            if (entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString()) {
                String value = entry.getAsString();
                if (links.getCheckout() == null || links.getCheckout().isBlank()) {
                    links.setCheckout(value);
                } else if (links.getPayment() == null || links.getPayment().isBlank()) {
                    links.setPayment(value);
                }
            }
        }
    }

    @Nullable
    private static String extractFromNamedField(@Nonnull JsonObject object, @Nonnull String fieldName) {
        JsonElement element = object.get(fieldName);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        return extractValue(element);
    }

    @Nullable
    private static String firstString(@Nonnull JsonObject object, @Nonnull String... keys) {
        for (String key : keys) {
            JsonElement element = object.get(key);
            if (element == null || element.isJsonNull()) {
                continue;
            }
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                String value = element.getAsString();
                if (!value.isBlank()) {
                    return value;
                }
            }
        }
        return null;
    }

    @Nullable
    private static String firstValue(@Nonnull JsonObject object, @Nonnull String... keys) {
        for (String key : keys) {
            JsonElement element = object.get(key);
            if (element == null || element.isJsonNull()) {
                continue;
            }
            String value = extractValue(element);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    @Nullable
    private static String extractValue(@Nonnull JsonElement element) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return element.getAsString();
        }

        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            String nested = firstValue(object, "href", "url", "uri", "link", "value");
            if (nested != null && !nested.isBlank()) {
                return nested;
            }
            String direct = firstString(object, "checkout", "payment");
            if (direct != null && !direct.isBlank()) {
                return direct;
            }
            return null;
        }

        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement child : array) {
                if (child == null || child.isJsonNull()) {
                    continue;
                }
                String value = extractValue(child);
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
        }

        return null;
    }
}
