package com.libdbm.xml;

import java.util.List;
import java.util.Map;

/** AST nodes for XML documents, somewhat like the DOM */
public sealed interface XMLNode {

  /** Content sealed interface - text, CDATA, child elements, or mixed */
  sealed interface Content {
    record Text(String value) implements Content {}

    record CDATA(String value) implements Content {}

    record Elements(List<XMLNode> nodes) implements Content {}

    /** Mixed content with text, CDATA, and elements interleaved */
    record Mixed(List<ContentItem> items) implements Content {}
  }

  /** Individual content item - can be text, CDATA, comment, or an element */
  sealed interface ContentItem {
    record Text(String value) implements ContentItem {}

    record CDATA(String value) implements ContentItem {}

    record Comment(String value) implements ContentItem {}

    record Element(XMLNode node) implements ContentItem {}
  }

  /**
   * XML element with tag name, attributes, and content Content can be either text or child elements
   */
  record Element(String tag, Map<String, String> attributes, Content content) implements XMLNode {}

  /** Empty XML element with tag name and attributes but no content */
  record EmptyElement(String tag, Map<String, String> attributes) implements XMLNode {}
}
