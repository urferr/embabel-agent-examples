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
package com.embabel.example.horoscope;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.Export;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.api.models.OpenAiModels;
import com.embabel.agent.core.CoreToolGroups;
import com.embabel.agent.core.hitl.WaitFor;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.domain.library.Person;
import com.embabel.agent.domain.library.PersonImpl;
import com.embabel.agent.domain.library.RelevantNewsStories;
import com.embabel.common.ai.model.LlmOptions;

/**
 * Writes an amusing personalised writeup combining a person's horoscope and
 * relevant current news stories.
 */
@Agent(name = "JavaStarNewsFinder", description = "Write an amusing personalised writeup combining a person's horoscope and current news stories", beanName = "javaStarNewsFinder")
public class StarNewsFinder {

	private final HoroscopeService horoscopeService;
	private final int storyCount;
	private final int wordCount;
	private final String model = OpenAiModels.GPT_6_LUNA;

	public StarNewsFinder(HoroscopeService horoscopeService, @Value("${star-news-finder.story.count:5}") int storyCount,
			@Value("${star-news-finder.word.count:100}") int wordCount) {
		this.horoscopeService = horoscopeService;
		this.storyCount = storyCount;
		this.wordCount = wordCount;
	}

	/**
	 * Extracts a person entity from user input by parsing the text for a name.
	 *
	 * This method uses a lightweight LLM model (GPT-41-NANO) to efficiently extract
	 * just the person's name from the user's input text. It's an entry point for
	 * the agent workflow when only basic person information is available.
	 *
	 * @param userInput The user's text input
	 * @return A Person object if extraction is successful, null otherwise
	 */
	@Action
	public Person extractPerson(UserInput userInput, OperationContext context) {
		// All prompts are typesafe
		return context.ai().withDefaultLlm().createObjectIfPossible("""
				Create a person from this user input, extracting their name:
				%s""".formatted(userInput.getContent()), PersonImpl.class);
	}

	/**
	 * Collects astrological details for a person through a form interface.
	 *
	 * This method is marked with a high cost (100.0) to indicate that it should
	 * only be used when no other path is available in the agent's planning process.
	 * The high cost discourages the agent from asking for user input unless
	 * necessary.
	 *
	 * @param person The person for whom to collect star sign information
	 * @return A Starry object containing the person's star sign
	 */
	@Action(cost = 100.0) // Make it costly so it won't be used in a plan unless there's no other path
	public Starry makeStarry(Person person) {
		return WaitFor.formSubmission("Let's get some astrological details for " + person.getName(), Starry.class);
	}

	/**
	 * Combines a person and their astrological details into a StarPerson object.
	 *
	 * This method serves as a data transformation step in the agent workflow,
	 * creating a specialized person object that includes star sign information.
	 *
	 * @param person The basic person information
	 * @param starry The astrological details
	 * @return A StarPerson object combining both sets of information
	 */
	@Action
	public StarPerson assembleStarPerson(Person person, Starry starry) {
		return new StarPerson(person.getName(), starry.sign());
	}

	/**
	 * Extracts both person information and star sign directly from user input.
	 *
	 * This method provides an alternative entry point to the agent workflow,
	 * allowing the extraction of both name and star sign in a single step when that
	 * information is present in the user's input.
	 *
	 * @param userInput The user's text input
	 * @return A StarPerson object if extraction is successful, null otherwise
	 */
	@Action
	public StarPerson extractStarPerson(UserInput userInput, OperationContext context) {
		return context.ai().withAutoLlm().createObjectIfPossible("""
				Create a person from this user input, extracting their name and star sign:
				%s""".formatted(userInput.getContent()), StarPerson.class);
	}

	/**
	 * Retrieves a daily horoscope for a person based on their star sign.
	 *
	 * This method calls the injected HoroscopeService to get the actual horoscope
	 * text, wrapping it in a Horoscope data class for use in subsequent steps.
	 *
	 * @param starPerson The person with their star sign information
	 * @return A Horoscope object containing the daily horoscope text
	 */
	@Action
	public Horoscope retrieveHoroscope(StarPerson starPerson) {
		return new Horoscope(horoscopeService.dailyHoroscope(starPerson.sign()));
	}

	/**
	 * Finds news stories relevant to a person's horoscope using web search tools.
	 *
	 * This method requires web tools (specified by toolGroups) to search for and
	 * summarize news stories that relate to themes in the person's horoscope. It
	 * uses the LLM to interpret the horoscope, generate appropriate search queries,
	 * and summarize the results.
	 *
	 * @param person    The person with their star sign
	 * @param horoscope The person's daily horoscope
	 * @return A collection of relevant news stories with summaries and URLs
	 */
	@Action
	public RelevantNewsStories findNewsStories(StarPerson person, Horoscope horoscope, OperationContext context) {
		return context.ai().withLlm(model).withId("StarNewsFinder.FindNewsStories").withToolGroup(CoreToolGroups.WEB)
				.createObjectIfPossible(
						"""
								%s is an astrology believer with the sign %s.
								Their horoscope for today is:
									<horoscope>${horoscope.summary}</horoscope>
								Given this, use web tools and generate search queries
								to find %d relevant news stories summarize them in a few sentences.
								Include the URL for each story.
								Do not look for another horoscope reading or return results directly about astrology;
								find stories relevant to the reading above.

								For example:
								- If the horoscope says that they may want to work on relationships, you could find news stories about novel gifts
								- If the horoscope says that they may want to work on their career, find news stories about training courses.
								"""
								.formatted(person.name(), person.sign(), horoscope.summary(), storyCount),
						RelevantNewsStories.class);
	}

	public record NewsSelection(List<Integer> indices) {}

	/**
	 * Creates a personalized writeup combining the horoscope and relevant news
	 * stories.
	 *
	 * This method is the final step in the agent's workflow, marked
	 * with @AchievesGoal to indicate that it fulfills the agent's primary purpose.
	 * It uses the LLM with increased temperature (0.9) to generate creative content
	 * that combines the horoscope interpretation with the found news stories in an
	 * amusing way.
	 *
	 * @param person              The person with their star sign
	 * @param relevantNewsStories The collection of news stories found
	 * @param horoscope           The person's daily horoscope
	 * @return A Writeup containing the formatted text combining horoscope and news
	 */
	// The @AchievesGoal annotation indicates that completing this action
	// achieves the given goal, so the agent can be complete
	@AchievesGoal(description = "Write an amusing writeup for the target person based on their horoscope and current news stories", export = @Export(remote = true, name = "StarNewsWriteup", startingInputTypes = {
			StarPerson.class }))
	@Action
	public Writeup starNewsWriteup(StarPerson person, RelevantNewsStories relevantNewsStories, Horoscope horoscope,
			OperationContext context) {
		var newsItems = relevantNewsStories.getItems().stream()
				.map(item -> "- " + item.getUrl() + ": " + item.getSummary()).collect(Collectors.joining("\n"));

		return context.ai().withLlm(LlmOptions.withModel(model).withTemperature(0.9)).createObjectIfPossible("""
				Take the following news stories and write up something
				amusing for the target person in %d words.

				Begin by summarizing their horoscope in a concise, amusing way, then
				talk about the news. End with a surprising signoff.

				%s is an astrology believer with the sign %s.
				Their horoscope for today is:
				    <horoscope>%s</horoscope>
				Relevant news stories are:
				%s

				Format it as Markdown with links.""".formatted(wordCount, person.name(), person.sign(),
				horoscope.summary(), newsItems), Writeup.class);
	}
}
