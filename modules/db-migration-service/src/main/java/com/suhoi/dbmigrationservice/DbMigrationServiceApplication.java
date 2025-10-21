package com.suhoi.dbmigrationservice;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class DbMigrationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DbMigrationServiceApplication.class, args);
    }

    @Bean
    ApplicationRunner exitAfterLiquibase(@Value("${migration.exit-after-run:true}") boolean exit) {
        return args -> {
            if (exit) {
                // Если Liquibase прошёл успешно — контекст поднялся → выходим c 0
                System.out.println("Liquibase migrations applied. Exiting as requested.");
                System.exit(0);
            }
        };
    }

}
