package com.gym.gym_management.controller;

import com.gym.gym_management.model.Attendance;
import com.gym.gym_management.model.Subscription;
import com.gym.gym_management.model.User;
import com.gym.gym_management.repository.AttendanceRepository;
import com.gym.gym_management.repository.SubscriptionRepository;
import com.gym.gym_management.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/checkin")
public class CheckInController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @PostMapping
    public ResponseEntity<?> checkIn(@RequestBody Map<String, String> data) {
        String gymCode = data.get("gymCode").trim().toUpperCase();
        Optional<User> userOpt = userRepository.findByGymCode(gymCode);

        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of(
                "access", "DENIED",
                "reason", "Invalid Gym ID: " + gymCode
            ));
        }

        User user = userOpt.get();
        Optional<Subscription> subOpt = subscriptionRepository.findTopByUserIdOrderByEndDateDesc(user.getId());

        if (subOpt.isEmpty()) {
            return ResponseEntity.status(403).body(Map.of(
                "access", "DENIED",
                "memberName", user.getName(),
                "reason", "No active subscription found. Please purchase a plan."
            ));
        }

        Subscription sub = subOpt.get();
        LocalDate today = LocalDate.now();

        // Check if subscription is expired!
        if (today.isAfter(sub.getEndDate())) {
            return ResponseEntity.status(403).body(Map.of(
                "access", "DENIED",
                "memberName", user.getName(),
                "planName", sub.getPlan().getPlanName(),
                "endDate", sub.getEndDate().toString(),
                "reason", "Subscription expired on " + sub.getEndDate() + ". Please renew!"
            ));
        }

        // Valid Subscriber -> Mark Attendance!
        Attendance attendance = new Attendance();
        attendance.setUser(user);
        attendance.setCheckInDate(today);
        attendance.setCheckInTime(LocalTime.now());
        attendanceRepository.save(attendance);

        Map<String, Object> response = new HashMap<>();
        response.put("access", "GRANTED");
        response.put("memberName", user.getName());
        response.put("planName", sub.getPlan().getPlanName());
        response.put("endDate", sub.getEndDate().toString());
        response.put("checkInDate", today.toString());
        response.put("checkInTime", LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm a")));

        return ResponseEntity.ok(response);
    }
}