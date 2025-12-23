
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

        // convert users -> userIds (so we can filter ratings easily)
        Set<Integer> matchingUserIds = matchingUsers.stream()
                .map(User::getId)
                .collect(Collectors.toSet());

        // 1) group ratings by itemId (only from matching users)
        Map<Integer, List<Rating>> ratingsByItem = ratings.stream()
                .filter(r -> matchingUserIds.contains(r.getUserId()))
                .collect(Collectors.groupingBy(Rating::getItemId));

        // 2) keep items with >= 5 ratings, compute avg
        Map<Integer, Double> avgByItem = ratingsByItem.entrySet().stream()
                .filter(e -> e.getValue().size() >= 5)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> Double.valueOf(
                                e.getValue().stream()
                                        .mapToDouble(Rating::getRating)
                                        .average()
                                        .orElse(0.0)
                        )
                ));


        return null;
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





}




