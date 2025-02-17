<div align="center">
<h1>
<picture>
  <source media="(prefers-color-scheme: dark)" srcset="ideahelix_dark.svg">
  <source media="(prefers-color-scheme: light)" srcset="ideahelix_light.svg">
  <img alt="IdeaHelix" height="128" src="ideahelix_light.svg">
</picture>
</h1>
</div>

## Goals
- Helix users and their muscle memory should feel at home in
  [JetBrains IDEs](https://www.jetbrains.com/ides/), with no need to re-learn everything.

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
- [Helix](https://helix-editor.com) – for doing the hard work of RIIR-ing Kakoune.
- [Iosevka](https://typeof.net/Iosevka/) – for being the font of choice, both in the
  logo and for daily use (licensed under the
  [SIL Open Font License](https://opensource.org/licenses/OFL-1.1)).
