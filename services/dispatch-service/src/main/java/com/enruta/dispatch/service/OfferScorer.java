package com.enruta.dispatch.service;

import java.util.Comparator;
import java.util.List;

/**
 * Ranking de candidatos: 70% cercania, 30% calificacion.
 *
 * <p>Se deja como funcion pura para poder probar el orden sin Redis ni base de datos.
 */
public final class OfferScorer {

    private static final double PROXIMITY_WEIGHT = 0.7;
    private static final double RATING_WEIGHT = 0.3;

    public record Ranked(CandidateFinder.Candidate candidate, double score) {}

    public static double score(int distanceMeters, int radiusMeters, double rating) {
        double proximity = 1.0 - Math.min((double) distanceMeters, radiusMeters) / radiusMeters;
        return PROXIMITY_WEIGHT * proximity + RATING_WEIGHT * (rating / 5.0);
    }

    public static List<Ranked> rank(
            List<CandidateFinder.Candidate> candidates,
            int radiusMeters,
            java.util.function.ToDoubleFunction<java.util.UUID> ratingOf) {
        return candidates.stream()
                .map(
                        c ->
                                new Ranked(
                                        c,
                                        score(
                                                c.distanceMeters(),
                                                radiusMeters,
                                                ratingOf.applyAsDouble(c.driverId()))))
                .sorted(Comparator.comparingDouble(Ranked::score).reversed())
                .toList();
    }

    private OfferScorer() {}
}
