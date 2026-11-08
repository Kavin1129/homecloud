package com.homecloud;

import com.homecloud.config.StorageProperties;
import com.homecloud.crypto.EncryptionProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(
        exclude = UserDetailsServiceAutoConfiguration.class
)
@EnableConfigurationProperties({
        StorageProperties.class,
        EncryptionProperties.class
})
public class HomecloudApplication {

    public static void main(String[] args) {
        SpringApplication.run(HomecloudApplication.class, args);
    }

}
