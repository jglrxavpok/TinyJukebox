package html

// From Kotlin's docs: https://kotlinlang.org/docs/reference/type-safe-builders.html

interface Element {
    fun render(builder: StringBuilder, indent: String)
}

class TextElement(val text: String) : Element {
    override fun render(builder: StringBuilder, indent: String) {
        builder.append("$indent$text\n")
    }
}

@DslMarker
annotation class HtmlTagMarker

@HtmlTagMarker
abstract class Tag(val name: String) : Element {
    val children = arrayListOf<Element>()
    val attributes = hashMapOf<String, String>()

    protected fun <T : Element> initTag(tag: T, init: T.() -> Unit): T {
        tag.init()
        children.add(tag)
        return tag
    }

    override fun render(builder: StringBuilder, indent: String) {
        builder.append("$indent<$name${renderAttributes()}>\n")
        for (c in children) {
            c.render(builder, indent + "  ")
        }
        builder.append("$indent</$name>\n")
    }

    private fun renderAttributes(): String {
        val builder = StringBuilder()
        for ((attr, value) in attributes) {
            builder.append(" $attr=\"$value\"")
        }
        return builder.toString()
    }

    override fun toString(): String {
        val builder = StringBuilder()
        render(builder, "")
        return builder.toString()
    }
}

abstract class TagWithText(name: String) : Tag(name) {
    operator fun String.unaryPlus() {
        children.add(TextElement(this))
    }
}

class HTML : TagWithText("html") {
    fun head(init: Head.() -> Unit) = initTag(Head(), init)

    fun body(init: Body.() -> Unit) = initTag(Body(), init)

    fun script(language: String, code: String) {
        val scriptObj = initTag(Script()) { +code }
        scriptObj.type = language
    }
}

class Head : TagWithText("head") {
    fun title(init: Title.() -> Unit) = initTag(Title(), init)
    fun meta(init: Meta.() -> Unit) = initTag(Meta(), init)
}

class Title : TagWithText("title")
class Meta : TagWithText("meta")

abstract class BodyTag(name: String) : TagWithText(name) {
    fun b(init: B.() -> Unit) = initTag(B(), init)
    fun p(init: P.() -> Unit) = initTag(P(), init)
    fun h1(init: H1.() -> Unit) = initTag(H1(), init)
    fun h2(init: H2.() -> Unit) = initTag(H2(), init)
    fun h3(init: H3.() -> Unit) = initTag(H3(), init)
    fun a(href: String, init: A.() -> Unit) {
        val a = initTag(A(), init)
        a.href = href
    }
    fun button(actionID: String, init: Button.() -> Unit): Button {
        val button = initTag(Button(), init)
        button.id = actionID
        return button
    }
}

class Body : BodyTag("body")
class B : BodyTag("b")
class P : BodyTag("p")
class H1 : BodyTag("h1")
class H2 : BodyTag("h2")
class H3 : BodyTag("h3")

class A : BodyTag("a") {
    var href: String
        get() = attributes["href"]!!
        set(value) {
            attributes["href"] = value
        }
}

fun html(init: HTML.() -> Unit): HTML {
    val html = HTML()
    html.init()
    return html
}

// Amendments by jglrxavpok
class Button: TagWithText("button") {
    var id: String
        get() = attributes["id"]!!
        set(value) {
            attributes["id"] = value
        }
}
class Script(): BodyTag("script") {
    var type: String
        get() = attributes["type"]!!
        set(value) {
            attributes["type"] = value
        }
}

/**
 * Error code names
 */
val htmlErrorCodeToName = mapOf(
    200 to "OK",
    403 to "ACCESS FORBIDDEN",
    404 to "NOT FOUND",
    418 to "I'M A TEAPOT"
)