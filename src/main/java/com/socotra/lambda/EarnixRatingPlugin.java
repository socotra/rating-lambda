package com.socotra.lambda;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.fasterxml.jackson.databind.*;
import com.socotra.coremodel.RatingSet;
import com.socotra.coremodel.RatingItem;
import com.socotra.deployment.AbstractDeploymentFactory;
import com.socotra.deployment.customer.*;
import com.socotra.platform.tools.ULID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.*;

public class EarnixRatingPlugin implements RatePlugin {

//    protected static final ObjectMapper mapper = AbstractDeploymentFactory
//            .defaultMapper()
//            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
//            .configure(SerializationFeature.INDENT_OUTPUT, true)
//            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
//
//    protected static final ObjectWriter writer = mapper
//            .writerWithDefaultPrettyPrinter();
//
//
//    public RatingSet handleLambdaInput(Map<String, Object> event, LambdaLogger logger) {
//        logger.log("=== handleLambdaInput START ===");
//        logger.log("Event keys: " + event.keySet());
//
//        try {
//            // 1. Extract raw JSON body
//            String rawBody = (String) event.get("body");
//            logger.log("Raw body string: " + rawBody);
//            if (rawBody == null || rawBody.isBlank()) {
//                throw new IllegalArgumentException("Missing or empty 'body' in event");
//            }
//
//            // 2. Parse JSON
//            JsonNode root = mapper.readTree(rawBody);
//            logger.log("Parsed JSON root:\n" + writer.writeValueAsString(root));
//
//            // 3. Branch on quote vs segment
//            if (root.has("quote")) {
//                logger.log("Found 'quote' node, mapping to PersonalAutoQuoteRequest");
//                RatePlugin.PersonalAutoQuoteRequest request =
//                        mapper.treeToValue(root, RatePlugin.PersonalAutoQuoteRequest.class);
//                logger.log("Mapped PersonalAutoQuoteRequest: " + writer.writeValueAsString(request));
//
//                RatingSet ratingSet = rate(request, logger);
//                logger.log("rate(quote) produced: " + ratingSet);
//                logger.log("=== handleLambdaInput END (quote) (success) ===");
//                return ratingSet;
//
//            } else if (root.has("segment")) {
//                logger.log("Found 'segment' node, mapping to PersonalAutoRequest");
//                RatePlugin.PersonalAutoRequest request =
//                        mapper.treeToValue(root, RatePlugin.PersonalAutoRequest.class);
//                logger.log("Mapped PersonalAutoRequest: " + writer.writeValueAsString(request));
//
//                RatingSet ratingSet = rate(request, logger);
//                logger.log("rate(segment) produced: " + ratingSet);
//                logger.log("=== handleLambdaInput END (segment) ===");
//                return ratingSet;
//
//            } else {
//                throw new IllegalArgumentException("Invalid payload: must contain 'quote' or 'segment'");
//            }
//
//        } catch (Exception e) {
//            logger.log("Exception in handleLambdaInput: " + e.toString());
//            for (StackTraceElement ste : e.getStackTrace()) {
//                logger.log("  at " + ste.toString());
//            }
//            logger.log("=== handleLambdaInput END (error) ===");
//            throw new RuntimeException("Failed to handle Lambda input", e);
//        }
//    }

    private RatingItem flatRateWithTaxes(ULID locator, double baseAmount) {
        double total =
                baseAmount +                  // Base premium
                        (baseAmount * 0.08) +         // FSL (8%)
                        (baseAmount * 0.10) +         // GST (10%)
                        (baseAmount * 0.09);          // SD (9%)

        return RatingItem.builder()
                .elementLocator(locator)
                .chargeType(ChargeType.premium)  // Keep using 'premium' as combined total
                .rate(BigDecimal.valueOf(total))
                .build();
    }

