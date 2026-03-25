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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.Arrays;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.embabel.agent.domain.library.NewsStory;
import com.embabel.agent.domain.library.RelevantNewsStories;
import com.embabel.agent.test.unit.FakeOperationContext;

public class StarNewsFinderTest {

	@Nested
	class Writeup {

		@Test
		void writeupPromptMustContainKeyData() {
			var wordCount = 100;

			HoroscopeService horoscopeService = mock(HoroscopeService.class);
			StarNewsFinder starNewsFinder = new StarNewsFinder(horoscopeService, 5, wordCount);

			var context = new FakeOperationContext();
			context.expectResponse(new com.embabel.example.horoscope.Writeup("Things are gonna get frosty"));

			NewsStory cockatoos = new NewsStory("https://fake.com.au", "Cockatoo behavior",
					"Cockatoos are eating cabbages");

			NewsStory emus = new NewsStory("https://morefake.com.au", "Emu movements", "Emus are massing");

			StarPerson starPerson = new StarPerson("Lynda", "Scorpio");
			RelevantNewsStories relevantNewsStories = new RelevantNewsStories(Arrays.asList(cockatoos, emus));

			var result = starNewsFinder.starNewsWriteup(starPerson, relevantNewsStories,
					new Horoscope("This is a good day for you"), context);

			var prompt = context.getLlmInvocations().getFirst().getMessages().getFirst().getContent();
			var toolGroups = context.getLlmInvocations().getFirst().getInteraction().getToolGroups();

			assertTrue(prompt.contains(starPerson.getName()));
			assertTrue(prompt.contains("Scorpio"));
			assertTrue(prompt.contains(cockatoos.getSummary()));
			assertTrue(prompt.contains(emus.getSummary()));
			assertTrue(prompt.contains(String.valueOf(wordCount)));

			assertTrue(toolGroups.isEmpty(), "The LLM should not have been given any tool groups");
		}
	}
}
