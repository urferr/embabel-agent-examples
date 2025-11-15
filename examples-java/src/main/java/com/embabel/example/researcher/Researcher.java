package com.embabel.example.researcher;

import static com.embabel.common.ai.model.ModelProvider.Companion.CHEAPEST_ROLE;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.Condition;
import com.embabel.agent.api.annotation.RequireNameMatch;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.core.CoreToolGroups;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.domain.library.ResearchReport;
import com.embabel.agent.prompt.PromptUtils;
import com.embabel.agent.prompt.ResponseFormat;
import com.embabel.agent.prompt.persona.Persona;
import com.embabel.common.ai.model.LlmOptions;
import com.embabel.common.ai.prompt.PromptContributor;
import com.embabel.common.ai.prompt.PromptContributorConsumer;
import com.embabel.common.core.types.Timestamped;

import jakarta.annotation.PostConstruct;

record SingleLlmReport(ResearchReport report, String model) implements Timestamped {

	@Override
	public Instant getTimestamp() {
		return Instant.now();
	}

}

record Critique(boolean accepted, String reasoning) {

}

@ConfigurationProperties(prefix = "embabel.examples.researcher")
record ResearcherProperties(ResponseFormat responseFormat, int maxWordCount, String claudeModelName,
		String openAiModelName, String criticModelName, String mergeModelName, String personaName,
		String personaDescription, String personaVoice, String personaObjective) implements PromptContributorConsumer {

	@Override
	public List<PromptContributor> getPromptContributors() {
		return List.of(responseFormat, new Persona(personaName, personaDescription, personaVoice, personaObjective));
	}
}

enum Category {
	QUESTION, DISCUSSION
}

record Categorization(Category category) {

}

/**
 * Researcher agent that implements the Embabel model for autonomous research.
 *
 * This agent demonstrates several key aspects of the Embabel framework: 1.
 * Multi-model approach - using both GPT-4 and Claude models for research 2.
 * Self-critique and improvement - evaluating reports and redoing research if
 * needed 3. Parallel execution - running multiple research actions concurrently
 * 4. Workflow control with conditions - using satisfactory/unsatisfactory
 * conditions 5. Model merging - combining results from different LLMs for
 * better output
 *
 * The agent follows a structured workflow: - First categorizes user input as a
 * question or discussion topic - Performs research using multiple LLM models in
 * parallel - Merges the research reports from different models - Self-critiques
 * the merged report - If unsatisfactory, reruns research with specific models -
 * Delivers the final research report when satisfactory
 */
@Agent(description = "Perform deep web research on a topic")
public class Researcher {
	/** Condition name for when a report is satisfactory */
	private final static String REPORT_SATISFACTORY = "reportSatisfactory";
	/** Condition name for when a report is unsatisfactory */
	private final static String REPORT_UNSATISFACTORY = "reportUnsatisfactory";

	private final Logger logger = LoggerFactory.getLogger(Researcher.class);

	private final ResearcherProperties properties;

	public Researcher(ResearcherProperties theProperties) {
		properties = theProperties;
	}

	@PostConstruct
	protected void init() {
		logger.info("Researcher agent initialized: $properties");
	}

	/**
	 * Categorizes the user input to determine the appropriate research approach.
	 * Uses the cheapest LLM model to efficiently classify the input.
	 *
	 * @param userInput The user's query or topic for research
	 * @return Categorization of the input as either a QUESTION or DISCUSSION
	 */
	@Action
	public Categorization categorize(UserInput theUserInput, OperationContext theContext) {
		return theContext.ai().withLlmByRole(CHEAPEST_ROLE).createObject("""
				    Categorize the following user input:

				    Topic:
				    %s
				""".formatted(theUserInput.getContent()), Categorization.class);
	}

	/**
	 * Performs research using the GPT-4 model. This is one of two parallel research
	 * paths (along with Claude).
	 *
	 * @param userInput      The user's query or topic
	 * @param categorization The categorization of the input
	 * @param context        The operation context for accessing tools and services
	 * @return A research report with the GPT-4 model's findings
	 */
	// These need a different output binding or only one will run
	@Action(post = { REPORT_SATISFACTORY }, canRerun = true, outputBinding = "gpt4Report", toolGroups = {
			CoreToolGroups.WEB, CoreToolGroups.BROWSER_AUTOMATION })
	public SingleLlmReport researchWithGpt4(UserInput theUserInput, Categorization theCategorization,
			OperationContext theContext) {
		return researchWith(theUserInput, theCategorization, null, LlmOptions.withModel(properties.openAiModelName()),
				theContext);
	}