    private double getRatingFactor(PersonalVehicle vehicle, PersonalAuto quote, String coverageType) {
        String coverage = coverageType != null ? coverageType : "";

        String stateCode = vehicle.data().vehicleLicenseState();
        String state = stateCode != null ? stateCode : "";

        String damageCode = vehicle.data().vehicleDamage();
        String damage = damageCode != null ? damageCode : "";

        String licenceCode = vehicle.data().primaryDriverLicence();
        String licence = licenceCode != null ? licenceCode : "";

        String excessCode = vehicle.data().excess();
        String excess = excessCode != null ? excessCode : "";

        String usageCode = vehicle.data().vehicleUsage();
        String usage = usageCode != null ? usageCode : "";

        double stateFactor = switch (state) {
            case "NSW" -> switch (coverage) {
                case "Fire" -> 1.1;
                case "Theft" -> 1.2;
                case "OwnDamage" -> 1.3;
                case "ThirdParty" -> 1.4;
                default -> 1.0;
            };
            case "WA" -> switch (coverage) {
                case "Fire" -> 1.2;
                case "Theft" -> 1.3;
                case "OwnDamage" -> 1.4;
                case "ThirdParty" -> 1.5;
                default -> 1.0;
            };
            case "QLD" -> switch (coverage) {
                case "Fire" -> 1.3;
                case "Theft" -> 1.4;
                case "OwnDamage" -> 1.5;
                case "ThirdParty" -> 1.6;
                default -> 1.0;
            };
            case "VIC" -> switch (coverage) {
                case "Fire" -> 1.4;
                case "Theft" -> 1.5;
                case "OwnDamage" -> 1.6;
                case "ThirdParty" -> 1.7;
                default -> 1.0;
            };
            case "NT" -> switch (coverage) {
                case "Fire" -> 1.5;
                case "Theft" -> 1.6;
                case "OwnDamage" -> 1.7;
                case "ThirdParty" -> 1.8;
                default -> 1.0;
            };
            case "ACT" -> switch (coverage) {
                case "Fire" -> 1.6;
                case "Theft" -> 1.7;
                case "OwnDamage" -> 1.8;
                case "ThirdParty" -> 1.9;
                default -> 1.0;
            };
            case "TAS" -> switch (coverage) {
                case "Fire" -> 1.7;
                case "Theft" -> 1.8;
                case "OwnDamage" -> 1.9;
                case "ThirdParty" -> 2.0;
                default -> 1.0;
            };
            case "SA" -> switch (coverage) {
                case "Fire" -> 1.8;
                case "Theft" -> 1.9;
                case "OwnDamage" -> 2.0;
                case "ThirdParty" -> 2.1;
                default -> 1.0;
            };
            default -> 1.0;
        };

        double damageFactor = switch (damage) {
            case "No Damage" -> switch (coverage) {
                case "Fire" -> 1.0;
                case "Theft" -> 1.1;
                case "OwnDamage" -> 1.2;
                case "ThirdParty" -> 1.3;
                default -> 1.0;
            };
            case "Hail Damage" -> switch (coverage) {
                case "Fire" -> 1.5;
                case "Theft" -> 1.6;
                case "OwnDamage" -> 1.7;
                case "ThirdParty" -> 1.8;
                default -> 1.0;
            };
            case "Accident Damage" -> switch (coverage) {
                case "Fire" -> 2.0;
                case "Theft" -> 2.1;
                case "OwnDamage" -> 2.2;
                case "ThirdParty" -> 2.3;
                default -> 1.0;
            };
            default -> 1.0;
        };

        double licenceFactor = switch (licence) {
            case "Learner Permit or Licence" -> switch (coverage) {
                case "Fire" -> 1.9;
                case "Theft" -> 2.0;
                case "OwnDamage" -> 2.1;
                case "ThirdParty" -> 2.2;
                default -> 1.0;
            };
            case "Provisional/Probationary/Restricted licence" -> switch (coverage) {
                case "Fire" -> 1.7;
                case "Theft" -> 1.8;
                case "OwnDamage" -> 1.9;
                case "ThirdParty" -> 2.0;
                default -> 1.0;
            };
            case "Full/Open licence" -> switch (coverage) {
                case "Fire" -> 1.0;
                case "Theft" -> 1.1;
                case "OwnDamage" -> 2.2;
                case "ThirdParty" -> 2.3;
                default -> 1.0;
            };
            case "International licence" -> switch (coverage) {
                case "Fire" -> 1.5;
                case "Theft" -> 1.7;
                case "OwnDamage" -> 2.8;
                case "ThirdParty" -> 1.2;
                default -> 1.0;
            };
            default -> 1.0;
        };

        double excessFactor = switch (excess) {
            case "100" -> switch (coverage) {
                case "Fire" -> 2.0;
                case "Theft" -> 2.1;
                case "OwnDamage" -> 1.8;
                case "ThirdParty" -> 1.5;
                default -> 1.0;
            };
            case "200" -> switch (coverage) {
                case "Fire" -> 1.9;
                case "Theft" -> 2.0;
                case "OwnDamage" -> 1.7;
                case "ThirdParty" -> 1.4;
                default -> 1.0;
            };
            case "300" -> switch (coverage) {
                case "Fire" -> 1.8;
                case "Theft" -> 1.9;
                case "OwnDamage" -> 1.6;
                case "ThirdParty" -> 1.3;
                default -> 1.0;
            };
            case "500" -> switch (coverage) {
                case "Fire" -> 1.7;
                case "Theft" -> 1.8;
                case "OwnDamage" -> 1.5;
                case "ThirdParty" -> 1.2;
                default -> 1.0;
            };
            case "600" -> switch (coverage) {
                case "Fire" -> 1.6;
                case "Theft" -> 1.7;
                case "OwnDamage" -> 1.4;
                case "ThirdParty" -> 1.1;
                default -> 1.0;
            };
            case "900" -> switch (coverage) {
                case "Fire" -> 1.0;
                case "Theft" -> 1.6;
                case "OwnDamage" -> 1.3;
                case "ThirdParty" -> 1.0;
                default -> 1.0;
            };
            case "1000" -> switch (coverage) {
                case "Fire" -> 0.9;
                case "Theft" -> 1.5;
                case "OwnDamage" -> 1.2;
                case "ThirdParty" -> 0.9;
                default -> 1.0;
            };
            case "1500" -> switch (coverage) {
                case "Fire" -> 0.8;
                case "Theft" -> 1.4;
                case "OwnDamage" -> 1.1;
                case "ThirdParty" -> 0.8;
                default -> 1.0;
            };
            default -> 1.0;
        };

        double usageFactor = switch (usage) {
            case "1-2" -> switch (coverage) {
                case "Theft" -> 1.5;
                case "OwnDamage" -> 1.6;
                case "ThirdParty" -> 1.7;
                default -> 1.8;
            };
            case "3-4" -> switch (coverage) {
                case "Theft" -> 1.4;
                case "OwnDamage" -> 1.3;
                case "ThirdParty" -> 1.2;
                default -> 1.1;
            };
            case "5+" -> switch (coverage) {
                case "Theft" -> 1.6;
                case "OwnDamage" -> 1.5;
                case "ThirdParty" -> 1.4;
                default -> 1.3;
            };
            default -> 1.0;
        };

        int vehicleYear = vehicle.data().year() != null ? vehicle.data().year() : 2020;
        double yearFactor = switch (vehicleYear) {
            case 2024 -> switch (coverage) {
                case "Theft" -> 2.1;
                case "OwnDamage" -> 3.1;
                case "ThirdParty" -> 3.1;
                default -> 2.1; // Relativity
            };
            case 2023 -> switch (coverage) {
                case "Theft" -> 2.0;
                case "OwnDamage" -> 3.0;
                case "ThirdParty" -> 3.0;
                default -> 2.0;
            };
            case 2022 -> switch (coverage) {
                case "Theft" -> 1.9;
                case "OwnDamage" -> 2.9;
                case "ThirdParty" -> 2.9;
                default -> 1.9;
            };
            case 2021 -> switch (coverage) {
                case "Theft" -> 1.8;
                case "OwnDamage" -> 2.8;
                case "ThirdParty" -> 2.8;
                default -> 1.8;
            };
            case 2020 -> switch (coverage) {
                case "Theft" -> 1.7;
                case "OwnDamage" -> 2.7;
                case "ThirdParty" -> 2.7;
                default -> 1.7;
            };
            case 2019 -> switch (coverage) {
                case "Theft" -> 1.6;
                case "OwnDamage" -> 2.6;
                case "ThirdParty" -> 2.6;
                default -> 1.6;
            };
            case 2018 -> switch (coverage) {
                case "Theft" -> 1.5;
                case "OwnDamage" -> 2.5;
                case "ThirdParty" -> 2.5;
                default -> 1.5;
            };
            case 2017 -> switch (coverage) {
                case "Theft" -> 1.4;
                case "OwnDamage" -> 2.4;
                case "ThirdParty" -> 2.4;
                default -> 1.4;
            };
            case 2016 -> switch (coverage) {
                case "Theft" -> 1.3;
                case "OwnDamage" -> 2.3;
                case "ThirdParty" -> 2.3;
                default -> 1.3;
            };
            case 2015 -> switch (coverage) {
                case "Theft" -> 1.2;
                case "OwnDamage" -> 2.2;
                case "ThirdParty" -> 2.2;
                default -> 1.2;
            };
            case 2014 -> switch (coverage) {
                case "Theft" -> 1.1;
                case "OwnDamage" -> 2.1;
                case "ThirdParty" -> 2.1;
                default -> 1.1;
            };
            case 2013 -> switch (coverage) {
                case "Theft" -> 1.0;
                case "OwnDamage" -> 2.0;
                case "ThirdParty" -> 2.0;
                default -> 1.0;
            };
            case 2012 -> switch (coverage) {
                case "Theft" -> 0.9;
                case "OwnDamage" -> 1.9;
                case "ThirdParty" -> 1.9;
                default -> 0.9;
            };
            case 2011 -> switch (coverage) {
                case "Theft" -> 0.8;
                case "OwnDamage" -> 1.8;
                case "ThirdParty" -> 1.8;
                default -> 0.8;
            };
            case 2010 -> switch (coverage) {
                case "Theft" -> 0.7;
                case "OwnDamage" -> 1.7;
                case "ThirdParty" -> 1.7;
                default -> 0.7;
            };
            default -> {
                if (vehicleYear < 2010) {
                    yield switch (coverage) {
                        case "Theft" -> 0.6;
                        case "OwnDamage" -> 1.6;
                        case "ThirdParty" -> 1.6;
                        default -> 0.6;
                    };
                } else {
                    yield 1.0;
                }
            }
        };

        int driverLicenseAge = 5; // default if no dob
        if (vehicle.data().drivers() != null && !vehicle.data().drivers().isEmpty()) {
            LocalDate dob = vehicle.data()
                    .drivers()
                    .get(0)    // first driver
                    .dateOfBirth();
            if (dob != null) {
                // license assumed at 17
                driverLicenseAge = Period.between(dob, LocalDate.now()).getYears() - 17;
            }
        }
        // clamp minimum to 1
        if (driverLicenseAge < 1) driverLicenseAge = 1;

        double driverLicenseAgeFactor = switch (driverLicenseAge) {
            case 1 -> switch (coverage) {
                case "Theft" -> 1.5;
                case "OwnDamage" -> 1.9;
                case "ThirdParty" -> 1.5;
                default -> 1.5;
            };
            case 2 -> switch (coverage) {
                case "Theft" -> 1.4;
                case "OwnDamage" -> 1.8;
                case "ThirdParty" -> 1.4;
                default -> 1.4;
            };
            case 3 -> switch (coverage) {
                case "Theft" -> 1.4;
                case "OwnDamage" -> 1.7;
                case "ThirdParty" -> 1.4;
                default -> 1.4;
            };
            case 4 -> switch (coverage) {
                case "Theft" -> 1.4;
                case "OwnDamage" -> 1.6;
                case "ThirdParty" -> 1.4;
                default -> 1.4;
            };
            case 5 -> switch (coverage) {
                case "Theft" -> 1.4;
                case "OwnDamage" -> 1.5;
                case "ThirdParty" -> 1.4;
                default -> 1.4;
            };
            case 6 -> switch (coverage) {
                case "Theft" -> 1.3;
                case "OwnDamage" -> 1.4;
                case "ThirdParty" -> 1.3;
                default -> 1.3;
            };
            case 7 -> switch (coverage) {
                case "Theft" -> 1.3;
                case "OwnDamage" -> 1.3;
                case "ThirdParty" -> 1.3;
                default -> 1.3;
            };
            case 8 -> switch (coverage) {
                case "Theft" -> 1.3;
                case "OwnDamage" -> 1.2;
                case "ThirdParty" -> 1.3;
                default -> 1.3;
            };
            case 9 -> switch (coverage) {
                case "Theft" -> 1.3;
                case "OwnDamage" -> 1.1;
                case "ThirdParty" -> 1.3;
                default -> 1.3;
            };
            case 10 -> switch (coverage) {
                case "Theft" -> 1.2;
                case "OwnDamage" -> 1.0;
                case "ThirdParty" -> 1.2;
                default -> 1.2;
            };
            default -> switch (coverage) {
                case "Theft" -> 1.3;
                case "OwnDamage" -> 0.9;
                case "ThirdParty" -> 1.3;
                default -> 1.3;
            };
        };

        // Parse the priorClaims field (string) into an integer count
        int claims = 0; // default
        if (quote != null
                && quote.data() != null
                && quote.data().claims() != null) {
            String claimStr = quote.data().claims();
            if ("5+".equals(claimStr)) {
                claims = 5;
            } else {
                try {
                    claims = Integer.parseInt(claimStr);
                } catch (NumberFormatException ignored) {
                    claims = 0;
                }
            }
        }

        // Apply coverage‐specific relativity from your table
        double claimsFactor = switch (claims) {
            case 0 -> switch (coverage) {
                case "Theft"     -> 1.2;
                case "OwnDamage" -> 1.3;
                case "ThirdParty"-> 1.4;
                default          -> 1.1;  // generic “Relativity” column
            };
            case 1 -> switch (coverage) {
                case "Theft"     -> 1.3;
                case "OwnDamage" -> 1.4;
                case "ThirdParty"-> 1.5;
                default          -> 1.2;
            };
            case 2 -> switch (coverage) {
                case "Theft"     -> 1.4;
                case "OwnDamage" -> 1.5;
                case "ThirdParty"-> 1.6;
                default          -> 1.3;
            };
            case 3 -> switch (coverage) {
                case "Theft"     -> 1.5;
                case "OwnDamage" -> 1.6;
                case "ThirdParty"-> 1.7;
                default          -> 1.4;
            };
            case 4 -> switch (coverage) {
                case "Theft"     -> 1.6;
                case "OwnDamage" -> 1.7;
                case "ThirdParty"-> 1.8;
                default          -> 1.5;
            };
            default -> {
                // Any count ≥5 (“5+”) falls into this case:
                yield switch (coverage) {
                    case "Theft"     -> 2.8;
                    case "OwnDamage" -> 2.9;
                    case "ThirdParty"-> 2.9;
                    default          -> 2.9;
                };
            }
        };

        return stateFactor * damageFactor * licenceFactor * excessFactor *
                usageFactor * yearFactor * driverLicenseAgeFactor * claimsFactor;
    }

