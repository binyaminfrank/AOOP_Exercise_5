import java.util.*;
import static java.util.stream.Collectors.*;

/** Similarity-based recommender with bias correction. */
class SimilarityBasedRecommender<T extends Item> extends RecommenderSystem<T> {

    // Thresholds from the requirements
    /** Minimum number of shared items between users to calculate similarity. */
    private static final int MIN_SHARED_ITEMS = 10;
    
    /** Number of similar users to consider for recommendations. */
    private static final int TOP_K_SIMILAR_USERS = 10;
    
    /** Minimum number of similar users who must have rated an item to recommend it. */
    private static final int MIN_SIM_USERS_PER_ITEM = 5;

    // biases 
    private final double globalBias;
    private final Map<Integer, Double> itemBiasByItem; 
    private final Map<Integer, Double> userBiasByUser; 

    // bias-free ratings: userId -> (itemId -> biasFreeRating)
    private final Map<Integer, Map<Integer, Double>> biasFreeRatingsByUser;

    public SimilarityBasedRecommender(Map<Integer, User> users,
                                      Map<Integer, T> items,
                                      List<Rating<T>> ratings) {
        super(users, items, ratings);

        // Step 1: Global bias (average of all ratings in the system)
        this.globalBias = ratings.stream()
                .mapToDouble(Rating::getRating)
                .average()
                .orElse(0.0);

        // Step 2: Item bias (how much each item differs from global average)
        this.itemBiasByItem = ratings.stream()
                .collect(groupingBy(
                        Rating::getItemId,
                        averagingDouble(r -> r.getRating() - globalBias)
                ));

        // Step 3: User bias (how much each user differs after removing global and item bias)
        this.userBiasByUser = ratings.stream()
                .collect(groupingBy(
                        Rating::getUserId,
                        averagingDouble(r -> r.getRating() - globalBias - getItemBias(r.getItemId()))
                ));

        // Step 4: Calculate bias-free ratings for similarity comparisons
        this.biasFreeRatingsByUser = ratingsByUser.entrySet().stream()
                .collect(toMap(
                        Map.Entry::getKey, 
                        e -> e.getValue().stream().collect(toMap(
                                Rating::getItemId, 
                                r -> r.getRating()
                                        - globalBias
                                        - getItemBias(r.getItemId())
                                        - getUserBias(r.getUserId())
                        ))
                ));
    }

    /**
     * Calculates similarity between two users using dot product.
     * 
     * The dot product measures how similarly two users rate items.
     * Higher value means more similar taste.
     * 
     * @param u1 the first user's ID
     * @param u2 the second user's ID
     * @return similarity score, or 0.0 if they have fewer than 10 shared items
     */
    public double getSimilarity(int u1, int u2) {

        // Get bias-free ratings for both users
        Map<Integer, Double> biasFreeOne = biasFreeRatingsByUser.entrySet().stream()
                .filter(e -> e.getKey() == u1)
                .map(Map.Entry::getValue)
                .reduce(Collections.emptyMap(), (a, b) -> b);

        Map<Integer, Double> biasFreeTwo = biasFreeRatingsByUser.entrySet().stream()
                .filter(e -> e.getKey() == u2)
                .map(Map.Entry::getValue)
                .reduce(Collections.emptyMap(), (a, b) -> b);

        // Find items both users have rated
        Set<Integer> sharedItems = biasFreeOne.keySet().stream()
                .filter(biasFreeTwo::containsKey)
                .collect(toSet());

        // Need at least 10 shared items to calculate meaningful similarity
        if (sharedItems.size() < MIN_SHARED_ITEMS) {
            return 0.0;
        }

        // Calculate dot product: sum of (rating1 × rating2) for each shared item
        return sharedItems.stream()
                .mapToDouble(itemId -> biasFreeOne.get(itemId) * biasFreeTwo.get(itemId))
                .sum();
    }

