package com.contentplatform.backend.config

import com.contentplatform.backend.database.repository.DirectConversationJdbcRepository
import com.contentplatform.backend.database.repository.DirectMessageJdbcRepository
import com.contentplatform.backend.database.repository.AuthorJdbcRepository
import com.contentplatform.backend.database.repository.CommentJdbcRepository
import com.contentplatform.backend.database.repository.CommentLikeJdbcRepository
import com.contentplatform.backend.database.repository.ContentJdbcRepository
import com.contentplatform.backend.database.repository.ContentTagJdbcRepository
import com.contentplatform.backend.database.repository.InstanceJdbcRepository
import com.contentplatform.backend.database.repository.InteractionJdbcRepository
import com.contentplatform.backend.database.repository.FollowJdbcRepository
import com.contentplatform.backend.database.repository.MediaJdbcRepository
import com.contentplatform.backend.database.repository.SourceJdbcRepository
import com.contentplatform.backend.database.repository.TagJdbcRepository
import com.contentplatform.backend.database.repository.TopicJdbcRepository
import com.contentplatform.backend.database.repository.ForumJdbcRepository
import com.contentplatform.backend.database.repository.CircleJdbcRepository
import com.contentplatform.backend.database.repository.QaJdbcRepository
import com.contentplatform.backend.database.repository.ColumnJdbcRepository
import com.contentplatform.backend.database.repository.CollectionJdbcRepository
import com.contentplatform.backend.database.repository.EventJdbcRepository
import com.contentplatform.backend.database.repository.NavJdbcRepository
import com.contentplatform.backend.database.repository.FlashJdbcRepository
import com.contentplatform.backend.database.repository.NotificationJdbcRepository
import com.contentplatform.backend.database.repository.NoticeJdbcRepository
import com.contentplatform.backend.repository.ForumRepository
import com.contentplatform.backend.repository.CircleRepository
import com.contentplatform.backend.repository.QaRepository
import com.contentplatform.backend.repository.ColumnRepository
import com.contentplatform.backend.repository.CollectionRepository
import com.contentplatform.backend.repository.EventRepository
import com.contentplatform.backend.repository.NavRepository
import com.contentplatform.backend.repository.FlashRepository
import com.contentplatform.backend.repository.NotificationRepository
import com.contentplatform.backend.repository.NoticeRepository
import com.contentplatform.backend.database.repository.UserJdbcRepository
import com.contentplatform.backend.database.repository.UserCredentialJdbcRepository
import com.contentplatform.backend.repository.DirectConversationRepository
import com.contentplatform.backend.repository.DirectMessageRepository
import com.contentplatform.backend.repository.AuthorRepository
import com.contentplatform.backend.repository.CommentRepository
import com.contentplatform.backend.repository.CommentLikeRepository
import com.contentplatform.backend.repository.ContentRepository
import com.contentplatform.backend.repository.ContentTagRepository
import com.contentplatform.backend.repository.InstanceRepository
import com.contentplatform.backend.repository.InteractionRepository
import com.contentplatform.backend.repository.FollowRepository
import com.contentplatform.backend.repository.MediaRepository
import com.contentplatform.backend.repository.PollRepository
import com.contentplatform.backend.database.repository.PollJdbcRepository
import com.contentplatform.backend.repository.SourceRepository
import com.contentplatform.backend.repository.TagRepository
import com.contentplatform.backend.repository.TopicRepository
import com.contentplatform.backend.repository.UserRepository
import com.contentplatform.backend.repository.UserCredentialRepository
import org.flywaydb.core.Flyway
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import javax.sql.DataSource

/**
 * Conditional persistence wiring.
 *
 * Active only when `spring.datasource.url` is configured. When no datasource is
 * configured the application context starts without any database beans, so the
 * app (and its tests) can run without a database.
 *
 * Flyway runs migrations against the configured datasource at startup.
 * Transaction management infrastructure is provided here; business transaction
 * boundaries belong to Application use cases (not yet implemented).
 */
