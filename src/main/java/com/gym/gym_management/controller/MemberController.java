package com.gym.gym_management.controller;

import com.gym.gym_management.model.Plan;
import com.gym.gym_management.model.Subscription;
import com.gym.gym_management.model.User;
import com.gym.gym_management.repository.PlanRepository;
import com.gym.gym_management.repository.SubscriptionRepository;
import com.gym.gym_management.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@RestController
@RequestMapping("/api/member")
public class MemberController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    // 1. Get Member's current subscription + 10-day reminder check
    @GetMapping("/{userId}/status")
    public ResponseEntity<?> getMemberStatus(@PathVariable Integer userId) {
        Optional<Subscription> subOpt = subscriptionRepository.findTopByUserIdOrderByEndDateDesc(userId);
        Map<String, Object> response = new HashMap<>();

        if (subOpt.isPresent()) {
            Subscription sub = subOpt.get();
            LocalDate today = LocalDate.now();
            LocalDate endDate = sub.getEndDate();
            long daysLeft = ChronoUnit.DAYS.between(today, endDate);

            response.put("hasPlan", true);
            response.put("planName", sub.getPlan().getPlanName());
            response.put("endDate", endDate.toString());
            response.put("daysLeft", daysLeft);

            if (daysLeft < 0) {
                response.put("status", "EXPIRED");
                response.put("showReminder", true);
                response.put("reminderMessage", "Your subscription expired on " + endDate + ". Gym access is blocked until you renew!");
            } else if (daysLeft <= 10) {
                response.put("status", "ACTIVE");
                response.put("showReminder", true);
                response.put("reminderMessage", "Heads up! Your subscription expires in " + daysLeft + " day(s) on " + endDate + ". Renew now to avoid interruption!");
            } else {
                response.put("status", "ACTIVE");
                response.put("showReminder", false);
            }
        } else {
            response.put("hasPlan", false);
            response.put("status", "NO_PLAN");
            response.put("showReminder", true);
            response.put("reminderMessage", "You don't have an active plan yet. Choose a plan below to get started!");
        }

        return ResponseEntity.ok(response);
    }

    // 2. Get all available plans
    @GetMapping("/plans")
    public List<Plan> getPlans() {
        return planRepository.findAll();
    }

    // 3. Member subscribes to a plan online with chosen Payment Mode
    @PostMapping("/subscribe")
    public ResponseEntity<?> subscribeOnline(@RequestBody Map<String, Object> data) {
        Integer userId = Integer.parseInt(data.get("userId").toString());
        Integer planId = Integer.parseInt(data.get("planId").toString());
        String modeStr = data.getOrDefault("paymentMethod", "ONLINE").toString().toUpperCase();

        User user = userRepository.findById(userId).orElseThrow();
        Plan plan = planRepository.findById(planId).orElseThrow();

        LocalDate startDate = LocalDate.now();
        Optional<Subscription> existingSub = subscriptionRepository.findTopByUserIdOrderByEndDateDesc(userId);
        if (existingSub.isPresent() && existingSub.get().getEndDate().isAfter(LocalDate.now())) {
            startDate = existingSub.get().getEndDate();
        }

        String txnId = "TXN" + (100000 + new Random().nextInt(900000));

        Subscription sub = new Subscription();
        sub.setUser(user);
        sub.setPlan(plan);
        sub.setStartDate(startDate);
        sub.setEndDate(startDate.plusDays(plan.getDurationDays()));
        sub.setPaymentMethod(Subscription.PaymentMethod.valueOf(modeStr));
        sub.setTransactionId(txnId);
        sub.setStatus(Subscription.Status.ACTIVE);

        subscriptionRepository.save(sub);
        return ResponseEntity.ok(Map.of(
                "message", "Payment Successful (" + modeStr + ")! Subscribed to " + plan.getPlanName() + " until " + sub.getEndDate(),
                "transactionId", txnId
        ));
    }
}