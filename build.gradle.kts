plugins {
	java
//	id("org.springframework.boot") version "3.2.4" // flyway throws errors when connecting to testcontainer
	id("org.springframework.boot") version "2.7.18" // works
	id("io.spring.dependency-management") version "1.1.4"
}

group = "com.thoughtworks.techops.platforms.poc"
version = "0.0.1-SNAPSHOT"

java {
	sourceCompatibility = JavaVersion.VERSION_17
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
	implementation("org.springframework.boot:spring-boot-starter-webflux")
	implementation("io.projectreactor:reactor-core")
	implementation("org.flywaydb:flyway-core")

	runtimeOnly("org.postgresql:postgresql")
	runtimeOnly("org.postgresql:r2dbc-postgresql")
	runtimeOnly("org.springframework:spring-jdbc") // required for flyway jdbc connection

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("io.projectreactor:reactor-test")
	testImplementation("org.testcontainers:junit-jupiter")
	testImplementation("org.testcontainers:postgresql")
	testImplementation("org.testcontainers:r2dbc")
}

dependencyManagement {
	overriddenByDependencies(false)
	imports {
		mavenBom("org.testcontainers:testcontainers-bom:1.19.6")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}