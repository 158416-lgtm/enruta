package com.enruta.dispatch.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.function.ToDoubleFunction;
import org.junit.jupiter.api.Test;

class OfferScorerTest {

    @Test
    void closerDriverWinsWhenRatingsAreEqual() {
        double near = OfferScorer.score(300, 3000, 5.0);
        double far = OfferScorer.score(2500, 3000, 5.0);
        assertThat(near).isGreaterThan(far);
    }

    @Test
    void ratingCanBeatSlightDistance() {
        double closeLowRating = OfferScorer.score(200, 3000, 3.0);
        double slightlyFarHighRating = OfferScorer.score(600, 3000, 5.0);
        assertThat(slightlyFarHighRating).isGreaterThan(closeLowRating);
    }

    @Test
    void scoreIsBoundedAndRadiusZeroIsSafe() {
        assertThat(OfferScorer.score(0, 3000, 5.0)).isBetween(0.0, 1.0);
        assertThat(OfferScorer.score(3000, 3000, 0.0)).isBetween(0.0, 1.0);
        assertThat(OfferScorer.score(1_000_000, 3000, 5.0)).isBetween(0.0, 1.0);
    }

    @Test
    void rankingPutsBestCandidateFirst() {
        UUID ana = UUID.randomUUID();
        UUID beto = UUID.randomUUID();
        UUID carla = UUID.randomUUID();
        List<CandidateFinder.Candidate> candidates =
                List.of(
                        new CandidateFinder.Candidate(ana, 1200),
                        new CandidateFinder.Candidate(beto, 200),
                        new CandidateFinder.Candidate(carla, 600));
        ToDoubleFunction<UUID> ratings = id -> id.equals(ana) ? 5.0 : id.equals(beto) ? 3.0 : 4.5;

        List<OfferScorer.Ranked> ranked = OfferScorer.rank(candidates, 3000, ratings);

        assertThat(ranked).hasSize(3);
        // beto: 200 m con 3.0 de rating (0.833) supera a carla: 600 m con 4.5 (0.830)
        assertThat(ranked.getFirst().candidate().driverId()).isEqualTo(beto);
        assertThat(ranked.get(1).candidate().driverId()).isEqualTo(carla);
        // ana esta mas lejos y no compensa con su 5.0
        assertThat(ranked.getLast().candidate().driverId()).isEqualTo(ana);
    }
}
