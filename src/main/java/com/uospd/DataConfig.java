package com.uospd;

import org.hibernate.cfg.Environment;
import org.hibernate.jpa.HibernatePersistenceProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;

import javax.sql.DataSource;
import java.util.Properties;

@Configuration
public class DataConfig {
    @Value("${db.driver}")
    private String PROP_DATABASE_DRIVER;
    @Value("${db.password}")
    private String PROP_DATABASE_PASSWORD;
    @Value("${db.url}")
    private String PROP_DATABASE_URL;
    @Value("${db.url2}")
    private String PROP_DATABASE_URL2;
    @Value("${db.username}")
    private String PROP_DATABASE_USERNAME;
    @Value("${db.hibernate.dialect}")
    private String PROP_HIBERNATE_DIALECT;
    @Value("${db.hibernate.show_sql}")
    private String PROP_HIBERNATE_SHOW_SQL;
    @Value("${db.entitymanager.packages.to.scan}")
    private String PROP_ENTITYMANAGER_PACKAGES_TO_SCAN;
    @Value("${db.hibernate.hbm2ddl.auto}")
    private String PROP_HIBERNATE_HBM2DDL_AUTO;

    @Bean(name = "dataSource")
    public DataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(PROP_DATABASE_DRIVER);
        dataSource.setUrl(PROP_DATABASE_URL);
        dataSource.setUsername(PROP_DATABASE_USERNAME);
        dataSource.setPassword(PROP_DATABASE_PASSWORD);
        return dataSource;
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory() {
        LocalContainerEntityManagerFactoryBean entityManagerFactoryBean = new LocalContainerEntityManagerFactoryBean();
        entityManagerFactoryBean.setDataSource(dataSource());
        entityManagerFactoryBean.setPersistenceProviderClass(HibernatePersistenceProvider.class);
        entityManagerFactoryBean.setPackagesToScan(PROP_ENTITYMANAGER_PACKAGES_TO_SCAN);
        entityManagerFactoryBean.setJpaProperties(getHibernateProperties());
        return entityManagerFactoryBean;
    }

    @Bean
    public JpaTransactionManager transactionManager() {
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(entityManagerFactory().getObject());
        return transactionManager;
    }

//     @Bean
//    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
//        return new JdbcTemplate(dataSource);
//    }

    private Properties getHibernateProperties() {
        Properties properties = new Properties();
        properties.put(Environment.DIALECT, PROP_HIBERNATE_DIALECT);
        properties.put(Environment.SHOW_SQL, PROP_HIBERNATE_SHOW_SQL);
        properties.put(Environment.HBM2DDL_AUTO, PROP_HIBERNATE_HBM2DDL_AUTO);
        return properties;
    }

}
