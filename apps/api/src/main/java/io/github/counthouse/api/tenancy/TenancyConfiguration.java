package io.github.counthouse.api.tenancy;

import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.transaction.autoconfigure.TransactionManagerCustomizers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class TenancyConfiguration {

    // Replaces Boot's JpaTransactionManager, so Boot's customizers (spring.transaction.* properties) are applied here.
    @Bean
    TenantTransactionManager transactionManager(
            EntityManagerFactory entityManagerFactory, ObjectProvider<TransactionManagerCustomizers> customizers) {
        var transactionManager = new TenantTransactionManager(entityManagerFactory);
        customizers.ifAvailable(c -> c.customize(transactionManager));
        return transactionManager;
    }

    @Bean
    static BeanPostProcessor tenantResettingDataSource() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                return bean instanceof DataSource pool && !(bean instanceof TenantResettingDataSource)
                        ? new TenantResettingDataSource(pool)
                        : bean;
            }
        };
    }
}
