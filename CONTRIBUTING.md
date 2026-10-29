# Contributing to NeaBans

Thank you for contributing to **NeaBans**!

## Engineering Guidelines

- **Java Version:** Java 21 (`--release 21`).
- **Database Architecture:** Use `Database` / `AbstractDatabase` abstractions. All SQL queries must be parameterized to eliminate injection risks.
- **REST & Web APIs:** Endpoints in `me.nea.neabans.web.api` must require valid JWT authorization via `WebAuthManager`.
- **Testing:** Run existing test suites (`mvn clean test`) to ensure model, time formatting, and color deserialization remain unbroken.
- **Git Commit Standards:** Use [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `docs:`, `refactor:`, `test:`).