    private RatingItem rateFire(PersonalVehicle vehicle, PersonalAuto quote, LambdaLogger logger) {
        double baseRate = 100.0;
        double finalRate = baseRate * getRatingFactor(vehicle, quote, "Fire");

        logger.log("Final rate is: " + finalRate);
        logger.log("Vehicle is : " + vehicle);

//        Element mockElement = Element.builder()
//                                    .type("FireQuote")
//                                    .locator(ULID.generate())
//                                    .tenantLocator(UUID.randomUUID())
//                                    .category(ElementCategory.coverage)
//                                    .build();
//        FireQuote fq = new FireQuote(vehicle.fire().PADeductible(), vehicle.fire().PALimit(), mockElement);

        return RatingItem.builder()
                .elementLocator(quote.locator())
                .chargeType(ChargeType.premium)
                .rate(BigDecimal.valueOf(finalRate))
                .build();
    }

    private RatingItem rateTheft(PersonalVehicle vehicle, PersonalAuto quote) {
        double baseRate = 100.0;
        double finalRate = baseRate * getRatingFactor(vehicle, quote, "Theft");
        return RatingItem.builder()
                .elementLocator(vehicle.locator())
                .chargeType(ChargeType.premium)
                .rate(BigDecimal.valueOf(finalRate))
                .build();
    }

