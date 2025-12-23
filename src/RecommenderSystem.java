import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.*;

/** Abstract generic recommender system. */
abstract class RecommenderSystem<T extends Item> {
    protected final Map<Integer, User> users;
    protected final Map<Integer, T> items;
    protected final List<Rating<T>> ratings;
    // TODO: add data structures to make the operation more efficient / simpler

    protected final Map<Integer, List<Rating<T>>> ratingsByItem;
    protected final Map<Integer, Long> countByItem;
    protected final Map<Integer, Double> avgByItem;
    protected final Map<Integer, Set<Integer>> ratedItemsByUser;

    protected final int NUM_OF_RECOMMENDATIONS = 10;

    protected RecommenderSystem(Map<Integer, User> users,
                                Map<Integer, T> items,
                                List<Rating<T>> ratings) {
        this.users = users;
        this.items = items;
        this.ratings = ratings;
        // TODO: initialize additional data structures

        // Ratings per Item
        this.ratingsByItem = ratings.stream()
                .collect(groupingBy(Rating::getItemId));

        // Count amount of ratings per Item
        this.countByItem = ratingsByItem.entrySet().stream()
                .collect(toMap(
                        Map.Entry::getKey,
                        e -> (long) e.getValue().size()
                ));

        // Average rating per Item
        this.avgByItem = ratingsByItem.entrySet().stream()
                .collect(toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().stream()
                                .mapToDouble(Rating::getRating)
                                .average()
                                .orElse(0.0)
                ));

        // Items rated by each user
        this.ratedItemsByUser = ratings.stream()
                .collect(groupingBy(
                        Rating::getUserId,
                        mapping(Rating::getItemId, toSet())
                ));

    }

    /** @return top‑10 recommended items for the given user, sorted best‑first. */
    public abstract List<T> recommendTop10(int userId);
}
