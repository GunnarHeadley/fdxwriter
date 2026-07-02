package com.example.fdxwriter.data.fdx

/**
 * The screenplay element types defined by Final Draft (the `Type` attribute of a
 * `<Paragraph>` inside `<Content>`). The [fdxName] is the exact string used in the file.
 */
enum class ElementType(val fdxName: String) {
    GENERAL("General"),
    SCENE_HEADING("Scene Heading"),
    ACTION("Action"),
    CHARACTER("Character"),
    PARENTHETICAL("Parenthetical"),
    DIALOGUE("Dialogue"),
    TRANSITION("Transition"),
    SHOT("Shot"),
    CAST_LIST("Cast List"),
    NEW_ACT("New Act"),
    END_OF_ACT("End of Act"),
    OUTLINE_BODY("Outline Body"),
    OUTLINE_1("Outline 1"),
    OUTLINE_2("Outline 2"),
    OUTLINE_3("Outline 3"),
    OUTLINE_4("Outline 4"),
    ;

    /**
     * The element created when the writer presses Enter at the end of this one. Mirrors the
     * default `Behavior ReturnKey` values from Final Draft's `<ElementSettings>`.
     */
    val returnType: ElementType
        get() = when (this) {
            SCENE_HEADING -> ACTION
            ACTION -> ACTION
            CHARACTER -> DIALOGUE
            PARENTHETICAL -> DIALOGUE
            DIALOGUE -> ACTION
            TRANSITION -> SCENE_HEADING
            SHOT -> ACTION
            NEW_ACT -> SCENE_HEADING
            END_OF_ACT -> NEW_ACT
            CAST_LIST -> ACTION
            GENERAL -> GENERAL
            OUTLINE_BODY, OUTLINE_1, OUTLINE_2, OUTLINE_3, OUTLINE_4 -> OUTLINE_BODY
        }

    /** Final Draft renders these element types in ALL CAPS (display only; stored text keeps its case). */
    val displaysUppercase: Boolean
        get() = this == SCENE_HEADING || this == CHARACTER || this == TRANSITION ||
            this == SHOT || this == CAST_LIST || this == NEW_ACT || this == END_OF_ACT

    val displayName: String get() = fdxName

    companion object {
        fun fromFdx(name: String?): ElementType? = entries.firstOrNull { it.fdxName == name }

        /** Element types a writer normally switches between while editing a screenplay. */
        val screenplayTypes: List<ElementType> = listOf(
            SCENE_HEADING, ACTION, CHARACTER, PARENTHETICAL, DIALOGUE, TRANSITION, SHOT, GENERAL,
        )
    }
}
