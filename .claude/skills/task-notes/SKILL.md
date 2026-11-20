---
name: task-notes
description: Writes a learning note in basic English after a task is done - what each file does, why it exists, how it was checked. Use right after finishing a task from tasks/<module>/task<N>.md, or when the user asks for notes on a task.
argument-hint: "task file, e.g. tasks/platform/task1.md (default: the task just finished)"
---

Write one markdown **learning note** for a finished task, so the user can learn from it later.
The reader is a developer who is still learning: write in **basic English** (short sentences,
common words, explain each technical word the first time it appears).

## Steps

1. **Find the task.** Use the task file given as the argument, or the task just finished. Read it
   (goal, subtasks, "Done when", "Result") and the parts of `SPEC-<module>.md` it points to.
2. **List every file the task touched.** Use git: the task's commit(s) (`git log --oneline`,
   `git show --stat <sha>`), or `git status` / `git diff --stat` if not committed yet. Include
   generated files (e.g. the Maven wrapper), grouping them when they belong together.
   Done when: every touched file is in the list.
3. **Read each file** and write its section (format below). Explain the important lines, not every
   line. Compare with something the reader may know (Maven, npm, plain Java) when it helps.
4. **Write the note** to `docs/learn/<module>/task<N>.md` (create the folder if needed; overwrite
   an older note for the same task). Use the format below.
   Done when: every file from step 2 has its own section, and every link works.
5. **Tell the user** the note's path in one line.

## Note format

```markdown
# Task <N>: <task title> - learning note

> Task: [task<N>.md](../../../tasks/<module>/task<N>.md) · Commit: `<sha>` · Date: <YYYY-MM-DD>

## In one minute
3-5 sentences: what this task built, and why the project needs it now.

## The files

### `<path/to/file>`
- **What it does:** ...
- **Why it exists:** the problem it solves; what breaks without it.
- **Key lines:** a short snippet (only the important part) with a plain explanation.
- **Like in ...:** (optional) the same idea in Maven / npm / plain Java.

(one section per file or file group)

## Decisions and why
Each choice made in this task (e.g. a version, a tool), the options, and the reason.

## Problems we hit
What went wrong while doing the task and how it was fixed. Write "None" if nothing did.

## How we checked it works
The commands that were run and what result proved it.

## Words to know
| Word | Meaning in simple words |

## Test yourself
3-5 questions about *why*, not just *what* (answers in a collapsed `<details>` block).

## Learn more
Official links only (docs of the tool or library), one line on what each teaches.
```

Every claim about a tool comes from its official docs or from the files in this repo.
