plugins {
	kotlin("jvm") version "2.3.21"
	kotlin("plugin.spring") version "2.3.21"
	id("org.springframework.boot") version "4.1.1"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.contentplatform"
version = "0.0.1-SNAPSHOT"
description = "Content Discovery and Creator Platform"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
        implementation("org.springframework.boot:spring-boot-starter-security")
        // Session persistence via JDBC (PostgreSQL) — no Redis.
        // Spring Boot 4 moved session auto-configuration out of spring-boot-autoconfigure
        // into dedicated modules: `spring-boot-session-jdbc` carries the JDBC store
        // auto-configuration (it transitively brings spring-boot-session and
        // spring-session-jdbc). Without it the `spring.session.*` properties are ignored
        // and sessions silently fall back to the container's in-memory store.
        implementation("org.springframework.boot:spring-boot-session-jdbc")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("tools.jackson.module:jackson-module-kotlin")
	// Persistence: plain Spring JDBC + Flyway + PostgreSQL driver.
	// Intentionally NOT using spring-boot-starter-jdbc / JPA / Hibernate / Spring Data:
	// database autoconfiguration stays out of the classpath so the app context can
	// start without a database; real wiring is conditional in DatabaseConfig.
	implementation("org.springframework:spring-jdbc")
	implementation("org.springframework:spring-tx")
	implementation("org.flywaydb:flyway-core")
	runtimeOnly("org.flywaydb:flyway-database-postgresql")
	runtimeOnly("org.postgresql:postgresql")
	// Session persistence via JDBC (PostgreSQL) — no Redis.
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}
