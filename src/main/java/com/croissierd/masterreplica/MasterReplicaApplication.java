package com.croissierd.masterreplica;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.init.DataSourceScriptDatabaseInitializer;
import org.springframework.boot.sql.init.DatabaseInitializationMode;
import org.springframework.boot.sql.init.DatabaseInitializationSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.annotation.Id;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SpringBootApplication
public class MasterReplicaApplication {

    public static void main(String[] args) {
        SpringApplication.run(MasterReplicaApplication.class, args);
    }

    @Bean
    CommandLineRunner commandLineRunner(PostService postService) {
        return args -> {
            postService.findAll().forEach(System.out::println);
            System.out.println(postService.save(new Post(0, "test", "", LocalDate.now(), 1, "test")));
        };
    }
}

enum DatabaseType {
    MASTER, REPLICA
}

class TransactionReadOnlyStatusRoutingDataSource extends AbstractRoutingDataSource {

    private final Logger logger = LoggerFactory.getLogger(TransactionReadOnlyStatusRoutingDataSource.class);

    @Override
    protected Object determineCurrentLookupKey() {
        if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            logger.info("determineCurrentLookupKey: read only");
            return DatabaseType.REPLICA;
        }
        logger.info("determineCurrentLookupKey: read write");
        return DatabaseType.MASTER;
    }

    public void initDataSource(DataSource masterDataSource, DataSource replicaDataSource) {
        Map<Object, Object> dataSourceMap = new HashMap<>();
        dataSourceMap.put(DatabaseType.MASTER, masterDataSource);
        dataSourceMap.put(DatabaseType.REPLICA, replicaDataSource);
        this.setTargetDataSources(dataSourceMap);
        this.setDefaultTargetDataSource(masterDataSource);
    }
}

@Service
@Transactional(readOnly = true)
class PostService {
    private final PostRepository postRepository;

    PostService(PostRepository postRepository) {this.postRepository = postRepository;}

    List<Post> findAll() {return postRepository.findAll();}

    @Transactional(readOnly = false)
    Post save(Post post) {return postRepository.save(post);}
}

record Post(@Id long id, String title, String slug, LocalDate date, int timeToRead, String tags) {
}

@Repository
interface PostRepository extends ListCrudRepository<Post, String> {}

@Configuration
@EnableJdbcRepositories
@EnableTransactionManagement()
class RoutingDataSourceConfiguration {

    @Bean
    @Primary
    DataSource dataSource() {
        TransactionReadOnlyStatusRoutingDataSource routingDataSource = new TransactionReadOnlyStatusRoutingDataSource();
        routingDataSource.initDataSource(masterDataSource(), replicaDataSource());
        routingDataSource.afterPropertiesSet();
        return new LazyConnectionDataSourceProxy(routingDataSource);
    }

    @Bean
    @ConfigurationProperties("master.datasource")
    DataSourceProperties masterDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("replica.datasource")
    DataSourceProperties replicaDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    HikariDataSource masterDataSource() {
        return masterDataSourceProperties()
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean
    HikariDataSource replicaDataSource() {
        return replicaDataSourceProperties()
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean
    DataSourceScriptDatabaseInitializer masterDatabaseInitializer() {
        var settings =new DatabaseInitializationSettings();
        settings.setSchemaLocations(List.of("classpath:schema.sql"));
        settings.setMode(DatabaseInitializationMode.EMBEDDED);
        return new DataSourceScriptDatabaseInitializer(masterDataSource(), settings);
    }
}