	/**
	 * Redoes research with GPT-4 after receiving an unsatisfactory critique. This
	 * demonstrates the agent's ability to improve based on feedback.
	 *
	 * @param userInput      The user's query or topic
	 * @param categorization The categorization of the input
	 * @param critique       The critique of the previous report explaining why it
	 *                       was unsatisfactory
	 * @param context        The operation context for accessing tools and services
	 * @return An improved research report with the GPT-4 model's findings
	 */
	@Action(pre = { REPORT_UNSATISFACTORY }, post = {
			REPORT_SATISFACTORY }, canRerun = true, outputBinding = "gpt4Report", toolGroups = { CoreToolGroups.WEB,
					CoreToolGroups.BROWSER_AUTOMATION })
	public SingleLlmReport redoResearchWithGpt4(UserInput theUserInput, Categorization theCategorization,
			Critique theCritique, OperationContext theContext) {
		return researchWith(theUserInput, theCategorization, theCritique,
				LlmOptions.withModel(properties.openAiModelName()), theContext);
	}

	/**
	 * Performs research using the Claude model. This is one of two parallel
	 * research paths (along with GPT-4).
	 *
	 * @param userInput      The user's query or topic
	 * @param categorization The categorization of the input
	 * @param context        The operation context for accessing tools and services
	 * @return A research report with the Claude model's findings
	 */
	@Action(post = { REPORT_SATISFACTORY }, outputBinding = "claudeReport", canRerun = true, toolGroups = {
			CoreToolGroups.WEB, CoreToolGroups.BROWSER_AUTOMATION })
	public SingleLlmReport researchWithClaude(UserInput theUserInput, Categorization theCategorization,
			OperationContext theContext) {
		return researchWith(theUserInput, theCategorization, null, LlmOptions.withModel(properties.claudeModelName()),
				theContext);
	}

	/**
	 * Redoes research with Claude after receiving an unsatisfactory critique. This
	 * demonstrates the agent's ability to improve based on feedback.
	 *
	 * @param userInput      The user's query or topic
	 * @param categorization The categorization of the input
	 * @param critique       The critique of the previous report explaining why it
	 *                       was unsatisfactory
	 * @param context        The operation context for accessing tools and services
	 * @return An improved research report with the Claude model's findings
	 */
	@Action(pre = { REPORT_UNSATISFACTORY }, post = {
			REPORT_SATISFACTORY }, outputBinding = "claudeReport", canRerun = true, toolGroups = { CoreToolGroups.WEB,
					CoreToolGroups.BROWSER_AUTOMATION })

	public SingleLlmReport redoResearchWithClaude(UserInput theUserInput, Categorization theCategorization,
			Critique theCritique, OperationContext theContext) {
		return researchWith(theUserInput, theCategorization, theCritique,
				LlmOptions.withModel(properties.claudeModelName()), theContext);
	}

	/**
	 * Common implementation for research with different models. Routes to the
	 * appropriate research method based on categorization.
	 *
	 * @param userInput      The user's query or topic
	 * @param categorization The categorization of the input
	 * @param critique       Optional critique from a previous attempt
	 * @param llm            The LLM options including model selection
	 * @param context        The operation context for accessing tools and services
	 * @return A research report with the specified model's findings
	 */
	private SingleLlmReport researchWith(UserInput theUserInput, Categorization theCategorization, Critique theCritique,
			LlmOptions theLlm, OperationContext theContext) {
		ResearchReport aResearchReport = switch (theCategorization.category()) {
		case QUESTION -> answerQuestion(theUserInput, theLlm, theCritique, theContext);
		case DISCUSSION -> research(theUserInput, theLlm, theCritique, theContext);
		};

		return new SingleLlmReport(aResearchReport, theLlm.getCriteria().toString());
	}

	/**
	 * Generates a research report that answers a specific question. Uses web tools
	 * to find precise answers with citations.
	 *
	 * @param userInput The user's question
	 * @param llm       The LLM options including model selection
	 * @param critique  Optional critique from a previous attempt
	 * @param context   The operation context for accessing tools and services
	 * @return A research report answering the question
	 */
	private ResearchReport answerQuestion(UserInput theUserInput, LlmOptions theLlm, Critique theCritique,
			OperationContext theContext) {
		var aCritiqueOfPreviousAnswer = (theCritique != null && theCritique.reasoning() != null)
				? "Critique of previous answer:\n%s".formatted(theCritique.reasoning())
				: "";

		return theContext
				.promptRunner(theLlm, Collections.emptySet(), Collections.emptyList(),
						properties.getPromptContributors(), Collections.emptyList(), false)
				.createObject("""
						  Use the web and browser tools to answer the given question.

						  You must try to find the answer on the web, and be definite, not vague.

						  Write a detailed report in at most %d words.
						  If you can answer the question more briefly, do so.
						  Including a number of links that are relevant to the topic.

						  Example:
						  %s

						  Question:
						  %s

						  %s
						""".formatted(properties.maxWordCount(), PromptUtils.jsonExampleOf(ResearchReport.class),
						theUserInput.getContent(), aCritiqueOfPreviousAnswer), ResearchReport.class);
	}

