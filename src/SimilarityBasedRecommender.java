import java.util.*;

import static java.util.stream.Collectors.*;

/** Similarity-based recommender with bias correction. */
class SimilarityBasedRecommender<T extends Item> extends RecommenderSystem<T> {

    // thresholds from the instructions
    private static final int MIN_SHARED_ITEMS = 10;
    private static final int TOP_K_SIMILAR_USERS = 10;
    private static final int MIN_SIM_USERS_PER_ITEM = 5;

    // biases (computed once)
    private final double globalBias;
    private final Map<Integer, Double> itemBiasByItem; // itemId -> item bias
    private final Map<Integer, Double> userBiasByUser; // userId -> user bias

    // bias-free ratings: userId -> (itemId -> biasFreeRating)
    private final Map<Integer, Map<Integer, Double>> biasFreeRatingsByUser;

    public SimilarityBasedRecommender(Map<Integer, User> users,
                                      Map<Integer, T> items,
                                      List<Rating<T>> ratings) {
        super(users, items, ratings);

        // Global bias: average of all ratings
        this.globalBias = ratings.stream()
                .mapToDouble(Rating::getRating)
                .average()
                .orElse(0.0);

        // Item bias: avg(rating - globalBias) per item
        this.itemBiasByItem = ratings.stream()
                .collect(groupingBy(
                        Rating::getItemId,
                        averagingDouble(r -> r.getRating() - globalBias)
                ));

        // User bias: avg(rating - globalBias - itemBias(item)) per user
        this.userBiasByUser = ratings.stream()
                .collect(groupingBy(
                        Rating::getUserId,
                        averagingDouble(r -> r.getRating() - globalBias - getItemBias(r.getItemId()))
                ));

        // Bias-free ratings map:
        // biasFree = rating - global - itemBias - userBias
        this.biasFreeRatingsByUser = ratingsByUser.entrySet().stream()
                .collect(toMap(
                        Map.Entry::getKey, // userId
                        e -> e.getValue().stream().collect(toMap(
                                Rating::getItemId, // itemId
                                r -> r.getRating()
                                        - globalBias
                                        - getItemBias(r.getItemId())
                                        - getUserBias(r.getUserId())
                        ))
                ));
    }

    /** Dot-product similarity; 0 if <10 shared items. */
    public double getSimilarity(int u1, int u2) {

        Map<Integer, Double> biasFreeOne = biasFreeRatingsByUser.entrySet().stream()
                .filter(e -> e.getKey() == u1)
                .map(Map.Entry::getValue)
                .reduce(Collections.emptyMap(), (a, b) -> b);

        Map<Integer, Double> biasFreeTwo = biasFreeRatingsByUser.entrySet().stream()
                .filter(e -> e.getKey() == u2)
                .map(Map.Entry::getValue)
                .reduce(Collections.emptyMap(), (a, b) -> b);

        Set<Integer> sharedItems = biasFreeOne.keySet().stream()
                .filter(biasFreeTwo::containsKey)
                .collect(toSet());

        if (sharedItems.size() < MIN_SHARED_ITEMS) {
            return 0.0;
        }

        return sharedItems.stream()
                .mapToDouble(itemId -> biasFreeOne.get(itemId) * biasFreeTwo.get(itemId))
                .sum();
    }

