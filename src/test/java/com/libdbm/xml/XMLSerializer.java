package com.libdbm.xml;

import com.libdbm.xml.XMLNode.*;
import java.util.Map;

/** Serializes XML AST nodes back to XML text */
public final class XMLSerializer {

  private final boolean indent;
  private final int indentSpaces;

  public XMLSerializer(final boolean indent, final int indentSpaces) {
    this.indent = indent;
    this.indentSpaces = indentSpaces;
  }

  public XMLSerializer() {
    this(true, 2);
  }

  public String serialize(final XMLNode node) {
    final var sb = new StringBuilder();
    serialize(node, sb, 0);
    return sb.toString();
  }

  private void serialize(final XMLNode node, final StringBuilder sb, final int depth) {
    switch (node) {
      case Element element -> serializeElement(element, sb, depth);
      case EmptyElement empty -> serializeEmptyElement(empty, sb, depth);
    }
  }

  private void serializeElement(final Element element, final StringBuilder sb, final int depth) {
    // Opening tag
    indent(sb, depth);
    sb.append("<").append(element.tag());
    serializeAttributes(element.attributes(), sb);
    sb.append(">");

    // Content
    switch (element.content()) {
      case Content.Text text -> {
        // Text content stays on same line
        sb.append(escapeText(text.value()));
      }
      case Content.CDATA cdata -> {
        // CDATA content stays on same line, not escaped
        sb.append("<![CDATA[").append(cdata.value()).append("]]>");
      }
      case Content.Elements elements -> {
        // Child elements get their own lines
        if (indent) {
          sb.append("\n");
        }
        for (final var child : elements.nodes()) {
          serialize(child, sb, depth + 1);
          if (indent) {
            sb.append("\n");
          }
        }
        indent(sb, depth);
      }
      case Content.Mixed mixed -> {
        // Mixed content - serialize items inline
        for (final var item : mixed.items()) {
          switch (item) {
            case ContentItem.Text text -> sb.append(escapeText(text.value()));
            case ContentItem.CDATA cdata ->
                sb.append("<![CDATA[").append(cdata.value()).append("]]>");
            case ContentItem.Comment comment ->
                sb.append("<!--").append(comment.value()).append("-->");
            case ContentItem.Element elem -> {
              if (indent) {
                sb.append("\n");
              }
              serialize(elem.node(), sb, depth + 1);
              if (indent) {
                sb.append("\n");
              }
              indent(sb, depth);
            }
          }
        }
      }
    }

    // Closing tag
    sb.append("</").append(element.tag()).append(">");
  }

  private void serializeEmptyElement(
      final EmptyElement element, final StringBuilder sb, final int depth) {
    indent(sb, depth);
    sb.append("<").append(element.tag());
    serializeAttributes(element.attributes(), sb);
    sb.append("/>");
  }

  private void serializeAttributes(final Map<String, String> attributes, final StringBuilder sb) {
    for (final var entry : attributes.entrySet()) {
      sb.append(" ")
          .append(entry.getKey())
          .append("=\"")
          .append(escapeAttribute(entry.getValue()))
          .append("\"");
    }
  }

  private void indent(final StringBuilder sb, final int depth) {
    if (indent && depth > 0) {
      sb.append(" ".repeat(depth * indentSpaces));
    }
  }

  private String escapeText(final String text) {
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  private String escapeAttribute(final String value) {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }
}