	/**
	 * Generates a research report on a discussion topic. Uses web tools to gather
	 * information and provide a comprehensive overview.
	 *
	 * @param userInput The user's topic for research
	 * @param llm       The LLM options including model selection
	 * @param critique  Optional critique from a previous attempt
	 * @param context   The operation context for accessing tools and services
	 * @return A research report on the topic
	 */
	private ResearchReport research(UserInput theUserInput, LlmOptions theLlm, Critique theCritique,
			OperationContext theContext) {
		var aCritiqueOfPreviousAnswer = (theCritique != null && theCritique.reasoning() != null)
				? "Critique of previous answer:\n%s".formatted(theCritique.reasoning())
				: "";

		return theContext
				.promptRunner(theLlm, Collections.emptySet(), Collections.emptyList(),
						properties.getPromptContributors(), Collections.emptyList(), false)
				.createObject("""
						  Use the web and browser tools to perform deep research on the given topic.

						  Write a detailed report in %d words,
						  including a number of links that are relevant to the topic.

						  Topic:
						  %s

						  %s
						""".formatted(properties.maxWordCount(), theUserInput.getContent(), aCritiqueOfPreviousAnswer),
						ResearchReport.class);
	}

	/**
	 * Evaluates the quality of the merged research report. This implements the
	 * self-critique capability of the Embabel model.
	 *
	 * @param userInput    The user's original query or topic
	 * @param mergedReport The combined report to evaluate
	 * @return A critique with acceptance status and reasoning
	 */
	@Action(post = { REPORT_SATISFACTORY }, canRerun = true)
	public Critique critiqueMergedReport(UserInput theUserInput,
			@RequireNameMatch("mergedReport") ResearchReport theMergedReport, OperationContext theContext) {
		return theContext.ai().withLlm(properties.criticModelName()).createObject("""
				    Is this research report satisfactory? Consider the following question:
				    %s
				    The report is satisfactory if it answers the question with adequate references.
				    It is possible that the question does not have a clear answer, in which
				    case the report is satisfactory if it provides a reasonable discussion of the topic.

				    %s
				""".formatted(theUserInput.getContent(), theMergedReport.infoString(true, 0)), Critique.class);
	}

	/**
	 * Combines the research reports from different models into a single, improved
	 * report. This demonstrates the multi-model approach of the Embabel framework.
	 *
	 * @param userInput    The user's original query or topic
	 * @param gpt4Report   The research report from the GPT-4 model
	 * @param claudeReport The research report from the Claude model
	 * @return A merged research report combining the best insights from both models
	 */
	@Action(post = { REPORT_SATISFACTORY }, outputBinding = "mergedReport", canRerun = true)
	public ResearchReport mergeReports(UserInput theUserInput,
			@RequireNameMatch("gpt4Report") SingleLlmReport theGpt4Report,
			@RequireNameMatch("claudeReport") SingleLlmReport theClaudeReport, OperationContext theContext) {
		var someReports = List.of(theGpt4Report, theClaudeReport).stream().map(
				theReport -> "Report from %s\n%s".formatted(theReport.model(), theReport.report().infoString(true, 0)))
				.collect(Collectors.joining("\n\n"));

		return theContext
				.promptRunner(LlmOptions.withModel(properties.criticModelName()), Collections.emptySet(),
						Collections.emptyList(), properties.getPromptContributors(), Collections.emptyList(), false)
				.createObject(
						"""
								    Merge the following research reports into a single report taking the best of each.
								    Consider the user direction: %s

								    ${reports.joinToString("\n\n") { "Report from ${it.model}\n${it.report.infoString(verbose = true)}" }}
								"""
								.formatted(theUserInput.getContent(), someReports),
						ResearchReport.class);
	}

	/**
	 * Condition that determines if a report is satisfactory. Used to control
	 * workflow progression.
	 *
	 * @param critique The critique of the report
	 * @return True if the report is accepted as satisfactory
	 */
	@Condition(name = REPORT_SATISFACTORY)
	public boolean makesTheGrade(Critique theCritique) {
		return theCritique.accepted();
	}

	/**
	 * Condition that determines if a report is unsatisfactory. Used to trigger
	 * rework of research.
	 *
	 * @param critique The critique of the report
	 * @return True if the report is rejected as unsatisfactory
	 */
	// TODO should be able to use !
	@Condition(name = REPORT_UNSATISFACTORY)
	public boolean rejected(Critique theCritique) {
		return !theCritique.accepted();
	}

	/**
	 * Final action that accepts the research report as the agent's output. This
	 * marks the successful completion of the research task.
	 *
	 * @param mergedReport The final merged research report
	 * @param critique     The positive critique confirming the report is
	 *                     satisfactory
	 * @return The final research report
	 */
	@AchievesGoal(description = "Completes a research or question answering task, producing a research report")
	// TODO this won't complete without the output binding to a new thing.
	// This makes some sense but seems a bit surprising
	@Action(pre = { REPORT_SATISFACTORY }, outputBinding = "finalResearchReport")
	public ResearchReport acceptReport(@RequireNameMatch("mergedReport") ResearchReport theMergedReport,
			Critique theCritique) {
		return theMergedReport;
	}
}
