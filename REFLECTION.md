# Reflection

**Prompt.** What classes of agent failures can `StubModelClient` not catch, and
what would you do instead?

The `StubModelClient` provides deterministic testing by returning pre-scripted responses, enabling unit tests without an LLM. However, this completeness comes at a cost: **Determinism in the harness does not buy determinism in the world.**

Two critical failure modes the stub cannot catch:

1. **Token limit exhaustion and hallucination under pressure**: Real models degrade when prompts exceed their context window or when they approach token limits mid-reasoning. The stub always returns the exact script, never truncating or degrading. To catch this: Run integration tests with intentionally large conversation histories using the actual `OllamaModelClient` and assert the model still produces valid tool calls, not gibberish; use `BlindspotAuditTool` to instrument and measure token usage thresholds.

2. **Latency-induced timeout failures and connection loss**: The stub completes instantly, masking real-world network jitter, timeouts, and intermittent failures. A real Ollama server might hang, return slowly, or drop the connection mid-response. To catch this: Run integration tests against a real Ollama instance with artificial delays injected via proxy tools or network simulation; explicitly test recovery paths when `complete()` throws `OllamaUnreachableException` and verify the agent gracefully recovers or exits cleanly.

Both require moving beyond scripted testing to live-model validation in a controlled environment.
