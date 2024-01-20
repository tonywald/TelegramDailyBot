package wtf.bandit.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@ComponentScan(basePackages = {
        "wtf.bandit.admin",
        "org.telegram.telegrambots"
})
public class TelegramDailyBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(TelegramDailyBotApplication.class, args);
    }

}
