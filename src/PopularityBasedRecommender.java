import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.*;

/** Popularity‑based recommender implementation. */
class PopularityBasedRecommender<T extends Item> extends RecommenderSystem<T> {
    private static final int POPULARITY_THRESHOLD = 100;
    public PopularityBasedRecommender(Map<Integer, User> users,
                                      Map<Integer, T> items,
                                      List<Rating<T>> ratings) {
        super(users, items, ratings);
    }

    @Override
    public List<T> recommendTop10(int userId) {
        // TODO: implement
        Set<Integer> ratedByUser = ratings.stream()
                .filter(r -> r.getUserId() == userId)
                .map(Rating::getItemId)
                .collect(Collectors.toSet());

        Map<Integer, List<Rating<T>>> groupByItem = ratings.stream().collect(groupingBy(Rating::getItemId));

        Map<Integer, Double> averages = groupByItem.entrySet()
                .stream()
                .filter(e -> getItemRatingsCount(e.getKey()) >= 100)
                .filter(e -> !ratedByUser.contains(e.getKey()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> getItemAverageRating(e.getKey())));

        List<T> top10 = averages.entrySet().stream()
                .sorted(Comparator.<Map.Entry<Integer, Double>>comparingDouble(Map.Entry::getValue).reversed()
                        .thenComparing(e -> getItemRatingsCount(e.getKey()), Comparator.reverseOrder())
                        .thenComparing(e -> items.get(e.getKey()).getName())
                )
                .limit(10)
                .map(e -> items.get(e.getKey()))
                .toList();
        return top10;
    }

    public double getItemAverageRating(int itemId) {
        double average = ratings.stream()
                .filter(r -> r.getItemId() == itemId)
                .mapToDouble(Rating::getRating)
                .average()
                .orElse(0);
        return average;
    }
    public int getItemRatingsCount(int itemId) {
        long count = ratings.stream()
                .filter(e -> e.getItemId() == itemId)
                .count();

        return (int)count;
    }

}
