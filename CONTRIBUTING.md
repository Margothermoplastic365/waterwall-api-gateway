# Contributing to Waterwall API Gateway

Thank you for your interest in contributing to Waterwall. This guide will help you get started.

## Getting Started

1. Fork the repository and clone it locally.
2. Create a feature branch from `main` for your work.
3. Make your changes, write tests, and verify everything passes before submitting.

## Development Setup

### Prerequisites

- **Java 21** (required for all Spring Boot services)
- **Node.js 18+** and npm (required for the Next.js frontend apps)
- **PostgreSQL** (primary data store)
- **RabbitMQ** (message broker for inter-service communication)

### Project Structure

This is a monorepo containing:

- **5 Spring Boot services** -- API Gateway, Auth Service, User Service, Notification Service, and Order Service
- **2 Next.js applications** -- the main web client and the admin dashboard

### Running Locally

1. Ensure PostgreSQL and RabbitMQ are running.
2. Copy any `.env.example` files to `.env` and fill in your local configuration.
3. Build and start the Spring Boot services with `./mvnw spring-boot:run` (or the Gradle equivalent) from each service directory.
4. Install frontend dependencies with `npm install` and start with `npm run dev` from each Next.js app directory.

## Code Style

- **Java** -- Follow standard Java conventions. Use meaningful names, keep methods short, and write Javadoc for public APIs.
- **TypeScript/JavaScript** -- Follow the ESLint configuration included in the repository. Run `npm run lint` before committing.
- Keep commits focused. Each commit should represent a single logical change.

## Pull Requests

1. Ensure your branch is up to date with `main`.
2. Run the full test suite and confirm all tests pass.
3. Write a clear PR title and description explaining what changed and why.
4. Link any related issues using `Closes #<issue-number>`.
5. Be responsive to review feedback.

## Reporting Issues

- Search existing issues before opening a new one.
- Use a clear, descriptive title.
- Include steps to reproduce the problem, expected behavior, and actual behavior.
- Mention your OS, Java version, Node.js version, and any other relevant environment details.

---

By contributing, you agree that your contributions will be licensed under the [Apache License 2.0](LICENSE).
