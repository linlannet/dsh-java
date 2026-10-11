---
description: Use the Baidu AI Search MCP server for current web information and Chinese-language search results.
when_to_use: Use when the user needs current web facts, news, policy changes, product availability, Chinese web results, or links that require search rather than model memory.
---

# Baidu AI Search

Use this skill for tasks that need up-to-date web evidence. The MCP server is registered as `baidu-ai-search`, so its tools are exposed as `mcp__baidu-ai-search__<tool-name>`.

## Workflow

1. Turn the user's request into one concise search query. Keep Chinese keywords in Chinese and preserve domain terms, product names, and version numbers.
2. Call the matching Baidu AI Search tool using the exact tool schema shown in the current tool catalog. Do not invent parameters or tool names.
3. Prefer one focused query first. If results are stale, ambiguous, or insufficient, refine the query once by adding a year, product name, region, or tighter keyword.
4. Verify that each claim is supported by a returned source. If results conflict, state the conflict instead of choosing silently.
5. Answer with the user's language, lead with the conclusion, and include source titles plus URLs for external claims.

## Guardrails

- Do not use this skill for private workspace data; use file tools instead.
- Do not fabricate search results, URLs, quotes, or citation dates.
- If the MCP server is unavailable or no matching tool is visible, tell the user and suggest restarting after checking `harness.extensions.mcp` and `BAIDU_APPBUILDER_API_KEY`.
