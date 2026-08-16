package com.dnsfilt.dnsadmin.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    public UserEntity() {
        this.email = "user@dnsfilt.internal";
        this.role = Role.ROLE_VIEWER;
    }

    public UserEntity(String username, String password, String email, Role role) {
        this.username = username;
        this.password = password;
        this.email = (email != null && !email.trim().isEmpty()) ? email : username + "@dnsfilt.internal";
        this.role = (role != null) ? role : Role.ROLE_VIEWER;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
}
