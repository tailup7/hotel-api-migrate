package com.khoubyari.example.config;

import com.khoubyari.example.dao.jpa.HotelRepository;
import com.khoubyari.example.domain.Hotel;
import com.khoubyari.example.service.HotelService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration(proxyBeanMethods = false)
@EnableTransactionManagement
@EnableJpaRepositories(basePackageClasses = HotelRepository.class)
public class PersistenceConfiguration {
    @Bean(destroyMethod = "close")
    HikariDataSource dataSource(DatabaseSettings settings) {
        HikariConfig pool = new HikariConfig();
        pool.setJdbcUrl(settings.getUrl());
        pool.setUsername(settings.getUsername());
        pool.setPassword(settings.getPassword());
        pool.setDriverClassName("org.postgresql.Driver");
        pool.setMaximumPoolSize(settings.getPoolSize());
        pool.setMinimumIdle(0);
        pool.setConnectionTimeout(10000);
        pool.setValidationTimeout(3000);
        pool.setInitializationFailTimeout(10000);
        // Pool workers outlive the request/test scheduler that initializes the bean.
        // Avoid inheriting Mule request thread groups and inheritable thread-local state.
        ThreadGroup group = Thread.currentThread().getThreadGroup();
        while (group.getParent() != null) group = group.getParent();
        ThreadGroup poolGroup = group;
        AtomicInteger threadNumber = new AtomicInteger();
        pool.setThreadFactory(task -> {
            Thread thread = new Thread(poolGroup, task, "hotel-db-" + threadNumber.incrementAndGet(), 0, false);
            thread.setDaemon(true);
            thread.setContextClassLoader(PersistenceConfiguration.class.getClassLoader());
            return thread;
        });
        return new HikariDataSource(pool);
    }

    @Bean
    LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource, DatabaseSettings settings) {
        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan(Hotel.class.getPackageName());
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of(
                "hibernate.hbm2ddl.auto", settings.getSchemaMode(),
                "hibernate.jdbc.lob.non_contextual_creation", true,
                "hibernate.jdbc.time_zone", "UTC"));
        return factory;
    }

    @Bean
    PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    @Bean
    HotelService hotelService(HotelRepository repository, DataSource dataSource, DatabaseSettings settings) {
        return new HotelService(repository, dataSource, settings.getServiceName());
    }
}
