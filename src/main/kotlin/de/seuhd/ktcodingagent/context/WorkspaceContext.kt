package de.seuhd.ktcodingagent.context

import kotlinx.serialization.Serializable
import java.nio.file.Path
import java.nio.file.Files

// ...existing code...

/**
 * Snapshot of stable facts about the workspace, captured once at agent startup.
 *
 * Embedded in the stable prefix of every prompt so the model has consistent situational
 * awareness without us having to re-discover it each turn: branch and default branch, the
 * current `git status --short`, the last few commits, and excerpts of well-known project
 * documents.
 */
@Serializable
data class WorkspaceContext(
    val cwd: String,
    val repoRoot: String,
    val branch: String,
    val defaultBranch: String,
    val status: String,
    val recentCommits: List<String>,
    val projectDocs: Map<String, String>
) {
    fun render(): String {
        val commits = if (recentCommits.isEmpty()) "- none" else recentCommits.joinToString("\n") { "- $it" }
        val docs = if (projectDocs.isEmpty()) {
            "- none"
        } else {
            projectDocs.entries.joinToString("\n") { (path, body) -> "- $path\n$body" }
        }
        return buildString {
            appendLine("Workspace:")
            appendLine("- cwd: $cwd")
            appendLine("- repo_root: $repoRoot")
            appendLine("- branch: $branch")
            appendLine("- default_branch: $defaultBranch")
            appendLine("- status:")
            appendLine(status)
            appendLine("- recent_commits:")
            appendLine(commits)
            appendLine("- project_docs:")
            append(docs)
        }
    }
}

/**
 * Sub-exercise (b): implement [load].
 *
 * Build a WorkspaceContext from the directory at [cwd].
 * - Use ProcessBuilder to run "git rev-parse --show-toplevel" (fall back to cwd if it fails).
 * - "git branch --show-current" (fall back to "-").
 * - "git symbolic-ref --short refs/remotes/origin/HEAD" (fall back to "origin/main"); strip "origin/".
 * - "git status --short" (fall back to "clean"); clip to 1500 chars.
 * - "git log --oneline -5"; split on newlines and drop blanks.
 * - Read AGENTS.md, README.md, build.gradle.kts:
 *   when [walkToRepoRoot] is true (default), from both repo root and cwd (skip duplicates);
 *   when false, from cwd only. Clip each to 1200 chars.
 *
 * All git calls must degrade gracefully (no exceptions if git is missing or this is not a repo).
 *
 * See WorkspaceContextTest for the contract.
 */
object WorkspaceContextLoader {
    fun load(cwd: Path, walkToRepoRoot: Boolean = true): WorkspaceContext {
        val repoRoot = runGitCommand(cwd, "rev-parse", "--show-toplevel")?.let { Path.of(it) } ?: cwd
        val branch = runGitCommand(cwd, "branch", "--show-current") ?: "-"
        val defaultBranch = (runGitCommand(cwd, "symbolic-ref", "--short", "refs/remotes/origin/HEAD")
            ?.removePrefix("origin/") ?: "origin/main")
        val status = (runGitCommand(cwd, "status", "--short") ?: "clean").take(1500)
        val recentCommits = (runGitCommand(cwd, "log", "--oneline", "-5") ?: "")
            .split("\n")
            .filter { it.isNotBlank() }

        val projectDocs = mutableMapOf<String, String>()
        val searchPaths = if (walkToRepoRoot) listOf(cwd, repoRoot) else listOf(cwd)
        val seen = mutableSetOf<String>()

        for (searchPath in searchPaths) {
            for (docName in listOf("AGENTS.md", "README.md", "build.gradle.kts")) {
                val docFile = searchPath.resolve(docName)
                if (Files.isRegularFile(docFile)) {
                    val content = try {
                        val fullContent = Files.readString(docFile)
                        if (fullContent.length > 1200) {
                            fullContent.take(1200) + "\n...[truncated ${fullContent.length - 1200} chars]"
                        } else {
                            fullContent
                        }
                    } catch (e: Exception) {
                        continue
                    }
                    val relPath = if (docName in seen) {
                        "${searchPath.fileName}/$docName"
                    } else {
                        docName
                    }
                    if (relPath !in seen) {
                        projectDocs[relPath] = content
                        seen.add(relPath)
                    }
                }
            }
        }

        return WorkspaceContext(
            cwd = cwd.toString(),
            repoRoot = repoRoot.toString(),
            branch = branch,
            defaultBranch = defaultBranch,
            status = status,
            recentCommits = recentCommits,
            projectDocs = projectDocs
        )
    }

    private fun runGitCommand(cwd: Path, vararg args: String): String? {
        return try {
            val process = ProcessBuilder("git", *args)
                .directory(cwd.toFile())
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText().trim() }
            val exitCode = process.waitFor()
            if (exitCode == 0 && output.isNotEmpty()) output else null
        } catch (e: Exception) {
            null
        }
    }
}
