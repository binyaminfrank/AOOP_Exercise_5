import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static java.util.stream.Collectors.*;

/** Abstract generic recommender system. */
abstract class RecommenderSystem<T extends Item> {
    protected final Map<Integer, User> users;
    protected final Map<Integer, T> items;
    protected final List<Rating<T>> ratings;

    
    /** Map of item IDs to all ratings for that item. */
    protected final Map<Integer, List<Rating<T>>> ratingsByItem;
    
    /** Map of item IDs to the count of ratings for that item. */
    protected final Map<Integer, Long> countByItem;
    
    /** Map of item IDs to the average rating for that item. */
    protected final Map<Integer, Double> avgByItem;
    
    /** Map of user IDs to the set of item IDs that user has rated. */
    protected final Map<Integer, Set<Integer>> ratedItemsByUser;
    
    /** Map of user IDs to all ratings made by that user. */
    protected final Map<Integer, List<Rating<T>>> ratingsByUser;

    /** Number of items to recommend. */
    protected final int NUM_OF_RECOMMENDATIONS = 10;

    protected RecommenderSystem(Map<Integer, User> users,
                                Map<Integer, T> items,
                                List<Rating<T>> ratings) {
        this.users = users;
        this.items = items;
        this.ratings = ratings;

        // Group all ratings by which item they're for
        // Example: itemId 5 -> [rating1, rating2, rating3...]
        this.ratingsByItem = ratings.stream()
                .collect(groupingBy(Rating::getItemId));

        // Count how many ratings each item has
        // Example: itemId 5 -> 150 (means 150 people rated item 5)
        this.countByItem = ratingsByItem.entrySet().stream()
                .collect(toMap(
                        Map.Entry::getKey,
                        e -> (long) e.getValue().size()
                ));

        // Calculate the average rating for each item
        // Example: itemId 5 -> 4.2 (average of all ratings for item 5)
        this.avgByItem = ratingsByItem.entrySet().stream()
                .collect(toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().stream()
                                .mapToDouble(Rating::getRating)
                                .average()
                                .orElse(0.0)
                ));

        // For each user, store which items they've rated
        // Example: userId 10 -> {itemId 5, itemId 12, itemId 23...}
        this.ratedItemsByUser = ratings.stream()
                .collect(groupingBy(
                        Rating::getUserId,
                        mapping(Rating::getItemId, toSet())
                ));

        // Group all ratings by which user made them
        // Example: userId 10 -> [rating1, rating2, rating3...]
        this.ratingsByUser = ratings.stream()
                .collect(groupingBy(Rating::getUserId));

    }

    /** @return top‑10 recommended items for the given user, sorted best‑first. */
    public abstract List<T> recommendTop10(int userId);

    /**
     * Gets all item IDs that a user has already rated.
     * 
     * We don't want to recommend items the user has already rated,
     * so this helper method makes it easy to filter them out.
     * 
     * @param userId the ID of the user
     * @return set of item IDs the user has rated (empty set if none)
     */
    protected Set<Integer> getRatedItemIds(int userId) {
        return ratedItemsByUser.entrySet().stream()
                .filter(e -> e.getKey() == userId)
                .flatMap(e -> e.getValue().stream())
                .collect(toSet());
    }

    /**
     * Takes a map of scores and returns the top 10 items.
     * 
     * This is the common "final step" for all recommenders:
     * <ol>
     *   <li>Sort by score (highest first)</li>
     *   <li>If tied, sort by number of ratings (most first)</li>
     *   <li>If still tied, sort by item name (alphabetical)</li>
     *   <li>Take the first 10</li>
     * </ol>
     * 
     * @param scoreByItemId map of item IDs to their scores
     * @return list of top 10 items based on scores
     */
    protected List<T> top10FromScores(Map<Integer, Double> scoreByItemId) {
        return scoreByItemId.entrySet().stream()
                .sorted(
                        Comparator.<Map.Entry<Integer, Double>>comparingDouble(Map.Entry::getValue).reversed()
                                .thenComparing(e -> countByItem.get(e.getKey()), Comparator.reverseOrder())
                                .thenComparing(e -> items.get(e.getKey()).getName())
                )
                .limit(NUM_OF_RECOMMENDATIONS)
                .map(e -> items.get(e.getKey()))
                .collect(toList());
    }

    /**
     * Gets the average rating for an item.
     * 
     * Uses the pre-computed avgByItem map for efficiency.
     * 
     * @param itemId the ID of the item
     * @return the average rating, or 0.0 if item not found
     */
    protected double getItemAvg(int itemId) {
        return avgByItem.entrySet().stream()
                .filter(e -> e.getKey() == itemId)
                .map(Map.Entry::getValue)
                .reduce(0.0, (a, b) -> b);
    }

    /**
     * Gets the number of ratings an item has received.
     * 
     * Uses the pre-computed countByItem map for efficiency.
     * 
     * @param itemId the ID of the item
     * @return the count of ratings, or 0 if item not found
     */
    protected long getItemCount(int itemId) {
        return countByItem.entrySet().stream()
                .filter(e -> e.getKey() == itemId)
                .map(Map.Entry::getValue)
                .reduce(0L, (a, b) -> b);
    }
}
