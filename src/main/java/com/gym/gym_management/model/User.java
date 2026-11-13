package com.gym.gym_management.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;


@Entity 
@Table(name = "users")
public class User {
    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String name;
    private String email;
    private String password;
    private String phone;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Column (name = "gym_code")
    private String gymCode;

    @Column (name="created_at",insertable=false, updatable=false)
    private LocalDateTime createdAt;

    public enum Role{
        ADMIN, MEMBER
    }
    public Integer getId() {
        return id;
    }
    public void setId(Integer id) {
        this.id = id;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getEmail() {
        return email;
    }
    public void setEmail(String email) {
        this.email = email;
    }
    public String getPassword() {
        return password;
    }
    public void setPassword(String password) {
        this.password = password;
    }
    public String getPhone() {
        return phone;
    }
    public void setPhone(String phone) {
        this.phone = phone;
    }
    public Role getRole() {
        return role;
    }
    public void setRole(Role role) {
        this.role = role;
    }
    public String getGymCode() {
        return gymCode;
    }
    public void setGymCode(String gymCode) {
        this.gymCode = gymCode ;
    }
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