@Configuration
@ConditionalOnProperty(name = ["spring.datasource.url"])
class DatabaseConfig(
    private val environment: Environment
) {
    @Bean
    fun dataSource(): DataSource {
        val url = requireNotNull(environment.getProperty("spring.datasource.url")) {
            "spring.datasource.url must be set"
        }
        val username = environment.getProperty("spring.datasource.username", "")
        val password = environment.getProperty("spring.datasource.password", "")
        return DriverManagerDataSource().apply {
            setDriverClassName("org.postgresql.Driver")
            this.url = url
            this.username = username
            this.password = password
        }
    }

    @Bean
    fun jdbcTemplate(dataSource: DataSource): JdbcTemplate = JdbcTemplate(dataSource)

    @Bean
    fun transactionManager(dataSource: DataSource): DataSourceTransactionManager =
        DataSourceTransactionManager(dataSource)

    @Bean
    fun transactionTemplate(txManager: PlatformTransactionManager): TransactionTemplate =
        TransactionTemplate(txManager)

    @Bean
    fun flyway(dataSource: DataSource): Flyway {
        val flyway = Flyway.configure()
            .dataSource(dataSource)
            .load()
        flyway.migrate()
        return flyway
    }

    // Repository implementations -------------------------------------------------

    @Bean
    fun directConversationRepository(jdbcTemplate: JdbcTemplate): DirectConversationRepository = DirectConversationJdbcRepository(jdbcTemplate)

    @Bean
    fun directMessageRepository(jdbcTemplate: JdbcTemplate): DirectMessageRepository = DirectMessageJdbcRepository(jdbcTemplate)

    @Bean
    fun sourceRepository(jdbcTemplate: JdbcTemplate): SourceRepository =
        SourceJdbcRepository(jdbcTemplate)

    @Bean
    fun instanceRepository(jdbcTemplate: JdbcTemplate): InstanceRepository =
        InstanceJdbcRepository(jdbcTemplate)

    @Bean
    fun authorRepository(jdbcTemplate: JdbcTemplate): AuthorRepository =
        AuthorJdbcRepository(jdbcTemplate)

    @Bean
    fun contentRepository(jdbcTemplate: JdbcTemplate): ContentRepository =
        ContentJdbcRepository(jdbcTemplate)

    @Bean
    fun followRepository(jdbcTemplate: JdbcTemplate): FollowRepository =
        FollowJdbcRepository(jdbcTemplate)

    @Bean
    fun mediaRepository(jdbcTemplate: JdbcTemplate): MediaRepository =
        MediaJdbcRepository(jdbcTemplate)

    @Bean
    fun pollRepository(jdbcTemplate: JdbcTemplate): PollRepository = PollJdbcRepository(jdbcTemplate)

    @Bean
    fun tagRepository(jdbcTemplate: JdbcTemplate): TagRepository =
        TagJdbcRepository(jdbcTemplate)

    @Bean
    fun topicRepository(jdbcTemplate: JdbcTemplate): TopicRepository =
        TopicJdbcRepository(jdbcTemplate)

    @Bean
    fun forumRepository(jdbcTemplate: JdbcTemplate): ForumRepository =
        ForumJdbcRepository(jdbcTemplate)

    @Bean
    fun circleRepository(jdbcTemplate: JdbcTemplate): CircleRepository =
        CircleJdbcRepository(jdbcTemplate)

    @Bean
    fun qaRepository(jdbcTemplate: JdbcTemplate): QaRepository = QaJdbcRepository(jdbcTemplate)

    @Bean
    fun columnRepository(jdbcTemplate: JdbcTemplate): ColumnRepository = ColumnJdbcRepository(jdbcTemplate)

    @Bean
    fun collectionRepository(jdbcTemplate: JdbcTemplate): CollectionRepository = CollectionJdbcRepository(jdbcTemplate)

    @Bean
    fun eventRepository(jdbcTemplate: JdbcTemplate): EventRepository = EventJdbcRepository(jdbcTemplate)

    @Bean
    fun navRepository(jdbcTemplate: JdbcTemplate): NavRepository = NavJdbcRepository(jdbcTemplate)

    @Bean
    fun flashRepository(jdbcTemplate: JdbcTemplate): FlashRepository = FlashJdbcRepository(jdbcTemplate)

    @Bean
    fun notificationRepository(jdbcTemplate: JdbcTemplate): NotificationRepository = NotificationJdbcRepository(jdbcTemplate)

    @Bean
    fun noticeRepository(jdbcTemplate: JdbcTemplate): NoticeRepository = NoticeJdbcRepository(jdbcTemplate)

    @Bean
    fun contentTagRepository(jdbcTemplate: JdbcTemplate): ContentTagRepository =
        ContentTagJdbcRepository(jdbcTemplate)

    @Bean
    fun userRepository(jdbcTemplate: JdbcTemplate): UserRepository =
        UserJdbcRepository(jdbcTemplate)

    @Bean
    fun userCredentialRepository(jdbcTemplate: JdbcTemplate): UserCredentialRepository =
        UserCredentialJdbcRepository(jdbcTemplate)

    @Bean
    fun commentRepository(jdbcTemplate: JdbcTemplate): CommentRepository =
        CommentJdbcRepository(jdbcTemplate)

    @Bean
    fun commentLikeRepository(jdbcTemplate: JdbcTemplate): CommentLikeRepository =
        CommentLikeJdbcRepository(jdbcTemplate)

    @Bean
    fun interactionRepository(jdbcTemplate: JdbcTemplate): InteractionRepository =
        InteractionJdbcRepository(jdbcTemplate)
}
