package com.venue

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.postgresql.PostgreSQLContainer

/** A real Postgres for tests, started once and shared. Never mock the database for reservation logic. */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	fun postgres(): PostgreSQLContainer = PostgreSQLContainer("postgres:17-alpine")
}
