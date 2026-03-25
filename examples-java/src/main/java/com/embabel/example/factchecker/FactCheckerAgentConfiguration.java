/*
 * Copyright 2024-2026 Embabel Pty Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.embabel.example.factchecker;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.api.common.workflow.control.SimpleAgentBuilder;
import com.embabel.agent.api.models.AnthropicModels;
import com.embabel.agent.core.Agent;
import com.embabel.agent.core.CoreToolGroups;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.domain.library.InternetResource;
import com.embabel.agent.domain.library.InternetResources;
import com.embabel.common.ai.model.LlmOptions;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

record FactualAssertion(String standaloneAssertion) {}

record FactualAssertions(List<FactualAssertion> factualAssertions) {}

record RationalizedFactualAssertions(List<FactualAssertion> factualAssertions,
		@JsonPropertyDescription("factual assertions that were merged") int numberMerged) {}

record AssertionCheck(String assertion, boolean isTrue,
		@JsonPropertyDescription("confidence in your judgment as to whether the assertion true or false. From 0-1") double confidence,
		@JsonPropertyDescription("reasoning for your scoring") String reasoning, List<InternetResource> links)
		implements InternetResources {

	@Override
	public List<InternetResource> getLinks() {
		return links;
	}
}

record FactCheck(List<AssertionCheck> checks) {}

@ConfigurationProperties("embabel.fact-checker")
record FactCheckerProperties(@DefaultValue("30") int reasoningWordCount,
		@DefaultValue({ "Wikipedia", "Wikidata", "Britannica", "BBC", "Reuters",
				"ABC Australia" }) List<String> trustedSources,
		@DefaultValue({ "Reddit", "4chan", "Twitter" }) List<String> untrustedSources) {}

@Configuration
@Profile("!test")
public class FactCheckerAgentConfiguration {

	@Bean
	public Agent factChecker(FactCheckerProperties properties) {
		return factCheckerAgent(List.of(LlmOptions.withModel(AnthropicModels.CLAUDE_HAIKU_4_5).withTemperature(0.3),
				LlmOptions.withModel(AnthropicModels.CLAUDE_HAIKU_4_5).withTemperature(0.0)), properties);
	}

	public static Agent factCheckerAgent(List<LlmOptions> llms, FactCheckerProperties properties) {
		return SimpleAgentBuilder.returning(FactCheck.class).consuming(UserInput.class).running(context -> {
			var assertions = context.parallelMap(llms, Math.max(1, llms.size()),
					llm -> extractAssertions(context.getInput(), context, llm));
			var rationalized = mergeAssertions(assertions, context);
			return checkAssertions(rationalized, context, properties);
		}).buildAgent("FactChecker", "Check content for factual accuracy");
	}

	private static FactualAssertions extractAssertions(UserInput input, OperationContext context, LlmOptions llm) {
		return context.ai().withLlm(llm).withToolGroup(CoreToolGroups.WEB).createObject("""
				Given the following content, identify any factual assertions.
				Phrase them as standalone assertions.
				Do not duplicate assertions.
				Use the minimum number of assertions possible, with no overlap.

				# Content
				%s
				""".formatted(input.getContent()), FactualAssertions.class);
	}

	private static RationalizedFactualAssertions mergeAssertions(List<FactualAssertions> assertions,
			OperationContext context) {
		var formattedAssertions = assertions.stream().flatMap(result -> result.factualAssertions().stream())
				.map(assertion -> "- " + assertion.standaloneAssertion()).collect(Collectors.joining("\n"));
		return context.ai().withDefaultLlm().createObject("""
				Given the following factual assertions, merge them into a single list if
				any are the same. Condense into one assertion if one assertion negates another.
				Count the number you merged.

				# Assertions
				%s
				""".formatted(formattedAssertions), RationalizedFactualAssertions.class);
	}

	private static FactCheck checkAssertions(RationalizedFactualAssertions assertions, OperationContext context,
			FactCheckerProperties properties) {
		var promptRunner = context.ai().withAutoLlm().withToolGroups(CoreToolGroups.WEB,
				CoreToolGroups.BROWSER_AUTOMATION);
		var checks = context.parallelMap(assertions.factualAssertions(),
				Math.max(1, assertions.factualAssertions().size()),
				assertion -> promptRunner.createObject("""
						Given the following assertion, check if it is true or false and explain why in %d words
						Express your confidence in your determination as a number between 0 and 1.
						Use web tools.

						Be guided by the following regarding sources:
						- Trusted sources: %s
						- Untrusted sources: %s
						Assertion: <%s>
						""".formatted(properties.reasoningWordCount(), String.join(", ", properties.trustedSources()),
						String.join(", ", properties.untrustedSources()), assertion.standaloneAssertion()),
						AssertionCheck.class));
		return new FactCheck(checks);
	}
}
