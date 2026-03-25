package com.embabel.example.secured;

import java.util.List;

import com.embabel.agent.domain.library.HasContent;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public class MarketIntelligenceTypes {
	/**
	 * The subject of a market analysis request, capturing the entity or sector to
	 * be analysed and the geographic scope of the report.
	 */
	@JsonClassDescription("Subject of market analysis")
	public static record AnalysisSubject(
			@JsonPropertyDescription("Company name, sector, or market segment to analyse") String subject,
			@JsonPropertyDescription("Geographic region of focus, e.g. US, EU, APAC, or Global") String region) {
		public AnalysisSubject(String subject) {
			this(subject, "Global");
		}
	}

	/**
	 * A single observation about a competitor or comparable entity identified
	 * during the competitive landscape analysis.
	 */
	@JsonClassDescription("Key player in the competitive landscape")
	public static record CompetitorInsight(
			@JsonPropertyDescription("Name of the competitor or comparable entity") String name,
			@JsonPropertyDescription("Notable recent development or positioning") String insight) {}

	/**
	 * Container for a list of [CompetitorInsight] items.
	 *
	 * Wraps `List<CompetitorInsight>` as a named type to avoid generic type erasure
	 * during LLM-driven JSON deserialisation.
	 */
	@JsonClassDescription("A list of competitor insights")
	public static record CompetitorInsightList(List<CompetitorInsight> items) {}

	/**
	 * A single entry in a SWOT analysis, classifying an observation as a Strength,
	 * Weakness, Opportunity, or Threat.
	 */
	@JsonClassDescription("A single SWOT observation")
	public static record SwotEntry(
			@JsonPropertyDescription("One of: Strength, Weakness, Opportunity, Threat") String category,
			@JsonPropertyDescription("Concise description of this SWOT item") String description) {}

	/**
	 * Container for a list of [SwotEntry] items.
	 *
	 * Wraps `List<SwotEntry>` as a named type to avoid generic type erasure during
	 * LLM-driven JSON deserialisation.
	 */
	@JsonClassDescription("A list of SWOT entries")
	public static record SwotEntryList(List<SwotEntry> items) {}

	/**
	 * Container for a list of key market trend statements.
	 *
	 * Wraps `List<String>` as a named type to avoid generic type erasure during
	 * LLM-driven JSON deserialisation.
	 */
	@JsonClassDescription("A list of key trend statements")
	public static record KeyTrendList(List<String> items) {}

	/**
	 * A structured market intelligence report, containing an executive summary,
	 * SWOT analysis, competitive landscape, and key trend observations for a given
	 * subject and region.
	 *
	 * Implements [HasContent] so the report can be consumed by downstream agents or
	 * exported as a content asset.
	 */
	@JsonClassDescription("Market intelligence report")
	public static record MarketIntelligenceReport(
			/** Company name, sector, or market segment that was analysed. */
			String subject,
			/** Geographic scope of the report (e.g. `US`, `EU`, `Global`). */
			String region,
			/** Four-sentence executive summary suitable for a senior decision-maker. */
			String executiveSummary,
			/** SWOT analysis entries, 2–3 items per category. */
			List<SwotEntry> swot,
			/** Top competitors or comparable entities with strategic positioning notes. */
			List<CompetitorInsight> competitors,
			/** Five most significant market trends, each expressed as a single sentence. */
			List<String> keyTrends, 
			String content) implements HasContent {
		@Override
		public String getContent() {
			return content;
		}

	}
}
