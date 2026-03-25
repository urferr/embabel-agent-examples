package com.embabel.example.secured;

import java.util.List;

import com.embabel.agent.domain.library.HasContent;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public class NewsDigestTypes {
	/**
	 * A single news item within a [NewsDigest], carrying a headline, summary, and
	 * source URL.
	 */
	@JsonClassDescription("A single news item in a digest")
	public static record DigestItem(@JsonPropertyDescription("Headline of the news item") String headline,
			@JsonPropertyDescription("Brief summary of the news item") String summary,
			@JsonPropertyDescription("Source URL") String url) {}

	/**
	 * Container for a list of [DigestItem] values.
	 *
	 * Wraps `List<DigestItem>` as a named type to avoid generic type erasure during
	 * LLM-driven JSON deserialisation. Using `createObject<List<DigestItem>>` loses
	 * the type parameter at runtime and Jackson returns `List<LinkedHashMap>`;
	 * wrapping in a named class preserves the element type.
	 */
	@JsonClassDescription("A list of news digest items")
	public static record DigestItemList(@JsonPropertyDescription("The list of news items") List<DigestItem> items) {}

	/**
	 * A research topic extracted from freeform user input, optionally narrowed to a
	 * specific focus area within that topic.
	 */
	@JsonClassDescription("A research topic extracted from user input")
	public static record NewsTopic(@JsonPropertyDescription("The topic to research") String topic,
			@JsonPropertyDescription("Optional focus area within the topic") String focusArea) {
		public NewsTopic(String topic) {
			this(topic, "");
		}
	}

	/**
	 * A curated news digest produced by a news digest agent, containing a list of
	 * [DigestItem] entries and a short editorial narrative.
	 *
	 * Implements [HasContent] so the digest can be consumed by downstream agents or
	 * exported as a content asset.
	 */
	@JsonClassDescription("A curated news digest for a given topic")
	public static record NewsDigest(
			/** The topic that was researched. */
			String topic, /** The curated list of news items. */
			List<DigestItem> items, String content) implements HasContent {
		@Override
		public String getContent() {
			return content;
		}
	}
}
