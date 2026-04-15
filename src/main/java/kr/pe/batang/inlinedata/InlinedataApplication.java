package kr.pe.batang.inlinedata;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class InlinedataApplication {

	public static void main(String[] args) {
		SpringApplication.run(InlinedataApplication.class, args);
	}

}
