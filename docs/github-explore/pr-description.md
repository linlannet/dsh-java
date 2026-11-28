# Add GitHub Explore topics: dsh-java and dsh-rust

## Summary

Adds GitHub Explore topic pages for the DeepSeek Harness modular agent runtime in two languages:

- **DeepSeek Harness Java (dsh-java)** — Java 17 / Spring Boot 3.5.16 implementation
- **DeepSeek Harness Rust (dsh-rust)** — Rust 1.85+ / edition 2024 implementation

## Changes

- Adds `topics/dsh-java/index.md` and `topics/dsh-java/dsh-java.png`
- Adds `topics/dsh-rust/index.md` and `topics/dsh-rust/dsh-rust.png`
- Adds the topic display name, aliases, related topics, repository URL, and short description for both topics
- Cross-links the two topics through their `related` metadata (`dsh-java` ↔ `dsh-rust`)
- Describes the Java runtime’s replaceable model adapters, tool registries, session event logs, ReAct agent loop, and plugin lifecycle
- Describes the Rust runtime’s contract-first Cargo workspace, runnable HTTP/SSE service (Axum + Tokio), minimal ReAct loop, replaceable `ModelPort`, offline FakeModel with an optional DeepSeek adapter, built-in `echo` / `clock` / sandboxed `read_file` tools, and `contracts/v1` SSE semantics
- Notes that the Rust plugin host and persistence are planned for 0.2.1
- Removes the conflicting `dsh-plugin` alias from the existing `dsh` topic because `dsh-plugin` now has its own curated topic

## Validation

- Topic names use lowercase and hyphens only
- Descriptions match each repository’s documented architecture and current version
- Related topics are directly relevant and reference each other
- Repository URLs point to `https://github.com/linlannet/dsh-java` and `https://github.com/linlannet/dsh-rust`
- Logo assets are included for both topics; resize to GitHub’s 288×288 non-transparent requirement if maintainers require that exact format

## Notes

I can adjust the display names, aliases, related topics, repository URLs, logos, or descriptions if maintainers prefer different metadata.
