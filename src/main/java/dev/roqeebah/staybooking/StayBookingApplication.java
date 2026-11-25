package dev.roqeebah.staybooking;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StayBookingApplication {

	public static void main(String[] args) {
		SpringApplication.run(StayBookingApplication.class, args);
	}

	/** Injected so tests can control time (hold expiry). */
	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}
}
