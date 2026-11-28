---
aliases: dsh-rust
created_by: linlan
display_name: DeepSeek Harness Rust
github_url: https://github.com/linlannet/dsh-rust
logo: dsh-rust.png
related: dsh-java, agent-harness, ai, ai-agent, deepseek, rust, llm
short_description: Rust implementation of the DeepSeek Harness modular agent runtime.
topic: dsh-rust
url: https://github.com/linlannet/dsh-rust
---

DeepSeek Harness Rust (dsh-rust) is a contract-first modular agent runtime for Rust 1.85+ and edition 2024, organized as a Cargo workspace (`dsh-types`, `dsh-domain`, `dsh-case`, `dsh-http`, `dsh-infra`, `dsh-runtime`). It ships a runnable HTTP/SSE service and a minimal ReAct loop: model step, tool call, observation, and stop. Model adapters are replaceable through a `ModelPort` port; a scriptable FakeModel runs offline by default, while the DeepSeek Chat Completions adapter is available behind an optional feature. Built-in tools include `echo`, `clock`, and a sandboxed `read_file`; SSE output follows `contracts/v1` with `chunk`, `tool_call`, `done`, and `error` events, increasing sequence numbers, and exactly one terminal event. Plugin host and persistence are planned for 0.2.1.
