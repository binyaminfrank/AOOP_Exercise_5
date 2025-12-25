
import java.util.*;
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

        // Get items this user already rated
        Set<Integer> rated = getRatedItemIds(userId);

        // Find users with matching profile (same gender, similar age)
        Set<Integer> matchingUserIds = getMatchingProfileUsers(userId).stream()
                .map(User::getId)
                .collect(toSet());

        // Group ratings by item (only from matching users)
        Map<Integer, List<Rating<T>>> itemRatings =
                ratings.stream()
                        .filter(r -> matchingUserIds.contains(r.getUserId()))
                        .collect(groupingBy(Rating::getItemId));

        // Calculate average rating for each item
        Map<Integer, Double> scores = itemRatings.entrySet().stream()
                .filter(e -> e.getValue().size() >= 5)
                .filter(e -> !rated.contains(e.getKey()))
                .collect(toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().stream()
                                .mapToDouble(Rating::getRating)
                                .average()
                                .orElse(0.0)
                ));

        // Sort and return top 10
        return scores.entrySet().stream()
                .sorted(
                        Comparator.<Map.Entry<Integer, Double>>comparingDouble(Map.Entry::getValue).reversed()
                                .thenComparing(e -> itemRatings.get(e.getKey()).size(), Comparator.reverseOrder())
                                .thenComparing(e -> items.get(e.getKey()).getName())
                )
                .limit(NUM_OF_RECOMMENDATIONS)
                .map(e -> items.get(e.getKey()))
                .collect(toList());
    }

    /**
     * Finds users with a matching profile.
     * 
     * <p>A user matches if they have:
     * <ul>
     *   <li>Same gender</li>
     *   <li>Age within 5 years (older or younger)</li>
     *   <li>Different user ID (exclude the user themselves)</li>
     * </ul>
     * 
     * @param userId the ID of the user to find matches for
     * @return list of users with matching profiles
     */
    public List<User> getMatchingProfileUsers(int userId) {
        User currentUser = users.get(userId);
        String gender = currentUser.getGender();
        int age = currentUser.getAge();

        return  users.values().stream()
                .filter(u -> u.getGender().equals(gender)) //same gender
                .filter(u -> Math.abs(u.getAge() - age) <= 5)   // within 5 years
                .filter(u -> u.getId() != currentUser.getId()) //excludes current user
                .collect(toList());
    }

}
