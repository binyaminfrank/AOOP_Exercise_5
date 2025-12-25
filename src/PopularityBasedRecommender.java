import java.util.*;
import static java.util.stream.Collectors.*;

/** Popularity‑based recommender implementation. */
class PopularityBasedRecommender<T extends Item> extends RecommenderSystem<T> {
    /** Minimum number of ratings an item needs to be considered "popular". */
    private static final int POPULARITY_THRESHOLD = 100;
    
    public PopularityBasedRecommender(Map<Integer, User> users,
                                      Map<Integer, T> items,
                                      List<Rating<T>> ratings) {
        super(users, items, ratings);
    }

    @Override
    public List<T> recommendTop10(int userId) {

        // Get items this user already rated 
        Set<Integer> rated = getRatedItemIds(userId);

        // Build a map of itemId: average rating
        // Only include items with >= 100 ratings that user hasn't rated
        Map<Integer, Double> scores = avgByItem.entrySet().stream()
                .filter(e -> getItemCount(e.getKey()) >= POPULARITY_THRESHOLD)
                .filter(e -> !rated.contains(e.getKey()))
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));

        return top10FromScores(scores);
    }

}
