<div align="center">
<h1>
<picture>
  <source media="(prefers-color-scheme: dark)" srcset="ideahelix_dark.svg">
  <source media="(prefers-color-scheme: light)" srcset="ideahelix_light.svg">
  <img alt="IdeaHelix" height="128" src="ideahelix_light.svg">
</picture>
</h1>
</div>

[JetBrains IDEs'](https://www.jetbrains.com/ides/) plugin for a
[Helix](https://helix-editor.com)-like experience.

🚧 Work in progress! See issues for more details. 🚧

Unfortunately, [LSP](https://github.com/microsoft/language-server-protocol) is not a
silver bullet, and there are still some projects and workflows that are better served by
more heavyweight solutions, with JB IDEs arguably being some of the best in that regard.
Unfortunately, this often forces us to leave an environment like Helix, which feels more
like a right hand than just an editor. The goal of this project is to bridge the gap
between the tooling provided by JetBrains and the editing model of Helix.

## Goals
- Helix users and their muscle memory should feel at home with no need to re-learn
  everything.

## Non-Goals
- **1:1 Emulation** – Achieving a perfect, one-to-one emulation of Helix is not the
  goal, at least for the foreseeable future. Reuse is preferred, and differences in
  caret implementations may lead to edge cases.  If you're looking for the complete Helix
  experience, there is Helix.
- **Plugin Best Practices Compliance** – Stability isn't guaranteed, as the plugin
  interrupts keyboard events with the author's limited knowledge of JetBrains IDEs and
  it likely doesn't follow plugin development best practices. Reaching ~150k lines while
  pursuing those, as IdeaVim does, is something to avoid. Contributions are welcome, as
  basic compatibility or UX improvements are always possible without overdoing it.

## Acknowledgments
- [Kakoune](https://kakoune.org) – for ruining my life by making every other editing
  style unbearable.
- [Iosevka](https://typeof.net/Iosevka/) – for being the font of choice, both in the
  logo and for daily use (licensed under the
  [SIL Open Font License](https://opensource.org/licenses/OFL-1.1)).
