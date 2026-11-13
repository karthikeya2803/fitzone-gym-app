package com.gym.gym_management.controller;

import com.gym.gym_management.model.User;
import com.gym.gym_management.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Value("${brevo.sender.email}")
    private String senderEmail;

    @Value("${BREVO_API_KEY:}")
    private String brevoApiKey;

    private final Map<String, String> otpStorage = new ConcurrentHashMap<>();

    // 1. Login with Email OR Mobile Number
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> loginData) {
        String identifier = loginData.get("email").trim();
        String password = loginData.get("password");

        Optional<User> userOpt = identifier.contains("@")
                ? userRepository.findByEmail(identifier)
                : userRepository.findByPhone(identifier);

        if (userOpt.isPresent() && userOpt.get().getPassword().equals(password)) {
            User user = userOpt.get();
            Map<String, Object> response = new HashMap<>();
            response.put("id", user.getId());
            response.put("name", user.getName());
            response.put("role", user.getRole().name());
            response.put("gymCode", user.getGymCode());
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
    }

    // 2. Send 6-Digit OTP via Brevo HTTPS API (or fallback for Mobile)
    @PostMapping("/send-otp")
    public ResponseEntity<?> sendOtp(@RequestBody Map<String, String> data) {
        String contact = data.get("contact").trim();
        if (contact.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Please enter an email or mobile number"));
        }

        boolean isEmail = contact.contains("@");

        if (isEmail && userRepository.findByEmail(contact).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "An account with this email already exists!"));
        }
        if (!isEmail && userRepository.findByPhone(contact).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "An account with this mobile number already exists!"));
        }

        String otp = String.valueOf(100000 + new Random().nextInt(900000));
        otpStorage.put(contact, otp);
        System.out.println("OTP FOR " + contact + " IS: " + otp);

        if (isEmail && brevoApiKey != null && !brevoApiKey.isEmpty()) {
            try {
                String jsonPayload = String.format(
                    "{\"sender\":{\"name\":\"FitZone Gym\",\"email\":\"%s\"}," +
                    "\"to\":[{\"email\":\"%s\"}]," +
                    "\"subject\":\"FitZone Gym - Your Sign Up OTP\"," +
                    "\"textContent\":\"Welcome to FitZone Gym! Your verification OTP is: %s\"}",
                    senderEmail.trim(), contact, otp
                );

                HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.brevo.com/v3/smtp/email"))
                    .timeout(Duration.ofSeconds(10))
                    .header("accept", "application/json")
                    .header("api-key", brevoApiKey.trim())
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return ResponseEntity.ok(Map.of("message", "OTP sent to your email inbox!"));
                } else {
                    System.out.println("Brevo API error (" + response.statusCode() + "): " + response.body());
                }
            } catch (Exception e) {
                System.out.println("Brevo API exception: " + e.getMessage());
            }
        }

        return ResponseEntity.ok(Map.of("message", "OTP generated! (Demo/SMS Fallback OTP: " + otp + ")"));
    }

    // 3. Verify OTP and Create Account
    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody Map<String, String> data) {
        String name = data.get("name").trim();
        String contact = data.get("contact").trim();
        String password = data.get("password");
        String enteredOtp = data.get("otp").trim();

        String validOtp = otpStorage.get(contact);
        if (validOtp == null || !validOtp.equals(enteredOtp)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid or expired OTP!"));
        }

        otpStorage.remove(contact);

        User user = new User();
        user.setName(name);
        user.setPassword(password);
        user.setRole(User.Role.MEMBER);
        user.setGymCode("GYM" + (1000 + new Random().nextInt(9000)));

        if (contact.contains("@")) {
            user.setEmail(contact);
        } else {
            user.setPhone(contact);
        }

        userRepository.save(user);
        return ResponseEntity.ok(Map.of(
                "message", "Account created successfully! Your Gym ID is " + user.getGymCode(),
                "gymCode", user.getGymCode()
        ));
    }
}