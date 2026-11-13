package com.gym.gym_management.repository;

import com.gym.gym_management.model.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Integer> {
    Optional<Subscription> findTopByUserIdOrderByEndDateDesc(Integer userId);
}