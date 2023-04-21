package be.sandervl.jiraharvest;

import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.shell.boot.TerminalCustomizer;
import org.springframework.shell.jline.PromptProvider;

@SpringBootApplication
public class JiraHarvestApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(JiraHarvestApplication.class);
        application.setBannerMode(Banner.Mode.OFF);
        application.run(args);
    }

    @Bean
    public TerminalCustomizer terminalCustomizer() {
        return terminalBuilder -> terminalBuilder.system(true);
    }

    @Bean
    public PromptProvider myPromptProvider() {
        return () -> new AttributedString("jira-harvest:>", AttributedStyle.DEFAULT.foreground(AttributedStyle.BLUE));
    }
}
