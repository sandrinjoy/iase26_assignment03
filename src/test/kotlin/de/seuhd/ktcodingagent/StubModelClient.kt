package de.seuhd.ktcodingagent

import de.seuhd.ktcodingagent.model.ModelClient

/**
 * Sub-exercise (d): implement the test double for ModelClient.
 *
 * - The constructor takes a list of scripted responses (already captured as a `val` below).
 * - `complete(prompt, maxNewTokens)` returns the next scripted response in order.
 * - You may record the prompts received (e.g., in a `prompts` list) for assertions.
 * - If the script is exhausted before the agent stops asking, fail loudly
 *   (e.g., throw IllegalStateException("stub ran out of scripted outputs")).
 *
 * AgentTest fails at runtime until `complete` is implemented.
 */
class StubModelClient(private val scripted: List<String>) : ModelClient {
    private var index = 0
    val prompts = mutableListOf<String>()

    override fun complete(prompt: String, maxNewTokens: Int): String {
        prompts.add(prompt)
        if (index >= scripted.size) {
            throw IllegalStateException(
                "stub ran out of scripted outputs (made ${scripted.size + 1} requests, only ${scripted.size} responses prepared)"
            )
        }
        return scripted[index++]
    }
}