    private RatingItem rateOwnDamage(PersonalVehicle vehicle, PersonalAuto quote) {
        double baseRate = 200.0;
        double finalRate = baseRate * getRatingFactor(vehicle, quote, "OwnDamage");
        return RatingItem.builder()
                .elementLocator(vehicle.ownDamage().locator())
                .chargeType(ChargeType.premium)
                .rate(BigDecimal.valueOf(finalRate))
                .build();
    }

    private RatingItem rateThirdParty(PersonalVehicle vehicle, PersonalAuto quote) {
        double baseRate = 80.0;
        double finalRate = baseRate * getRatingFactor(vehicle, quote, "ThirdParty");
        return RatingItem.builder()
                .elementLocator(vehicle.thirdParty().locator())
                .chargeType(ChargeType.premium)
                .rate(BigDecimal.valueOf(finalRate))
                .build();
    }

    private RatingItem rateWindscreen(PersonalVehicle vehicle) {
        return RatingItem.builder()
                .elementLocator(vehicle.windscreen().locator())
                .chargeType(ChargeType.premium)
                .rate(BigDecimal.valueOf(20))
                .build();
    }

    private RatingItem rateBabySeat(PersonalVehicle vehicle) {
        return RatingItem.builder()
                .elementLocator(vehicle.babySeat().locator())
                .chargeType(ChargeType.premium)
                .rate(BigDecimal.valueOf(20))
                .build();
    }

