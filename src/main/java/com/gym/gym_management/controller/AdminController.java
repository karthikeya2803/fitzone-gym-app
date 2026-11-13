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
import java.util.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    // 1. Get all members + their latest subscription details
    @GetMapping("/members")
    public List<Map<String,Object>> getAllMembers() {
        List<User> users = userRepository.findAll();
        List<Map<String, Object>> result = new ArrayList<>();

        for (User u : users) {
            if (u.getRole() == User.Role.MEMBER) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", u.getId());
                map.put("name", u.getName());
                map.put("email", u.getEmail());
                map.put("phone", u.getPhone());
                map.put("gymCode", u.getGymCode());

                Optional<Subscription> subOpt = subscriptionRepository.findTopByUserIdOrderByEndDateDesc(u.getId());
                if (subOpt.isPresent()) {
                    Subscription sub = subOpt.get();
                    map.put("planName", sub.getPlan().getPlanName());
                    map.put("endDate", sub.getEndDate().toString());
                    map.put("status", sub.getEndDate().isBefore(LocalDate.now()) ? "EXPIRED" : "ACTIVE");
                } else {
                    map.put("planName", "No Plan");
                    map.put("endDate", "-");
                    map.put("status", "INACTIVE");
                }
                result.add(map);
            }
        }
        return result;
    }

    // 2. Get all plans (for the dropdown menu)
    @GetMapping("/plans")
    public List<Plan> getAllPlans() {
        return planRepository.findAll();
    }

    // 3. Add a new gym member
    @PostMapping("/members")
    public ResponseEntity<?> addMember(@RequestBody Map<String, String> data) {
        if (userRepository.findByEmail(data.get("email")).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email already exists!"));
        }

        User user = new User();
        user.setName(data.get("name"));
        user.setEmail(data.get("email"));
        user.setPassword(data.get("password"));
        user.setPhone(data.get("phone"));
        user.setRole(User.Role.MEMBER);
        // Generate a unique Gym ID like GYM4821
        user.setGymCode("GYM" + (1000 + new Random().nextInt(9000)));

        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "Member added!", "gymCode", user.getGymCode()));
    }

    // 4. Delete a member
    @DeleteMapping("/members/{id}")
    public ResponseEntity<?> deleteMember(@PathVariable Integer id) {
        userRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Member deleted"));
    }

    // 5. Assign or Renew a subscription (for offline/cash payments)
    @PostMapping("/subscribe")
    public ResponseEntity<?> assignSubscription(@RequestBody Map<String, Integer> data) {
        Integer userId = data.get("userId");
        Integer planId = data.get("planId");

        User user = userRepository.findById(userId).orElseThrow();
        Plan plan = planRepository.findById(planId).orElseThrow();

        Subscription sub = new Subscription();
        sub.setUser(user);
        sub.setPlan(plan);
        sub.setStartDate(LocalDate.now());
        sub.setEndDate(LocalDate.now().plusDays(plan.getDurationDays()));
        sub.setPaymentMethod(Subscription.PaymentMethod.CASH_BY_ADMIN);
        sub.setStatus(Subscription.Status.ACTIVE);

        subscriptionRepository.save(sub);
        return ResponseEntity.ok(Map.of("message", "Plan assigned until " + sub.getEndDate()));
    }
}