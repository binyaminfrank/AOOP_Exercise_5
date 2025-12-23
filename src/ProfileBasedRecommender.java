
import java.util.*;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.*;

/** Profile‑based recommender implementation. */
class ProfileBasedRecommender<T extends Item> extends RecommenderSystem<T> {
    public ProfileBasedRecommender(Map<Integer, User> users,
                                   Map<Integer, T> items,
                                   List<Rating<T>> ratings) {
        super(users, items, ratings);
    }

    @Override
    public List<T> recommendTop10(int userId) {
        List<User> matchingUsers = getMatchingProfileUsers(userId);

        Set<Integer> ratedByUser = ratings.stream()
                .filter(r -> r.getUserId() == userId)
                .map(Rating::getItemId)
                .collect(Collectors.toSet());

        // convert users -> userIds (so we can filter ratings easily)
        Set<Integer> matchingUserIds = matchingUsers.stream()
                .map(User::getId)
                .collect(Collectors.toSet());

        Map<Integer, List<Rating<T>>> groupByItem =
                ratings.stream()
                        .filter(r -> matchingUserIds.contains(r.getUserId()))
                        .collect(Collectors.groupingBy(Rating::getItemId));


        //ItemId, Average Ratings
        Map<Integer, Double> ItemAverageRatings = groupByItem.entrySet().stream()
                .filter(i -> getItemRatingsCount(i.getKey()) >= 5)
                .filter(e -> !ratedByUser.contains(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, e -> getItemAverageRating(e.getKey())));

        return ItemAverageRatings.entrySet().stream()
                .sorted(Comparator.<Map.Entry<Integer, Double>>comparingDouble(Map.Entry::getValue).reversed()
                        .thenComparing(e -> getItemRatingsCount(e.getKey()), Comparator.reverseOrder())
                        .thenComparing(e -> items.get(e.getKey()).getName())
                )
                .limit(10)
                .map(e -> items.get(e.getKey()))
                .toList();
    }

    public List<User> getMatchingProfileUsers(int userId) {
        //
        User currentUser = users.get(Integer.valueOf(userId));
        String gender = currentUser.getGender();
        int age = currentUser.getAge();

        return  users.values().stream()
                .filter(u -> u.getGender().equals(gender)) //same gender
                .filter(u -> Math.abs(u.getAge() - age) <= 5)   // within 5 years
                .filter(u -> u.getId() != currentUser.getId()) //excludes current user
                .toList();
    }

    public double getItemAverageRating(int itemId) {
        return  ratings.stream()
                .filter(r -> r.getItemId() == itemId)
                .mapToDouble(Rating::getRating)
                .average()
                .orElse(0);
    }

    public long getItemRatingsCount(int itemId) {
        return ratings.stream()
                .filter(e -> e.getItemId() == itemId)
                .count();


    }



}




