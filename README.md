# Clickable Terminal Prompt

A JetBrains IDE plugin (Rider, IntelliJ IDEA, PyCharm, …) that lets you edit the prompt of a
terminal program with the mouse. Made for Claude Code and other CLI agents; also works on a plain
shell prompt.

VS Code and iTerm2 have Alt/Option+click for this. The JetBrains terminal had nothing, so writing a
long prompt meant deleting from the end and walking the caret back with arrow keys.

## What it does

- **Click** in the prompt: the caret moves there.
- **Select** text in the prompt, press **Backspace** or **Delete**: the selection is removed.
- **Ctrl+Z** right after such a deletion: the text comes back where it was.

## How it works

The terminal only forwards keystrokes; the text you type lives inside the program. So the plugin
types for you: the arrow keys and Backspaces you would press by hand, checking where the program
actually put its caret after each step until it is where you clicked.

- Claude Code's input box is found between its two horizontal rules; elsewhere the prompt is the
  cursor's row.
- Up is never sent from the prompt's first row, where it would recall history and replace your text.
- When the program reads the mouse itself (e.g. full-screen mode), the plugin stands aside.
- Ctrl+Z is only taken when there is a deletion to undo; otherwise it reaches the terminal as usual.

Works with the classic (JediTerm) terminal, which Rider uses for Claude Code tabs. Tested on
Rider 2026.2.2.

## Install

Download the zip from Releases, then *Settings → Plugins → ⚙ → Install Plugin from Disk…*, restart.

## Build

Requires JDK 21+ (the JetBrains Runtime bundled with your IDE works).

```
./gradlew test buildPlugin
```

The zip lands in `build/distributions/`. `platformLocalPath` in `gradle.properties` builds against
an installed IDE; leave it empty to download the platform instead.

## See also

[Clean Terminal Copy](https://github.com/olviia/clean-terminal-copy): copy from the terminal without
the margin and wrap breaks.

## License

MIT