    /**
     * Recommends top 10 items based on similar users' preferences.
     * 
     * Steps:
     * <ol>
     *   <li>Find the 10 most similar users</li>
     *   <li>Look at items those users rated</li>
     *   <li>Filter for items at least 5 similar users rated</li>
     *   <li>Predict ratings for those items</li>
     *   <li>Return top 10 predictions</li>
     * </ol>
     * 
     * @param userId the ID of the user to recommend items for
     * @return list of top 10 items similar users liked
     */
    @Override
    public List<T> recommendTop10(int userId) {

        // Get items this user already rated (don't recommend those)
        Set<Integer> ratedByUser = getRatedItemIds(userId);

        // Find top 10 most similar users (by dot product similarity)
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

        // Find candidate items: at least 5 similar users rated it, and user hasn't rated it
        Set<Integer> candidateItemIds = ratings.stream()
                .filter(r -> similarUserIds.contains(r.getUserId()))
                .collect(groupingBy(Rating::getItemId, counting()))
                .entrySet().stream()
                .filter(e -> e.getValue() >= MIN_SIM_USERS_PER_ITEM)
                .map(Map.Entry::getKey)
                .filter(itemId -> !ratedByUser.contains(itemId))
                .collect(toSet());

        // Predict rating for each candidate item
        Map<Integer, Double> predictedByItem = candidateItemIds.stream()
                .collect(toMap(
                        itemId -> itemId,
                        itemId -> predictRating(userId, itemId, topSimilarUsers)
                ));

        // Sort and return top 10
        return top10FromScores(predictedByItem);
    }

    // ---------------- Bias getters and printing ----------------

    /** Prints the global bias to console. */
    public void printGlobalBias() {
        System.out.println("Global bias: " + String.format("%.2f", globalBias));
    }

    /**
     * Gets the global bias (overall average rating).
     * 
     * @return the global bias value
     */
    public Double getGlobalBias() {
        return globalBias;
    }

    /** Prints the item bias to console. */
    public void printItemBias(int itemId) {
        System.out.println("Item bias for item " + itemId + ": " + String.format("%.2f", getItemBias(itemId)));
    }

    /**
     * Gets the bias for a specific item.
     * 
     * Item bias tells us if this item is rated higher or lower than average.
     * Positive = rated higher, Negative = rated lower.
     * 
     * @param itemId the ID of the item
     * @return the item's bias value, or 0.0 if not found
     */
    public double getItemBias(int itemId) {
        return itemBiasByItem.entrySet().stream()
                .filter(e -> e.getKey() == itemId)
                .map(Map.Entry::getValue)
                .reduce(0.0, (a, b) -> b);
    }

    /** Prints the user bias to console. */
    public void printUserBias(int userId) {
        System.out.println("User bias for user " + userId + ": " + String.format("%.2f", getUserBias(userId)));
    }

    /**
     * Gets the bias for a specific user.
     * 
     * User bias tells us if this user rates higher or lower than expected.
     * Positive = generous rater, Negative = harsh rater.
     * 
     * @param userId the ID of the user
     * @return the user's bias value, or 0.0 if not found
     */
    public double getUserBias(int userId) {
        return userBiasByUser.entrySet().stream()
                .filter(e -> e.getKey() == userId)
                .map(Map.Entry::getValue)
                .reduce(0.0, (a, b) -> b);
    }

    // ---------------- Prediction helper ----------------

    /**
     * Predicts what rating a user would give to an item.
     * 
     * Formula: predicted = global + itemBias + userBias + weightedAvg(similar users' bias-free ratings)
     * 
     * This combines:
     * <ul>
     *   <li>Overall average (global bias)</li>
     *   <li>How this item is typically rated (item bias)</li>
     *   <li>How this user typically rates (user bias)</li>
     *   <li>What similar users thought (weighted by their similarity)</li>
     * </ul>
     * 
     * @param userId the user to predict for
     * @param itemId the item to predict
     * @param topSimilarUsers map of similar user IDs to their similarity scores
     * @return predicted rating
     */
    private double predictRating(int userId, int itemId, Map<Integer, Double> topSimilarUsers) {

        // Calculate weighted sum: similarity × bias-free rating
        // Only include similar users who actually rated this item
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

        // Calculate sum of similarities
        double denominator = topSimilarUsers.entrySet().stream()
                .filter(e -> biasFreeRatingsByUser.entrySet().stream()
                        .filter(x -> Objects.equals(x.getKey(), e.getKey()))
                        .flatMap(x -> x.getValue().entrySet().stream())
                        .anyMatch(p -> p.getKey() == itemId))
                .mapToDouble(Map.Entry::getValue)
                .sum();

        // Weighted average of bias-free ratings
        double weightedBiasFree = (denominator == 0.0) ? 0.0 : (numerator / denominator);

        // Add back all the biases to get final prediction
        return getGlobalBias()
                + getItemBias(itemId)
                + getUserBias(userId)
                + weightedBiasFree;
    }
}
