package dev.roqeebah.staybooking;

import org.springframework.boot.SpringApplication;

public class TestStayBookingApplication {

	public static void main(String[] args) {
		SpringApplication.from(StayBookingApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
