# FDX Writer

An Android app for reading and editing **Final Draft** screenplays (`.fdx`) on the go — built with Kotlin and Jetpack Compose.

FDX Writer opens real Final Draft files, lets you edit the screenplay, beat board, and notes, and writes them back **losslessly**: anything it doesn't touch is preserved so the file stays compatible with Final Draft.

## Features

- **Open, create, and save `.fdx`** files via the Android Storage Access Framework — edit in place or start a new blank script.
- **Screenplay editor** with the usual element types (Scene Heading, Action, Character, Dialogue, Parenthetical, Transition…), screenplay-style layout, and rich text (**bold** / *italic* / underline).
- **Final Draft–style editing** — element switching, Enter creates the next logical element, and Backspace on an empty line merges it into the previous one.
- **Beat board** — a pannable, zoomable canvas of draggable beat cards using Final Draft's colour palette.
- **Script notes** — review, add, edit, and remove notes; they're highlighted inline in the script and can be added straight from the text-selection menu.
- **Undo / redo** and **auto-save** with a configurable interval.
- **Search** and **find / replace** across the whole script.
- **Character-name autofill** drawn from names already used in the script.
- **Net-change tracking** — Save only enables when the document actually differs from what's on disk.

## Tech stack

- **Kotlin** + **Jetpack Compose** (Material 3)
- **MVVM** — `ViewModel` + `StateFlow`
- **DataStore** for settings and the recent-files list
- **Storage Access Framework** for file I/O with persistable URI permissions
- A dependency-free, **DOM-based FDX parser/serializer** (`javax.xml`) that keeps the full document and only rebuilds the sections you edit

## Architecture

The guiding principle is a **lossless DOM round-trip**: the entire `.fdx` XML is parsed into a DOM, only the editable sections (Content paragraphs, beat list items / display board, and script notes) are regenerated on save, and everything else is written back exactly as it was.

```
app/src/main/java/com/example/fdxwriter/
├── data/
│   ├── fdx/     # FDX model, parser, serializer, offset mapping, colours, text edits
│   └── repo/    # ScriptRepository (SAF I/O), SettingsStore, RecentFilesStore (DataStore)
└── ui/
    ├── editor/      # screenplay editor, format bar, rich-text codec
    ├── beatboard/   # beat board canvas
    ├── notes/       # notes list + note editor
    ├── settings/    # settings screen
    ├── theme/       # Compose theme
    └── AppScreen · HomeScreen · ScriptViewModel
```

## Build & run

Requirements:

- Android Studio (latest) or the Android SDK with **API 36** installed
- **JDK 17+** (the JBR bundled with Android Studio works)

```bash
# Build and install a debug build on a connected device / emulator
./gradlew installDebug

# Run the unit tests
./gradlew testDebugUnitTest
```

- **Minimum Android version:** API 24 (Android 7.0)
- **Target / compile:** API 36

## Testing

The pure logic — FDX parse / serialize / round-trip, offset mapping, and find/replace run editing — is covered by JVM unit tests that build small synthetic screenplays in code, so no external sample files are needed.

```bash
./gradlew testDebugUnitTest
```

## Limitations

- Editing a paragraph preserves **bold / italic / underline**; other Final Draft run attributes (font, size, colour) on an edited paragraph are not user-editable.
- Note anchors are not re-positioned live while large amounts of text are inserted or deleted above them.
- Export, print/PDF, and repagination are out of scope.

## License

No license is included yet. Add one (for example, MIT) before publishing if you want to allow reuse.