    @Override
    public List<T> recommendTop10(int userId) {

        // items already rated by this user (no getOrDefault)
        Set<Integer> ratedByUser = ratedItemsByUser.entrySet().stream()
                .filter(e -> e.getKey() == userId)
                .flatMap(e -> e.getValue().stream())
                .collect(toSet());

        // Top 10 similar users
        Map<Integer, Double> topSimilarUsers = users.keySet().stream()
                .filter(otherId -> otherId != userId)
                .collect(toMap(
                        otherId -> otherId,
                        otherId -> getSimilarity(userId, otherId)
                ))
                .entrySet().stream()
                .filter(e -> e.getValue() > 0.0)
                .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
                .limit(TOP_K_SIMILAR_USERS)
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));

        Set<Integer> similarUserIds = topSimilarUsers.keySet();

        // Candidate items: at least 5 similar users rated, and user didn't rate
        Set<Integer> candidateItemIds = ratings.stream()
                .filter(r -> similarUserIds.contains(r.getUserId()))
                .collect(groupingBy(Rating::getItemId, counting()))
                .entrySet().stream()
                .filter(e -> e.getValue() >= MIN_SIM_USERS_PER_ITEM)
                .map(Map.Entry::getKey)
                .filter(itemId -> !ratedByUser.contains(itemId))
                .collect(toSet());

        // Predict rating for each candidate
        Map<Integer, Double> predictedByItem = candidateItemIds.stream()
                .collect(toMap(
                        itemId -> itemId,
                        itemId -> predictRating(userId, itemId, topSimilarUsers)
                ));

        // Sort by predicted desc, tie: more ratings, then name
        return predictedByItem.entrySet().stream()
                .sorted(
                        Comparator.<Map.Entry<Integer, Double>>comparingDouble(Map.Entry::getValue).reversed()
                                .thenComparing(e -> countByItem.get(e.getKey()), Comparator.reverseOrder())
                                .thenComparing(e -> items.get(e.getKey()).getName())
                )
                .limit(NUM_OF_RECOMMENDATIONS)
                .map(e -> items.get(e.getKey()))
                .collect(toList());
    }

    // ---------------- bias printing / getters ----------------

    public void printGlobalBias() {
        System.out.println("Global bias: " + String.format("%.2f", globalBias));
    }

    public Double getGlobalBias() {
        return globalBias;
    }

    public void printItemBias(int itemId) {
        System.out.println("Item bias for item " + itemId + ": " + String.format("%.2f", getItemBias(itemId)));
    }

    public double getItemBias(int itemId) {
        return itemBiasByItem.entrySet().stream()
                .filter(e -> e.getKey() == itemId)
                .map(Map.Entry::getValue)
                .reduce(0.0, (a, b) -> b);
    }

    public void printUserBias(int userId) {
        System.out.println("User bias for user " + userId + ": " + String.format("%.2f", getUserBias(userId)));
    }

    public double getUserBias(int userId) {
        return userBiasByUser.entrySet().stream()
                .filter(e -> e.getKey() == userId)
                .map(Map.Entry::getValue)
                .reduce(0.0, (a, b) -> b);
    }

    // ---------------- prediction helper ----------------

    // predicted = global + itemBias + userBias + weightedAvg(biasFree of similar users)
    private double predictRating(int userId, int itemId, Map<Integer, Double> topSimilarUsers) {

        double numerator = topSimilarUsers.entrySet().stream()
                .filter(e -> biasFreeRatingsByUser.entrySet().stream()
                        .filter(x -> Objects.equals(x.getKey(), e.getKey()))
                        .flatMap(x -> x.getValue().entrySet().stream())
                        .anyMatch(p -> p.getKey() == itemId))
                .mapToDouble(e -> {
                    double bf = biasFreeRatingsByUser.entrySet().stream()
                            .filter(x -> Objects.equals(x.getKey(), e.getKey()))
                            .flatMap(x -> x.getValue().entrySet().stream())
                            .filter(p -> p.getKey() == itemId)
                            .map(Map.Entry::getValue)
                            .reduce(0.0, (a, b) -> b);

                    return e.getValue() * bf;
                })
                .sum();

        double denominator = topSimilarUsers.entrySet().stream()
                .filter(e -> biasFreeRatingsByUser.entrySet().stream()
                        .filter(x -> Objects.equals(x.getKey(), e.getKey()))
                        .flatMap(x -> x.getValue().entrySet().stream())
                        .anyMatch(p -> p.getKey() == itemId))
                .mapToDouble(Map.Entry::getValue)
                .sum();

        double weightedBiasFree = (denominator == 0.0) ? 0.0 : (numerator / denominator);

        return getGlobalBias()
                + getItemBias(itemId)
                + getUserBias(userId)
                + weightedBiasFree;
    }
}