    public RatingSet rate(PersonalAutoQuoteRequest request, LambdaLogger logger) {
        PersonalAutoQuote personalAutoQuote = request.quote();
        BigDecimal duration = request.duration();
        List<RatingItem> ratingItems = new ArrayList<>();
        for(PersonalVehicle vehicle : personalAutoQuote.personalVehicles()) {
            if (vehicle.ownDamage() != null) {
                ratingItems.add(rateOwnDamage(vehicle, personalAutoQuote));
            }
            if (vehicle.fire() != null) {
                ratingItems.add(rateFire(vehicle, personalAutoQuote, logger));
            }
            if (vehicle.theft() != null) {
                ratingItems.add(rateTheft(vehicle, personalAutoQuote));
            }
            if (vehicle.thirdParty() != null) {
                ratingItems.add(rateThirdParty(vehicle, personalAutoQuote));
            }
            if (vehicle.windscreen() != null) {
                ratingItems.add(rateWindscreen(vehicle));
            }
            if (vehicle.babySeat() != null) {
                ratingItems.add(rateBabySeat(vehicle));
            }
        }

        if (ratingItems.isEmpty()) {
            ratingItems.add(flatRateWithTaxes(request.quote().locator(), 35));
        }

        return RatingSet.builder().ok(true).ratingItems(ratingItems).build();
    }

