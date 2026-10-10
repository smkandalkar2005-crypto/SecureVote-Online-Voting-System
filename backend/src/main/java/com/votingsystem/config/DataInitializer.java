package com.votingsystem.config;

import com.votingsystem.entity.Candidate;
import com.votingsystem.entity.Election;
import com.votingsystem.entity.User;
import com.votingsystem.enums.ElectionStatus;
import com.votingsystem.enums.Role;
import com.votingsystem.repository.CandidateRepository;
import com.votingsystem.repository.ElectionRepository;
import com.votingsystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Configuration
@RequiredArgsConstructor
public class DataInitializer {

    private final UserRepository userRepository;
    private final ElectionRepository electionRepository;
    private final CandidateRepository candidateRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.default-password:${ADMIN_DEFAULT_PASSWORD:}}")
    private String adminDefaultPassword;

    @Bean
    @SuppressWarnings("null")
    public CommandLineRunner initData() {
        return args -> {
            Optional<User> existingAdmin = userRepository.findByEmail("admin@securevote.com");
            User admin = existingAdmin.orElse(null);

            // 1. Create default admin ONLY if explicitly configured via environment variable and not existing
            if (admin == null) {
                if (adminDefaultPassword != null && !adminDefaultPassword.trim().isEmpty()) {
                    String trimmedPassword = adminDefaultPassword.trim();
                    if (trimmedPassword.length() < 8) {
                        System.out.println(">>> WARNING: ADMIN_DEFAULT_PASSWORD must be at least 8 characters. Default admin creation skipped. <<<");
                    } else {
                        admin = User.builder()
                                .fullName("System Administrator")
                                .email("admin@securevote.com")
                                .registerNumber("ADMIN001")
                                .password(passwordEncoder.encode(trimmedPassword))
                                .role(Role.ADMIN)
                                .enabled(true)
                                .build();
                        admin = userRepository.save(admin);
                        System.out.println(">>> Default administrator 'admin@securevote.com' initialized from environment configuration <<<");
                    }
                } else {
                    System.out.println(">>> Notice: Default administrator 'admin@securevote.com' not seeded (ADMIN_DEFAULT_PASSWORD is not set) <<<");
                }
            }

            // 2. Create sample University Election if elections are empty
            if (electionRepository.findAll().isEmpty()) {
                // Determine creator: use configured/existing admin, or any existing admin, or a disabled system account
                User electionCreator = admin;
                if (electionCreator == null) {
                    electionCreator = userRepository.findAll().stream()
                            .filter(u -> u.getRole() == Role.ADMIN)
                            .findFirst()
                            .orElse(null);
                }

                if (electionCreator == null) {
                    User systemOrganizer = User.builder()
                            .fullName("System Organizer")
                            .email("system@securevote.local")
                            .registerNumber("SYS001")
                            .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                            .role(Role.ADMIN)
                            .enabled(false) // Disabled account; cannot be used to authenticate
                            .build();
                    electionCreator = userRepository.save(systemOrganizer);
                }

                Election election = Election.builder()
                        .title("Student Council 2026")
                        .description("Annual election for the University Student Council President and Vice President roles.")
                        .startDate(LocalDateTime.now())
                        .endDate(LocalDateTime.now().plusDays(7))
                        .status(ElectionStatus.ACTIVE)
                        .createdBy(electionCreator)
                        .build();

                Election savedElection = electionRepository.save(election);

                Candidate c1 = Candidate.builder()
                        .name("Arjun Sharma")
                        .party("Tech Forward")
                        .description("Engineering major focusing on lab accessibility and digital infrastructure.")
                        .election(savedElection)
                        .build();

                Candidate c2 = Candidate.builder()
                        .name("Priya Patel")
                        .party("Unity & Growth")
                        .description("Political Science major advocating for campus safety and diverse cultural festivals.")
                        .election(savedElection)
                        .build();

                Candidate c3 = Candidate.builder()
                        .name("Sarah Jenkins")
                        .party("Green Campus")
                        .description("Environmental Science major pushing for zero-waste initiatives.")
                        .election(savedElection)
                        .build();

                candidateRepository.saveAll(List.of(c1, c2, c3));
                System.out.println(">>> Sample data initialized: Student Council 2026 <<<");
            }
        };
    }
}
