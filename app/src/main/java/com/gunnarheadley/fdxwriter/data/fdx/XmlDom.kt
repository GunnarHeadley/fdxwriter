package com.gunnarheadley.fdxwriter.data.fdx

import org.w3c.dom.Element
import org.w3c.dom.Node

/** Small DOM convenience helpers used by the parser and serializer. */

internal fun Element.childElements(): List<Element> {
    val out = ArrayList<Element>()
    val nl = childNodes
    for (i in 0 until nl.length) {
        val n = nl.item(i)
        if (n.nodeType == Node.ELEMENT_NODE) out.add(n as Element)
    }
    return out
}

internal fun Element.childElements(tag: String): List<Element> =
    childElements().filter { it.tagName == tag }

internal fun Element.firstChild(tag: String): Element? =
    childElements().firstOrNull { it.tagName == tag }

internal fun Element.attributesMap(): Map<String, String> {
    val out = LinkedHashMap<String, String>()
    val a = attributes
    for (i in 0 until a.length) {
        val node = a.item(i)
        out[node.nodeName] = node.nodeValue
    }
    return out
}

/** Concatenated text of all direct `<Text>` children of an element. */
internal fun Element.concatTextRuns(): String = buildString {
    for (t in childElements("Text")) append(t.textContent ?: "")
}

/** Remove every child node from this element. */
internal fun Element.clearChildren() {
    while (firstChild != null) removeChild(firstChild)
}