    public RatingSet rate(PersonalAutoRequest request, LambdaLogger logger) {
        BigDecimal duration = request.duration();
        List<RatingItem> ratingItems = new ArrayList<>();
        List<PersonalVehicle> personalVehicles = new ArrayList<>();
        request.segment().ifPresent(s -> personalVehicles.addAll(s.personalVehicles()));
        for(PersonalVehicle vehicle : personalVehicles){
            if (vehicle.ownDamage() != null) {
                ratingItems.add(rateOwnDamage(vehicle, request.segment().get()));
            }
            if (vehicle.fire() != null) {
                ratingItems.add(rateFire(vehicle, request.segment().get(), logger));
            }
            if (vehicle.theft() != null) {
                ratingItems.add(rateTheft(vehicle, request.segment().get()));
            }
            if (vehicle.thirdParty() != null) {
                ratingItems.add(rateThirdParty(vehicle, request.segment().get()));
            }
            if (vehicle.windscreen() != null) {
                ratingItems.add(rateWindscreen(vehicle));
            }
            if (vehicle.babySeat() != null) {
                ratingItems.add(rateBabySeat(vehicle));
            }
        }

        //request.segment().ifPresent(s-> ratingItems.add(rateFee(s.locator(), duration)));
        if (ratingItems.isEmpty()) {
            request.segment().ifPresent(s-> ratingItems.add(flatRateWithTaxes(request.segment().get().locator(), 35)));
        }

        return RatingSet.builder().ok(true).ratingItems(ratingItems).build();
    }
}
